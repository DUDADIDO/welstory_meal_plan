package com.ssafy.welstory.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final Duration INTERVAL = Duration.ofMinutes(10);
    private final RestClient api;
    private final Clock clock;
    private final KmaWeatherClient kma;
    private final double latitude, longitude;
    private Snapshot latest;
    private Instant nextAttempt = Instant.EPOCH;
    private boolean failed;

    @Autowired
    public WeatherService(RestClient.Builder builder,
                          @Value("${weather.base-url:https://api.open-meteo.com}") String baseUrl,
                          @Value("${weather.latitude:35.09}") double latitude,
                          @Value("${weather.longitude:128.87}") double longitude,
                          @Value("${weather.kma-service-key:}") String kmaKey,
                          @Value("${weather.kma-base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String kmaBase) {
        this(withTimeouts(builder), baseUrl, latitude, longitude, Clock.systemUTC(), kmaKey, kmaBase);
    }

    private static RestClient.Builder withTimeouts(RestClient.Builder builder) {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.clone().requestFactory(factory);
    }

    WeatherService(RestClient.Builder builder, String baseUrl, double latitude, double longitude, Clock clock) {
        this(builder, baseUrl, latitude, longitude, clock, "", "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0");
    }

    WeatherService(RestClient.Builder builder, String baseUrl, double latitude, double longitude, Clock clock, String kmaKey, String kmaBase) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        api = builder.clone().baseUrl(baseUrl).build();
        this.latitude = latitude;
        this.longitude = longitude;
        this.clock = clock;
        this.kma = kmaKey == null || kmaKey.isBlank() ? null : new KmaWeatherClient(builder, kmaBase, kmaKey.strip(), latitude, longitude);
    }

    @Scheduled(fixedDelay = 600_000)
    public synchronized void refresh() {
        Instant now = clock.instant();
        if (now.isBefore(nextAttempt)) return;
        nextAttempt = now.plus(INTERVAL); // One attempt per 10 minutes, including failures.
        try {
            if (kma != null) {
                latest = kma.fetch(now, nextAttempt);
                failed = false;
                return;
            }
            JsonNode body = api.get().uri(uri -> uri.path("/v1/forecast")
                    .queryParam("latitude", latitude).queryParam("longitude", longitude)
                    .queryParam("current", "temperature_2m,weather_code,is_day,precipitation,cloud_cover")
                    .queryParam("daily", "sunrise,sunset").queryParam("forecast_days", 1)
                    .queryParam("timezone", "Asia/Seoul").queryParam("timeformat", "unixtime").build())
                    .retrieve().body(JsonNode.class);
            if (body == null) throw new IllegalStateException("Empty weather response");
            JsonNode current = body.path("current");
            if (!current.path("time").isNumber() || !current.path("temperature_2m").isNumber()
                    || !current.path("weather_code").isNumber() || !current.path("is_day").isNumber()) {
                throw new IllegalStateException("Incomplete current weather response");
            }
            var condition = condition(current.path("weather_code").asInt());
            latest = new Snapshot("녹산산업단지", "READY", condition.kind(), condition.label(),
                    current.path("temperature_2m").asDouble(), current.path("is_day").asInt() == 1,
                    current.path("precipitation").isNumber() ? current.path("precipitation").asDouble() : null,
                    current.path("cloud_cover").isNumber() ? current.path("cloud_cover").asInt() : null,
                    Instant.ofEpochSecond(current.path("time").asLong()), clock.instant(), nextAttempt, dailyTime(body, "sunrise"), dailyTime(body, "sunset"), latitude, longitude, "OPEN_METEO");
            failed = false;
        } catch (Exception error) {
            failed = true;
            log.warn("Current weather refresh failed: {}", error.getClass().getSimpleName());
        }
    }

    public synchronized Snapshot current() {
        refresh();
        if (latest == null) return new Snapshot("녹산산업단지", "UNAVAILABLE", null, null,
                null, null, null, null, null, null, nextAttempt, null, null, latitude, longitude, kma == null ? "OPEN_METEO" : "KMA");
        Instant now = clock.instant();
        boolean stale = failed || latest.fetchedAt().isBefore(now.minus(Duration.ofMinutes(20)))
                || latest.observedAt().isBefore(now.minus(Duration.ofMinutes("KMA".equals(latest.provider()) ? 90 : 30)))
                || latest.observedAt().isAfter(now.plus(Duration.ofMinutes(15)));
        return new Snapshot(latest.location(), stale ? "STALE" : "READY", latest.condition(), latest.description(),
                latest.temperature(), latest.isDay(), latest.precipitation(), latest.cloudCover(),
                latest.observedAt(), latest.fetchedAt(), nextAttempt, latest.sunrise(), latest.sunset(), latest.latitude(), latest.longitude(), latest.provider());
    }

    private static Instant dailyTime(JsonNode body, String field) {
        JsonNode value = body.path("daily").path(field).path(0);
        return value.isNumber() ? Instant.ofEpochSecond(value.asLong()) : null;
    }

    static Condition condition(int code) {
        return switch (code) {
            case 0 -> new Condition("clear", "맑음");
            case 1 -> new Condition("clear", "대체로 맑음");
            case 2 -> new Condition("cloudy", "구름 많음");
            case 3 -> new Condition("cloudy", "흐림");
            case 45, 48 -> new Condition("fog", "안개");
            case 51, 53, 55, 56, 57 -> new Condition("rain", "이슬비");
            case 61, 63, 65, 66, 67 -> new Condition("rain", "비");
            case 80, 81, 82 -> new Condition("rain", "소나기");
            case 71, 73, 75, 77, 85, 86 -> new Condition("snow", "눈");
            case 95, 96, 99 -> new Condition("storm", "뇌우");
            default -> throw new IllegalArgumentException("Unknown weather code");
        };
    }

    record Condition(String kind, String label) {}
    public record Snapshot(String location, String status, String condition, String description, Double temperature,
                           Boolean isDay, Double precipitation, Integer cloudCover, Instant observedAt,
                           Instant fetchedAt, Instant nextCheckAt, Instant sunrise, Instant sunset, Double latitude, Double longitude, String provider) {}
}
