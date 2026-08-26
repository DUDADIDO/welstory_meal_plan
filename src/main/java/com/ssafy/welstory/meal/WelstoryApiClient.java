package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.welstory.config.WelstoryProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class WelstoryApiClient implements WelstoryGateway {
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<String> EXCLUDED_KEYWORDS = List.of("코인", "품목", "음료", "베이커리");

    private final RestClient api;
    private final RestClient images;
    private final WelstoryProperties properties;
    private final String deviceId = UUID.randomUUID().toString();
    private volatile String accessToken;
    private volatile Instant loginExpiresAt = Instant.EPOCH;

    public WelstoryApiClient(RestClient.Builder builder, WelstoryProperties properties) {
        this.properties = properties;
        this.api = builder.clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Welplus")
                .defaultHeader("X-Device-Id", deviceId)
                .build();
        this.images = builder.clone().build();
    }

    @Override
    public List<MealModels.UpstreamMeal> fetchLunch(LocalDate date) {
        ensureLoggedIn();
        try {
            return requestLunch(date);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            accessToken = null;
            ensureLoggedIn();
            return requestLunch(date);
        }
    }

    private List<MealModels.UpstreamMeal> requestLunch(LocalDate date) {
        JsonNode body = api.get()
                .uri(uriBuilder -> uriBuilder.path("/api/meal")
                        .queryParam("menuDt", API_DATE.format(date))
                        .queryParam("menuMealType", properties.mealType())
                        .queryParam("restaurantCode", properties.restaurantCode())
                        .build())
                .header("Authorization", accessToken)
                .retrieve()
                .body(JsonNode.class);

        JsonNode mealList = body == null ? null : body.path("data").path("mealList");
        if (mealList == null || !mealList.isArray()) {
            throw new IllegalStateException("웰스토리 식단 응답 형식이 올바르지 않습니다.");
        }

        List<MealModels.UpstreamMeal> meals = new ArrayList<>();
        for (JsonNode meal : mealList) {
            String name = text(meal, "menuName");
            String course = text(meal, "courseTxt");
            String searchable = name + course;
            if (EXCLUDED_KEYWORDS.stream().anyMatch(searchable::contains)) {
                continue;
            }
            meals.add(new MealModels.UpstreamMeal(
                    course,
                    name,
                    nullableText(meal, "subMenuTxt"),
                    text(meal, "photoUrl") + text(meal, "photoCd"),
                    calorie(meal)
            ));
            if (meals.size() == 6) {
                break;
            }
        }
        return meals;
    }

    @Override
    public MealModels.DownloadedImage downloadImage(String url) {
        ResponseEntity<byte[]> response = images.get()
                .uri(URI.create(url))
                .retrieve()
                .toEntity(byte[].class);
        MediaType contentType = response.getHeaders().getContentType();
        byte[] bytes = response.getBody();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("빈 식단 이미지가 반환되었습니다.");
        }
        return new MealModels.DownloadedImage(bytes,
                contentType == null ? MediaType.IMAGE_JPEG_VALUE : contentType.toString());
    }

    private synchronized void ensureLoggedIn() {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("WELSTORY_USERNAME과 WELSTORY_PASSWORD를 설정해 주세요.");
        }
        if (accessToken != null && Instant.now().isBefore(loginExpiresAt)) {
            return;
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", properties.username());
        form.add("password", properties.password());
        form.add("remember-me", "false");

        ResponseEntity<JsonNode> response = api.post()
                .uri("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Autologin", "N")
                .body(form)
                .retrieve()
                .toEntity(JsonNode.class);
        String token = response.getHeaders().getFirst("Authorization");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("웰스토리 로그인 토큰을 받지 못했습니다.");
        }
        accessToken = token;
        loginExpiresAt = Instant.now().plus(Duration.ofMinutes(25));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String nullableText(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : value;
    }

    private static String calorie(JsonNode meal) {
        String[] keys = {"calorie", "calories", "calorieTxt", "calorieInfo", "calorieValue",
                "kcal", "kcalTxt", "menuKcal", "menuKcalTxt", "menuCalorie", "energyKcal", "energy"};
        for (String key : keys) {
            String value = nullableText(meal, key);
            if (value != null) return normalizeCalorie(value);
        }
        for (String parent : new String[]{"nutrition", "nutrient", "nutritionInfo"}) {
            JsonNode nested = meal.path(parent);
            for (String key : keys) {
                String value = nullableText(nested, key);
                if (value != null) return normalizeCalorie(value);
            }
        }
        return findNestedCalorie(meal, 0);
    }

    private static String findNestedCalorie(JsonNode node, int depth) {
        if (node == null || depth > 3 || !node.isContainerNode()) return null;
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            JsonNode value = entry.getValue();
            if ((key.contains("calorie") || key.contains("kcal")) && value.isValueNode()) {
                String text = nullableText(node, entry.getKey());
                if (text != null) return normalizeCalorie(text);
            }
            String nested = findNestedCalorie(value, depth + 1);
            if (nested != null) return nested;
        }
        return null;
    }

    private static String normalizeCalorie(String value) {
        String trimmed = value.trim();
        return trimmed.matches(".*(?i)(kcal|칼로리).*") ? trimmed : trimmed + " kcal";
    }
}
