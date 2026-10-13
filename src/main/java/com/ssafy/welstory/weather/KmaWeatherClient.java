package com.ssafy.welstory.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

final class KmaWeatherClient {
    private static final Logger log = LoggerFactory.getLogger(KmaWeatherClient.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private final RestClient api;
    private final String key, baseUrl;
    private final double latitude, longitude;
    private final Grid grid;

    KmaWeatherClient(RestClient.Builder builder, String baseUrl, String serviceKey, double latitude, double longitude) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        api = builder.clone().baseUrl(baseUrl).build();
        key = serviceKey.contains("%") ? URLDecoder.decode(serviceKey.replace("+", "%2B"), StandardCharsets.UTF_8) : serviceKey;
        this.latitude = latitude;
        this.longitude = longitude;
        grid = grid(latitude, longitude);
    }

    WeatherService.Snapshot fetch(Instant now, Instant nextCheck) {
        ZonedDateTime local = now.atZone(SEOUL);
        ZonedDateTime observed = local.minusMinutes(10).truncatedTo(ChronoUnit.HOURS);
        Map<String, String> observations = read("getUltraSrtNcst", observed, null);
        double temperature = required(observations, "T1H");
        double rawType = required(observations, "PTY");
        if (rawType != Math.rint(rawType)) throw new IllegalStateException("Invalid precipitation type");
        int precipitationType = (int) rawType;
        if (temperature < -80 || temperature > 60) throw new IllegalStateException("Invalid observed temperature");
        Map<String, String> forecast = Map.of();
        try {
            // The previous hour's :30 run contains the current hour's SKY/LGT.
            // The latest run can start at the following hour, so it must not
            // replace current observations with a future rain prediction.
            ZonedDateTime base = local.minusHours(1).withMinute(30).withSecond(0).withNano(0);
            forecast = read("getUltraSrtFcst", base, local.truncatedTo(ChronoUnit.HOURS));
        } catch (Exception error) {
            // Observation remains useful even when supplementary sky data fails.
            // Never log RestClient exception messages: they can include the key.
            log.warn("KMA sky supplement unavailable: {}", error.getClass().getSimpleName());
        }
        WeatherService.Condition condition = classify(precipitationType, optional(forecast, "SKY"), optional(forecast, "LGT"));
        Double precipitation = optional(observations, "RN1");
        return new WeatherService.Snapshot("녹산산업단지", "READY", condition.kind(), condition.label(), temperature,
                null, precipitation, null, observed.toInstant(), now, nextCheck,
                null, null, latitude, longitude, "KMA");
    }

    private Map<String, String> read(String endpoint, ZonedDateTime base, ZonedDateTime target) {
        // Expand the key as a URI variable, so '+', '/', '=' and '%' are encoded
        // as data instead of being interpreted as query delimiters.
        URI uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl).path("/" + endpoint)
                .queryParam("serviceKey", "{key}").queryParam("pageNo", 1).queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON").queryParam("base_date", DATE.format(base))
                .queryParam("base_time", TIME.format(base)).queryParam("nx", grid.x()).queryParam("ny", grid.y())
                .encode().buildAndExpand(key).toUri();
        JsonNode body = api.get().uri(uri).retrieve().body(JsonNode.class);
        if (body == null || !"00".equals(body.path("response").path("header").path("resultCode").asText())) {
            throw new IllegalStateException("KMA response unavailable");
        }
        JsonNode items = body.path("response").path("body").path("items").path("item");
        if (!items.isArray()) throw new IllegalStateException("KMA items unavailable");
        Map<String, String> result = new HashMap<>();
        for (JsonNode item : items) {
            if (target != null && (!DATE.format(target).equals(item.path("fcstDate").asText())
                    || !TIME.format(target).equals(item.path("fcstTime").asText()))) continue;
            if (target == null && (!DATE.format(base).equals(item.path("baseDate").asText())
                    || !TIME.format(base).equals(item.path("baseTime").asText()))) continue;
            result.put(item.path("category").asText(), item.path(target == null ? "obsrValue" : "fcstValue").asText());
        }
        return result;
    }

    private static double required(Map<String, String> values, String field) {
        Double value = optional(values, field);
        if (value == null) throw new IllegalStateException("KMA observation missing");
        return value;
    }

    private static Double optional(Map<String, String> values, String field) {
        try {
            double value = Double.parseDouble(values.get(field));
            return Double.isFinite(value) && value > -900 ? value : null;
        } catch (Exception ignored) { return null; }
    }

    static WeatherService.Condition classify(int precipitation, Double sky, Double lightning) {
        boolean thunder = lightning != null && lightning > 0;
        return switch (precipitation) {
            case 1 -> new WeatherService.Condition(thunder ? "storm" : "rain", thunder ? "천둥·번개를 동반한 비" : "비");
            case 2, 6 -> new WeatherService.Condition(thunder ? "storm" : "rain", "비·눈");
            case 3, 7 -> new WeatherService.Condition("snow", precipitation == 7 ? "눈날림" : "눈");
            case 5 -> new WeatherService.Condition(thunder ? "storm" : "rain", "빗방울");
            case 0 -> sky == null ? new WeatherService.Condition(null, "기온 관측 · 하늘 상태 확인 지연")
                    : sky == 1 ? new WeatherService.Condition("clear", "맑음")
                    : sky == 3 ? new WeatherService.Condition("cloudy", "구름 많음")
                    : sky == 4 ? new WeatherService.Condition("cloudy", "흐림")
                    : new WeatherService.Condition(null, "기온 관측 · 하늘 상태 확인 지연");
            default -> throw new IllegalStateException("Unknown KMA precipitation type");
        };
    }

    static Grid grid(double latitude, double longitude) {
        double rad = Math.PI / 180, earth = 6371.00877 / 5;
        double sn = Math.log(Math.cos(30 * rad) / Math.cos(60 * rad))
                / Math.log(Math.tan(Math.PI / 4 + 60 * rad / 2) / Math.tan(Math.PI / 4 + 30 * rad / 2));
        double sf = Math.pow(Math.tan(Math.PI / 4 + 30 * rad / 2), sn) * Math.cos(30 * rad) / sn;
        double ro = earth * sf / Math.pow(Math.tan(Math.PI / 4 + 38 * rad / 2), sn);
        double ra = earth * sf / Math.pow(Math.tan(Math.PI / 4 + latitude * rad / 2), sn);
        double theta = (longitude - 126) * rad;
        if (theta > Math.PI) theta -= 2 * Math.PI;
        if (theta < -Math.PI) theta += 2 * Math.PI;
        theta *= sn;
        return new Grid((int) Math.floor(ra * Math.sin(theta) + 43 + .5), (int) Math.floor(ro - ra * Math.cos(theta) + 136 + .5));
    }

    record Grid(int x, int y) {}
}
