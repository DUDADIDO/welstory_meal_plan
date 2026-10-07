package com.ssafy.welstory.chat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Runs against an isolated schema in a disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "CHAT_TEST_JDBC_URL", matches = ".+")
class ChatServicePostgresTest {
    private final LocalDate mealDate = LocalDate.of(2026, 10, 7);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T03:00:00Z"));
    private JdbcTemplate root;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private ChatService service;
    private String schema;

    @BeforeEach
    void prepare() {
        String url = System.getenv("CHAT_TEST_JDBC_URL");
        String username = System.getenv().getOrDefault("CHAT_TEST_DB_USER", "chat_test");
        String password = System.getenv().getOrDefault("CHAT_TEST_DB_PASSWORD", "chat_test");
        root = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        schema = "chat_test_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        var dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, username, password);
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new ChatService(jdbc, clock);
    }

    @AfterEach
    void cleanup() {
        if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private ChatService.Identity identity() {
        return transactions.execute(status -> service.identity(null));
    }

    private ChatService.Sent send(String token, String mealId, String content) {
        return transactions.execute(status -> service.send(mealDate, mealId, token, content));
    }

    @Test
    void identityPersistsAndNamesAreUniqueWithTwoDifferentFoods() {
        var user = identity();
        assertThat(service.identity(user.token())).isEqualTo(user);
        var names = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String name = identity().user().nickname();
            assertThat(names.add(name)).isTrue();
            String[] foods = name.split("·");
            assertThat(foods).hasSize(2);
            assertThat(foods[0]).isNotEqualTo(foods[1]);
        }
        assertThat(FoodNames.FOODS).hasSize(200).doesNotHaveDuplicates();
        assertThat(service.identity("forged-token").token()).isNotEqualTo("forged-token");
    }

    @Test
    void fiveSecondLimitIsSharedAcrossRoomsAndOtherUsersCanSend() {
        var one = identity();
        var two = identity();
        var first = send(one.token(), "meal-01", "  맛있어요  ");
        assertThat(first.message().content()).isEqualTo("맛있어요");
        clock.advanceMillis(4999);
        assertThatThrownBy(() -> send(one.token(), "meal-02", "아직 안 돼요"))
                .isInstanceOfSatisfying(ChatService.RateLimited.class, e -> {
                    assertThat(e.retryAfterSeconds).isEqualTo(1);
                    assertThat(e.user.remainingToday()).isEqualTo(49);
                });
        send(two.token(), "meal-02", "다른 사람은 가능");
        clock.advanceMillis(1);
        assertThat(send(one.token(), "meal-02", "정확히 5초 후").user().remainingToday()).isEqualTo(48);
        var room = service.room(mealDate, "meal-01", two.token(), null);
        assertThat(room.messages()).hasSize(1);
        assertThat(room.messages().getFirst().mine()).isFalse();
        assertThat(service.room(mealDate.minusDays(1), "meal-01", one.token(), null).messages()).isEmpty();
    }

    @Test
    void dailyLimitAllowsFiftyAndResetsAtSeoulMidnight() {
        clock.set("2026-10-07T14:55:45Z");
        var user = identity();
        for (int i = 0; i < 50; i++) {
            assertThat(send(user.token(), i % 2 == 0 ? "meal-01" : "meal-02", "메시지 " + i).user().remainingToday()).isEqualTo(49 - i);
            clock.advanceMillis(5000);
        }
        assertThatThrownBy(() -> send(user.token(), "meal-03", "51번째"))
                .isInstanceOfSatisfying(ChatService.RateLimited.class, e -> assertThat(e.user.remainingToday()).isZero());
        clock.set("2026-10-07T15:00:00Z");
        assertThat(send(user.token(), "meal-01", "다음 날").user().remainingToday()).isEqualTo(49);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isEqualTo(51);
    }

    @Test
    void cooldownStillAppliesAcrossMidnight() {
        clock.set("2026-10-07T14:59:58Z");
        var user = identity();
        send(user.token(), "meal-01", "자정 전");
        clock.set("2026-10-07T15:00:00Z");
        assertThatThrownBy(() -> send(user.token(), "meal-01", "너무 빨라요"))
                .isInstanceOfSatisfying(ChatService.RateLimited.class, e -> {
                    assertThat(e.retryAfterSeconds).isEqualTo(3);
                    assertThat(e.user.remainingToday()).isEqualTo(50);
                });
        clock.set("2026-10-07T15:00:03Z");
        assertThat(send(user.token(), "meal-01", "전송 가능").user().remainingToday()).isEqualTo(49);
    }

    @Test
    void simultaneousRequestsFromSameUserOnlySaveOneMessage() throws Exception {
        var user = identity();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> {
                start.await();
                try {
                    send(user.token(), "meal-01", "동시 전송");
                    return true;
                } catch (ChatService.RateLimited expected) {
                    return false;
                }
            }));
            start.countDown();
            int successful = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) successful++;
            assertThat(successful).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isEqualTo(1);
        assertThat(service.identity(user.token()).user().remainingToday()).isEqualTo(49);
    }

    @Test
    void invalidMessagesDoNotSpendQuotaAndHistoryPaginatesWithoutDuplicates() {
        var user = identity();
        assertThatThrownBy(() -> send(user.token(), "meal-01", " \n ")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> send(user.token(), "meal-01", "x".repeat(501))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> send(user.token(), "meal-01", "hello\u0000")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.identity(user.token()).user().remainingToday()).isEqualTo(50);
        assertThatThrownBy(() -> send(UUID.randomUUID().toString(), "meal-01", "임의 사용자")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        for (int i = 0; i < 105; i++) jdbc.update("INSERT INTO chat_messages (meal_date, meal_id, user_token, content, created_at) VALUES (?, ?, ?, ?, ?)",
                mealDate, "meal-01", user.token(), "기록 " + i, java.sql.Timestamp.from(clock.instant()));
        var latest = service.room(mealDate, "meal-01", user.token(), null);
        assertThat(latest.messages()).hasSize(100);
        assertThat(latest.hasOlder()).isTrue();
        assertThat(latest.messages().getFirst().content()).isEqualTo("기록 5");
        var older = service.room(mealDate, "meal-01", user.token(), latest.messages().getFirst().id());
        assertThat(older.messages()).hasSize(5);
        assertThat(older.hasOlder()).isFalse();
        assertThat(older.messages().getFirst().content()).isEqualTo("기록 0");
    }

    private static class MutableClock extends Clock {
        private volatile Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void set(String instant) { this.instant = Instant.parse(instant); }
        void advanceMillis(long millis) { instant = instant.plusMillis(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
