package com.ssafy.welstory.chat;

import com.fasterxml.jackson.databind.JsonNode;
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
    @Autowired TestRestTemplate http;
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
    }

    @BeforeEach
    void mealExists() {
        when(meals.mealExists(any(LocalDate.class), anyString())).thenReturn(true);
    }

    @AfterAll
    static void cleanup() {
        if (root != null) root.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    private ResponseEntity<JsonNode> identity(String cookie) {
        var headers = new HttpHeaders();
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        return http.exchange("/api/chat/identity", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private String cookie(ResponseEntity<JsonNode> response) {
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
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
}
