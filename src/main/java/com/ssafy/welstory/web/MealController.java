package com.ssafy.welstory.web;

import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.MealModels;
import org.springframework.core.io.FileSystemResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/meals")
public class MealController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MealCacheService cache;

    public MealController(MealCacheService cache) {
        this.cache = cache;
    }

    @GetMapping
    public ResponseEntity<MealModels.MealDayResponse> meals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(SEOUL) : date;
        MealModels.MealDayResponse response = cache.get(target);
        CacheControl policy = response.status() == MealModels.Status.READY
                ? CacheControl.maxAge(Duration.ofHours(12)).cachePublic().immutable()
                : CacheControl.noCache();
        return ResponseEntity.ok().cacheControl(policy).body(response);
    }

    @GetMapping("/{date}/images/{mealId}")
    public ResponseEntity<FileSystemResource> image(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String mealId) {
        return cache.image(date, mealId)
                .map(asset -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .contentType(MediaType.parseMediaType(asset.contentType()))
                        .body(new FileSystemResource(asset.path())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
