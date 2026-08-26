package com.ssafy.welstory.meal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class MealRefreshScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MealCacheService cache;

    public MealRefreshScheduler(MealCacheService cache) {
        this.cache = cache;
    }

    @Scheduled(cron = "0 */5 6-8 * * *", zone = "Asia/Seoul")
    public void ensureTodayMenu() {
        LocalDate today = LocalDate.now(SEOUL);
        if (!cache.hasMealData(today)) {
            cache.refreshMenu(today);
        }
    }

    @Scheduled(cron = "0 */5 9-18 * * *", zone = "Asia/Seoul")
    public void pollTodayPhotos() {
        if (LocalTime.now(SEOUL).isAfter(LocalTime.of(18, 0))) return;
        cache.refreshPhotosIfDue(LocalDate.now(SEOUL));
    }
}
