package com.ssafy.welstory.web;

import com.ssafy.welstory.config.AdminProperties;
import com.ssafy.welstory.config.WelstoryProperties;
import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.MealModels;
import com.ssafy.welstory.meal.RatingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MealCacheService cache;
    private final RatingService ratings;
    private final WelstoryProperties welstory;
    private final AdminProperties admin;

    public AdminController(MealCacheService cache, RatingService ratings, WelstoryProperties welstory,
                           AdminProperties admin) {
        this.cache = cache;
        this.ratings = ratings;
        this.welstory = welstory;
        this.admin = admin;
    }

    @GetMapping("/status")
    public ResponseEntity<AdminStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AdminStatus> refresh(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        cache.refresh(date == null ? LocalDate.now(SEOUL) : date);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot());
    }

    private AdminStatus snapshot() {
        return new AdminStatus(Instant.now(), welstory.restaurantName(), welstory.restaurantCode(),
                welstory.hasCredentials(), admin.configured(), welstory.cacheDir().toAbsolutePath().normalize().toString(),
                "평일 06:00–10:40 (Asia/Seoul)", welstory.retryInterval(), welstory.offHoursRetryInterval(),
                cache.inspectCaches(), ratings.stats());
    }

    public record AdminStatus(
            Instant serverTime,
            String restaurantName,
            String restaurantCode,
            boolean welstoryCredentialsConfigured,
            boolean adminCredentialsConfigured,
            String cacheDirectory,
            String pollingSchedule,
            Duration retryInterval,
            Duration offHoursRetryInterval,
            List<MealModels.CacheEntry> caches,
            RatingService.RatingStats ratings
    ) {}
}
