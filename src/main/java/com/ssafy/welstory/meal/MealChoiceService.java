package com.ssafy.welstory.meal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class MealChoiceService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;

    public MealChoiceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate date, String token) {
        return summary(date, token, Instant.now());
    }

    private Summary summary(LocalDate date, String token, Instant now) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("SELECT meal_id, count(*) AS votes FROM meal_choices WHERE meal_date = ? GROUP BY meal_id ORDER BY meal_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getString("meal_id"), rs.getInt("votes")), date);
        var mine = token == null ? java.util.List.<String>of() : jdbc.query(
                "SELECT meal_id FROM meal_choices WHERE meal_date = ? AND user_token = ?", (rs, row) -> rs.getString(1), date, token);
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        var limits = token == null ? java.util.List.<Limit>of() : jdbc.query(
                "SELECT quota_date, daily_count, last_vote_at FROM meal_choice_limits WHERE user_token = ?",
                (rs, row) -> new Limit(rs.getObject(1, LocalDate.class), rs.getInt(2), rs.getTimestamp(3).toInstant()), token);
        Limit limit = limits.isEmpty() ? null : limits.getFirst();
        int used = limit != null && today.equals(limit.date()) ? limit.count() : 0;
        return new Summary(date, counts, counts.values().stream().mapToInt(Integer::intValue).sum(),
                mine.isEmpty() ? null : mine.getFirst(), 10 - used,
                limit == null ? now : limit.last().plusSeconds(5), today.plusDays(1).atStartOfDay(SEOUL).toInstant(), now);
    }

    @Transactional
    public Summary choose(LocalDate date, String token, String mealId) {
        if (token == null || token.length() > 64 || jdbc.query(
                "SELECT token FROM chat_users WHERE token = ? FOR UPDATE", (rs, row) -> rs.getString(1), token).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "익명 사용자 정보를 다시 확인해 주세요.");
        }
        Instant now = Instant.now(); // Enforce time after acquiring the user lock across tabs/instances.
        if (!date.equals(now.atZone(SEOUL).toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오늘 식단에만 투표할 수 있습니다.");
        }
        Summary previous = summary(date, token, now);
        if (Objects.equals(previous.myMealId(), mealId)) return previous; // Retries cannot consume another action.
        if (previous.remainingToday() == 0) throw new RateLimited("오늘 투표 10회를 모두 사용했습니다.", previous, previous.resetsAt());
        if (now.isBefore(previous.nextVoteAt())) throw new RateLimited("투표는 5초에 한 번 할 수 있습니다.", previous, previous.nextVoteAt());
        if (mealId == null) jdbc.update("DELETE FROM meal_choices WHERE meal_date = ? AND user_token = ?", date, token);
        else jdbc.update("""
                INSERT INTO meal_choices (meal_date, user_token, meal_id, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (meal_date, user_token) DO UPDATE SET meal_id = EXCLUDED.meal_id, updated_at = EXCLUDED.updated_at
                """, date, token, mealId, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO meal_choice_limits (user_token, quota_date, daily_count, last_vote_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_token) DO UPDATE SET quota_date = EXCLUDED.quota_date,
                    daily_count = EXCLUDED.daily_count, last_vote_at = EXCLUDED.last_vote_at
                """, token, date, 11 - previous.remainingToday(), Timestamp.from(now));
        return summary(date, token, now);
    }

    private record Limit(LocalDate date, int count, Instant last) {}
    public record Summary(LocalDate date, Map<String, Integer> counts, int total, String myMealId,
                          int remainingToday, Instant nextVoteAt, Instant resetsAt, Instant serverTime) {}

    public static class RateLimited extends ResponseStatusException {
        public final Summary summary;
        public final long retryAfterSeconds;
        RateLimited(String message, Summary summary, Instant until) {
            super(HttpStatus.TOO_MANY_REQUESTS, message);
            this.summary = summary;
            this.retryAfterSeconds = Math.max(1, (Duration.between(summary.serverTime(), until).toMillis() + 999) / 1000);
        }
    }
}
