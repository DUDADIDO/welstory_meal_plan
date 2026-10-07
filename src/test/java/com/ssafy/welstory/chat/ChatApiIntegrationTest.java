package com.ssafy.welstory.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.MealRefreshScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "CHAT_TEST_JDBC_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatApiIntegrationTest {
    private static final String SCHEMA = "chat_api_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate root;
    private static Path imageDirectory;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean MealCacheService meals;
    @MockitoBean MealRefreshScheduler scheduler;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("CHAT_TEST_JDBC_URL");
        String username = System.getenv().getOrDefault("CHAT_TEST_DB_USER", "chat_test");
        String password = System.getenv().getOrDefault("CHAT_TEST_DB_PASSWORD", "chat_test");
        root = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        root.execute("CREATE SCHEMA " + SCHEMA);
        properties.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        try { imageDirectory = Files.createTempDirectory("welstory-history-test-"); }
        catch (IOException error) { throw new UncheckedIOException(error); }
        properties.add("welstory.cache-dir", () -> imageDirectory.toString());
    }

    @BeforeEach
    void mealExists() {
        when(meals.mealExists(any(LocalDate.class), anyString())).thenReturn(true);
        jdbc.update("DELETE FROM meal_choices");
        jdbc.update("DELETE FROM meal_choice_limits");
        jdbc.update("DELETE FROM rating_votes");
        jdbc.update("DELETE FROM meal_days");
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (root != null) root.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        if (imageDirectory != null) {
            try (var paths = Files.walk(imageDirectory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private ResponseEntity<JsonNode> identity(String cookie) {
        var headers = new HttpHeaders();
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        return http.exchange("/api/chat/identity", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private String cookie(ResponseEntity<JsonNode> response) {
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    }

    private ResponseEntity<JsonNode> browserIdentity(String cookie, String key, boolean header) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        if (header) headers.set("X-Chat-Request", "1");
        return http.exchange("/api/chat/identity", HttpMethod.POST,
                new HttpEntity<>(Map.of("browserKey", key), headers), JsonNode.class);
    }

    private String browserKey() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void browserKeyRecoversExistingUserQuotaAfterCookieDeletion() {
        var original = identity(null);
        String key = browserKey();
        var bound = browserIdentity(cookie(original), key, true);
        assertThat(cookie(bound)).isEqualTo(cookie(original));
        assertThat(send(cookie(bound), "쿠키 삭제 후에도 같은 사용자", true).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var recovered = browserIdentity(null, key, true);
        assertThat(cookie(recovered)).isEqualTo(cookie(original));
        assertThat(recovered.getBody().get("remainingToday").asInt()).isEqualTo(49);
        assertThat(recovered.getBody().get("nickname")).isEqualTo(original.getBody().get("nickname"));
        assertThat(send(cookie(recovered), "제한 우회 시도", true).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(jdbc.queryForList("SELECT key_hash FROM anonymous_browser_keys", String.class)).doesNotContain(key);
        assertThat(browserIdentity(null, browserKey(), true).getBody().get("nickname")).isNotEqualTo(original.getBody().get("nickname"));
    }

    @Test
    void concurrentBrowserKeyRegistrationCreatesExactlyOneIdentity() throws Exception {
        String key = browserKey();
        int usersBefore = jdbc.queryForObject("SELECT count(*) FROM chat_users", Integer.class);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var ready = new java.util.concurrent.CountDownLatch(8);
            var start = new java.util.concurrent.CountDownLatch(1);
            var jobs = new ArrayList<java.util.concurrent.Future<ResponseEntity<JsonNode>>>();
            for (int i = 0; i < 8; i++) jobs.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return browserIdentity(null, key, true);
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var cookies = new java.util.HashSet<String>();
            for (var job : jobs) {
                var response = job.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                cookies.add(cookie(response));
            }
            assertThat(cookies).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_users", Integer.class)).isEqualTo(usersBefore + 1);
    }

    @Test
    void browserIdentityRequiresCustomHeaderAndValidSecret() {
        assertThat(browserIdentity(null, browserKey(), false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(browserIdentity(null, "invalid", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<JsonNode> send(String cookie, String content, boolean header) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        if (header) headers.set("X-Chat-Request", "1");
        return http.exchange("/api/chat/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("date", "2026-10-07", "mealId", "meal-01", "content", content), headers), JsonNode.class);
    }

    @Test
    void cookieIdentitySendAndServerRateLimitWorkThroughSecurityFilters() {
        var identity = identity(null);
        assertThat(identity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(identity.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(identity.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "SameSite=Strict", "Path=/api/chat");
        assertThat(identity.getBody().has("token")).isFalse();
        String cookie = cookie(identity);
        assertThat(identity(cookie).getBody().get("nickname")).isEqualTo(identity.getBody().get("nickname"));
        var sent = send(cookie, "맛있어요 <script>alert(1)</script>", true);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody().at("/user/remainingToday").asInt()).isEqualTo(49);
        assertThat(sent.getBody().at("/message/mine").asBoolean()).isTrue();
        assertThat(sent.getBody().at("/message/content").asText()).contains("<script>"); // Rendered as text by React.
        assertThat(sent.getBody().toString()).doesNotContain(cookie.substring(cookie.indexOf('=') + 1));
        var limited = send(cookie, "너무 빠른 메시지", true);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 5L);
        assertThat(limited.getBody().get("detail").asText()).contains("5초");
        assertThat(limited.getBody().at("/user/remainingToday").asInt()).isEqualTo(49);
    }

    @Test
    void rejectsMissingIdentityCrossOriginStyleRequestAndInvalidContent() {
        String cookie = cookie(identity(null));
        assertThat(send(null, "메시지", true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(cookie, "메시지", false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(cookie, "   ", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(cookie, "x".repeat(501), true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(meals.mealExists(any(LocalDate.class), anyString())).thenReturn(false);
        assertThat(send(cookie, "없는 식단", true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void queryValidationReturnsBadRequestAndOtherUserSeesDistinctNickname() {
        var one = identity(null);
        var two = identity(null);
        assertThat(two.getBody().get("nickname")).isNotEqualTo(one.getBody().get("nickname"));
        assertThat(send(cookie(one), "식단 이야기", true).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie(two));
        var room = http.exchange("/api/chat/messages?date=2026-10-07&mealId=meal-01", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(room.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean found = false;
        for (JsonNode message : room.getBody().get("messages")) {
            if (message.get("nickname").equals(one.getBody().get("nickname"))) {
                assertThat(message.get("mine").asBoolean()).isFalse();
                found = true;
            }
        }
        assertThat(found).isTrue();
        var invalid = http.exchange("/api/chat/messages?date=2026-10-07&mealId=invalid", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }

    private ResponseEntity<JsonNode> vote(String cookie, LocalDate date, String mealId, boolean customHeader) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        if (customHeader) headers.set("X-Chat-Request", "1");
        var body = new java.util.HashMap<String, Object>();
        body.put("date", date.toString());
        body.put("mealId", mealId);
        return http.exchange("/api/chat/meal-votes", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private void allowNextVote(String cookie) {
        jdbc.update("UPDATE meal_choice_limits SET last_vote_at = now() - interval '5 seconds' WHERE user_token = ?",
                cookie.substring(cookie.indexOf('=') + 1));
    }

    @Test
    void floatingVoteChangesCancelsAndPreservesQuotaAfterCookieDeletion() {
        String key = browserKey();
        var user = browserIdentity(null, key, true);
        String cookie = cookie(user);
        var first = vote(cookie, today(), "meal-01", true);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("remainingToday").asInt()).isEqualTo(9);
        assertThat(vote(cookie, today(), "meal-01", true).getBody().get("remainingToday").asInt()).isEqualTo(9);
        var limited = vote(cookie, today(), "meal-02", true);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 5L);
        assertThat(limited.getBody().at("/summary/remainingToday").asInt()).isEqualTo(9);
        allowNextVote(cookie);
        var changed = vote(cookie, today(), "meal-02", true);
        assertThat(changed.getBody().get("remainingToday").asInt()).isEqualTo(8);
        assertThat(changed.getBody().get("total").asInt()).isEqualTo(1);
        assertThat(changed.getBody().get("myMealId").asText()).isEqualTo("meal-02");
        String recovered = cookie(browserIdentity(null, key, true));
        assertThat(recovered).isEqualTo(cookie);
        assertThat(vote(recovered, today(), null, true).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        allowNextVote(recovered);
        var cancelled = vote(recovered, today(), null, true);
        assertThat(cancelled.getBody().get("total").asInt()).isZero();
        assertThat(cancelled.getBody().get("remainingToday").asInt()).isEqualTo(7);
        assertThat(cancelled.getBody().hasNonNull("myMealId")).isFalse();
        assertThat(send(cookie, "채팅 제한은 별도", true).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void floatingVoteDailyTenLimitResetsBySeoulDateWhilePreservingFiveSecondInterval() {
        String user = cookie(identity(null));
        for (int i = 1; i <= 10; i++) {
            allowNextVote(user);
            var result = vote(user, today(), "meal-%02d".formatted(i), true);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().get("remainingToday").asInt()).isEqualTo(10 - i);
        }
        allowNextVote(user);
        assertThat(vote(user, today(), "meal-11", true).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_choices", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE meal_choice_limits SET quota_date = ?, last_vote_at = now() WHERE user_token = ?",
                today().minusDays(1), user.substring(user.indexOf('=') + 1));
        var midnight = vote(user, today(), "meal-11", true);
        assertThat(midnight.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(midnight.getBody().at("/summary/remainingToday").asInt()).isEqualTo(10);
        allowNextVote(user);
        assertThat(vote(user, today(), "meal-11", true).getBody().get("remainingToday").asInt()).isEqualTo(9);
    }

    @Test
    void concurrentFloatingVotesSaveOneActionAndOneSelection() throws Exception {
        String user = cookie(identity(null));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var jobs = new ArrayList<java.util.concurrent.Future<ResponseEntity<JsonNode>>>();
            for (int i = 1; i <= 8; i++) {
                String meal = "meal-%02d".formatted(i);
                jobs.add(executor.submit(() -> { start.await(); return vote(user, today(), meal, true); }));
            }
            start.countDown();
            int saved = 0;
            for (var job : jobs) {
                var result = job.get(10, java.util.concurrent.TimeUnit.SECONDS);
                if (result.getStatusCode() == HttpStatus.OK) saved++;
                else assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
            assertThat(saved).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_choices", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT daily_count FROM meal_choice_limits", Integer.class)).isEqualTo(1);
    }

    @Test
    void floatingVoteValidatesDateIdentityHeaderAndMeal() {
        String user = cookie(identity(null));
        assertThat(vote(user, today().minusDays(1), "meal-01", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(vote(user, today().plusDays(1), "meal-01", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(vote(null, today(), "meal-01", true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(vote(user, today(), "meal-01", false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(vote(user, today(), "invalid", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(meals.mealExists(any(LocalDate.class), anyString())).thenReturn(false);
        assertThat(vote(user, today(), "meal-01", true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_choice_limits", Integer.class)).isZero();
    }

    private Map<String, Object> menu(String id, String name, String imageFile, boolean placeholder) {
        var menu = new java.util.HashMap<String, Object>();
        menu.put("id", id);
        menu.put("name", name);
        menu.put("courseName", "동방식객");
        menu.put("description", name + ", 밥과 반찬");
        menu.put("calorie", "876 kcal");
        menu.put("imageFile", imageFile);
        menu.put("imageHash", imageFile == null ? null : "fixture-hash");
        menu.put("imageContentType", "image/png");
        menu.put("placeholder", placeholder);
        return menu;
    }

    private void day(LocalDate date, List<Map<String, Object>> menus) throws Exception {
        jdbc.update("""
                INSERT INTO meal_days (meal_date, restaurant_name, complete, meals_json, last_updated_at)
                VALUES (?, '테스트 식당', false, ?, ?)
                ON CONFLICT (meal_date) DO UPDATE SET meals_json = EXCLUDED.meals_json
                """, date, mapper.writeValueAsString(menus), Timestamp.from(Instant.now()));
        Path directory = imageDirectory.resolve(date.toString());
        Files.createDirectories(directory);
        for (var menu : menus) if (menu.get("imageFile") != null) {
            var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(image, "png", directory.resolve((String) menu.get("imageFile")).toFile());
        }
    }

    @Test
    void referenceUsesLatestRealPhotoOfSameNameAndStopsWhenCurrentPhotoArrives() throws Exception {
        day(today().minusDays(1), List.of(menu("meal-01", "분짜", "placeholder.png", true)));
        day(today().minusDays(2), List.of(menu("meal-01", "분짜", "real.png", false)));
        day(today().minusDays(3), List.of(menu("meal-01", "매콤분짜", "other.png", false)));
        day(today().plusDays(1), List.of(menu("meal-01", "분짜", "future.png", false)));
        day(today(), List.of(menu("meal-02", "분짜", null, false)));
        var reference = http.getForEntity("/api/menu-history/references?date=" + today(), JsonNode.class);
        assertThat(reference.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reference.getBody().at("/meal-02/date").asText()).isEqualTo(today().minusDays(2).toString());
        assertThat(reference.getBody().at("/meal-02/imageUrl").asText()).contains("/images/meal-01?v=fixture-hash");
        day(today(), List.of(menu("meal-02", "분짜", "today.png", false)));
        var current = http.getForEntity("/api/menu-history/references?date=" + today(), JsonNode.class);
        assertThat(current.getBody().has("meal-02")).isFalse();
    }

    @Test
    void referenceNormalizesTagsSpacingAndSymbolsButRequiresExactDishAndBrandNames() throws Exception {
        day(today().minusDays(1), List.of(menu("meal-01", "참치 김치찌개", "different.png", false)));
        day(today().minusDays(2), List.of(
                menu("meal-01", "[트렌드미식회]_이차돌 된장찌개", "stew.png", false),
                menu("meal-02", "[테이스티가든] 소 불고기", "beef.png", false),
                menu("meal-03", "분\u00a0짜", "buncha.png", false),
                menu("meal-04", "[라면 14종 중 택1]", "ramen.png", false),
                menu("meal-05", "[특선]", "tag-only.png", false)));
        day(today(), List.of(
                menu("meal-01", "된 장 찌 개", null, false),
                menu("meal-02", "소불고기!", null, false),
                menu("meal-03", "[오늘의 메뉴]분\u3000짜", null, false),
                menu("meal-04", "김치찌개", null, false),
                menu("meal-05", "[라면14종중택1]", null, false),
                menu("meal-06", "[]", null, false),
                menu("meal-07", "이 차 돌 된 장 찌 개", null, false)));
        var response = http.getForEntity("/api/menu-history/references?date=" + today(), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(4);
        assertThat(response.getBody().has("meal-01")).isFalse();
        assertThat(response.getBody().at("/meal-07/imageUrl").asText()).contains("/images/meal-01?");
        assertThat(response.getBody().at("/meal-02/imageUrl").asText()).contains("/images/meal-02?");
        assertThat(response.getBody().at("/meal-03/imageUrl").asText()).contains("/images/meal-03?");
        assertThat(response.getBody().at("/meal-05/imageUrl").asText()).contains("/images/meal-04?");
        assertThat(response.getBody().has("meal-04")).isFalse();
        assertThat(response.getBody().has("meal-06")).isFalse();
    }

}
