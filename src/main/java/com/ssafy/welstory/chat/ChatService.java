package com.ssafy.welstory.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int DAILY_LIMIT = 50;
    private static final int PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ChatService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ChatService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Identity identity(String token) {
        Instant now = clock.instant();
        if (validToken(token)) {
            List<User> existing = users(token, false);
            if (!existing.isEmpty()) return new Identity(token, quota(existing.getFirst(), now));
        }
        String created = UUID.randomUUID().toString();
        // PostgreSQL resolves simultaneous nickname allocation without aborting the transaction.
        for (int attempt = 0; attempt < 200; attempt++) {
            String nickname = FoodNames.randomName();
            int inserted = jdbc.update("""
                    INSERT INTO chat_users (token, nickname, quota_date, daily_count, created_at)
                    VALUES (?, ?, ?, 0, ?) ON CONFLICT (nickname) DO NOTHING
                    """, created, nickname, now.atZone(SEOUL).toLocalDate(), Timestamp.from(now));
            if (inserted == 1) {
                return new Identity(created, quota(new User(nickname, now.atZone(SEOUL).toLocalDate(), 0, null), now));
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "익명 이름을 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Transactional
    public Identity browserIdentity(String token, String browserKey) {
        if (browserKey == null || !browserKey.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 브라우저 식별키입니다.");
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(browserKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
        // Serialize creation across tabs and instances without retaining the recovery secret.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, hash);
        var existing = jdbc.query("SELECT user_token FROM anonymous_browser_keys WHERE key_hash = ?",
                (rs, row) -> rs.getString(1), hash);
        if (!existing.isEmpty()) return identity(existing.getFirst());
        Identity user = identity(token);
        jdbc.update("INSERT INTO anonymous_browser_keys (key_hash, user_token, created_at) VALUES (?, ?, ?)",
                hash, user.token(), Timestamp.from(clock.instant()));
        return user;
    }

    @Transactional(readOnly = true)
    public Room room(LocalDate date, String mealId, String token, Long before) {
        Instant now = clock.instant();
        User user = requireUser(token, false);
        List<Message> messages = jdbc.query("""
                SELECT m.id, u.nickname, m.content, m.created_at, m.user_token = ? AS mine
                FROM chat_messages m JOIN chat_users u ON u.token = m.user_token
                WHERE m.meal_date = ? AND m.meal_id = ? AND m.id < ?
                ORDER BY m.id DESC LIMIT ?
                """, ChatService::message, token, date, mealId, before == null ? Long.MAX_VALUE : before, PAGE_SIZE + 1);
        boolean hasOlder = messages.size() > PAGE_SIZE;
        if (hasOlder) messages.removeLast();
        Collections.reverse(messages);
        return new Room(messages, hasOlder, quota(user, now));
    }

    @Transactional
    public Sent send(LocalDate date, String mealId, String token, String content) {
        String text = content.strip();
        if (text.isEmpty() || text.length() > 500 || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 공백을 제외한 1~500자로 작성해 주세요.");
        }
        // The database row lock applies across rooms, tabs and application instances.
        User user = requireUser(token, true);
        Instant now = clock.instant(); // Read time after acquiring the lock.
        Quota quota = quota(user, now);
        if (quota.remainingToday() == 0) {
            throw new RateLimited("오늘은 채팅을 모두 사용했습니다. 자정 이후 다시 참여해 주세요.", quota,
                    secondsUntil(now, quota.resetsAt()));
        }
        if (now.isBefore(quota.nextMessageAt())) {
            throw new RateLimited("메시지는 5초에 한 번 보낼 수 있습니다.", quota,
                    secondsUntil(now, quota.nextMessageAt()));
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO chat_messages (meal_date, meal_id, user_token, content, created_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, date, mealId, token, text, Timestamp.from(now));
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        int count = DAILY_LIMIT - quota.remainingToday() + 1;
        jdbc.update("UPDATE chat_users SET quota_date = ?, daily_count = ?, last_message_at = ? WHERE token = ?",
                today, count, Timestamp.from(now), token);
        return new Sent(new Message(id, user.nickname(), text, now, true),
                quota(new User(user.nickname(), today, count, now), now));
    }

    private User requireUser(String token, boolean lock) {
        List<User> users = validToken(token) ? users(token, lock) : List.of();
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "익명 사용자 정보가 없습니다. 채팅방을 다시 열어 주세요.");
        return users.getFirst();
    }

    private List<User> users(String token, boolean lock) {
        return jdbc.query("SELECT nickname, quota_date, daily_count, last_message_at FROM chat_users WHERE token = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new User(rs.getString("nickname"), rs.getObject("quota_date", LocalDate.class),
                        rs.getInt("daily_count"), rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toInstant()), token);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static Quota quota(User user, Instant now) {
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        int used = today.equals(user.quotaDate()) ? user.dailyCount() : 0;
        Instant next = user.lastMessageAt() == null ? now : user.lastMessageAt().plusSeconds(5);
        return new Quota(user.nickname(), DAILY_LIMIT - used, next, today.plusDays(1).atStartOfDay(SEOUL).toInstant(), now);
    }

    private static long secondsUntil(Instant now, Instant until) {
        return Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
    }

    private static Message message(ResultSet rs, int row) throws SQLException {
        return new Message(rs.getLong("id"), rs.getString("nickname"), rs.getString("content"),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("mine"));
    }

    private record User(String nickname, LocalDate quotaDate, int dailyCount, Instant lastMessageAt) {}
    public record Identity(String token, Quota user) {}
    public record Quota(String nickname, int remainingToday, Instant nextMessageAt, Instant resetsAt, Instant serverTime) {}
    public record Message(long id, String nickname, String content, Instant createdAt, boolean mine) {}
    public record Room(List<Message> messages, boolean hasOlder, Quota user) {}
    public record Sent(Message message, Quota user) {}

    public static class RateLimited extends ResponseStatusException {
        public final Quota user;
        public final long retryAfterSeconds;

        RateLimited(String reason, Quota user, long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, reason);
            this.user = user;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
