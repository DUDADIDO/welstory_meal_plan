package com.ssafy.welstory.web;

import com.ssafy.welstory.analytics.VisitorStatsService;
import com.ssafy.welstory.config.AdminProperties;
import com.ssafy.welstory.config.WelstoryProperties;
import com.ssafy.welstory.logging.InMemoryLogService;
import com.ssafy.welstory.meal.CacheRangeJobService;
import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.MealModels;
import com.ssafy.welstory.meal.RatingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.ACCEPTED;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MealCacheService cache;
    private final RatingService ratings;
    private final WelstoryProperties welstory;
    private final AdminProperties admin;
    private final CacheRangeJobService rangeJobs;
    private final InMemoryLogService logs;
    private final VisitorStatsService visitors;

    public AdminController(MealCacheService cache, RatingService ratings, WelstoryProperties welstory,
                           AdminProperties admin, CacheRangeJobService rangeJobs, InMemoryLogService logs, VisitorStatsService visitors) {
        this.cache = cache;
        this.ratings = ratings;
        this.welstory = welstory;
        this.admin = admin;
        this.rangeJobs = rangeJobs;
        this.logs = logs;
        this.visitors = visitors;
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

    @PostMapping("/cache-jobs")
    public ResponseEntity<CacheRangeJobService.JobProgress> startCacheJob(@RequestBody CacheRangeRequest request) {
        try {
            return ResponseEntity.status(ACCEPTED).cacheControl(CacheControl.noStore())
                    .body(rangeJobs.start(request.startDate(), request.endDate()));
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(CONFLICT, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(BAD_REQUEST, error.getMessage(), error);
        }
    }

    @DeleteMapping("/cache-jobs/current")
    public ResponseEntity<CacheRangeJobService.JobProgress> cancelCacheJob() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rangeJobs.cancel());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<InMemoryLogService.LogEntry>> logs(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(logs.recent(after, limit));
    }

    private AdminStatus snapshot() {
        return new AdminStatus(Instant.now(), welstory.restaurantName(), welstory.restaurantCode(),
                welstory.hasCredentials(), admin.configured(), welstory.cacheDir().toAbsolutePath().normalize().toString(),
                "메뉴 매일 06:00부터 · 사진 매일 09:00–18:00, 5분 간격 (Asia/Seoul)",
                welstory.retryInterval(), welstory.offHoursRetryInterval(),
                cache.inspectCaches(), ratings.stats(), rangeJobs.progress(), visitors.stats());
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
            RatingService.RatingStats ratings,
            CacheRangeJobService.JobProgress cacheJob,
            VisitorStatsService.Stats visitors
    ) {}

    public record CacheRangeRequest(LocalDate startDate, LocalDate endDate) {}
}
