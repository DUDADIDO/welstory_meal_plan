package com.ssafy.welstory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "welstory")
public record WelstoryProperties(
        String baseUrl,
        String username,
        String password,
        String restaurantCode,
        String restaurantName,
        String mealType,
        Path cacheDir,
        Duration retryInterval,
        Duration offHoursRetryInterval
) {
    public WelstoryProperties {
        baseUrl = defaultIfBlank(baseUrl, "https://welplus.welstory.com");
        restaurantCode = defaultIfBlank(restaurantCode, "REST000595");
        restaurantName = defaultIfBlank(restaurantName, "삼성전기 부산사업장");
        mealType = defaultIfBlank(mealType, "2");
        cacheDir = cacheDir == null ? Path.of("./data/cache") : cacheDir;
        retryInterval = retryInterval == null ? Duration.ofMinutes(5) : retryInterval;
        offHoursRetryInterval = offHoursRetryInterval == null ? Duration.ofMinutes(30) : offHoursRetryInterval;
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
