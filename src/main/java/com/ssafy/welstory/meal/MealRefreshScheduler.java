package com.ssafy.welstory.meal;

import com.ssafy.welstory.config.WelstoryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class MealRefreshScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MealCacheService cache;
    private final WelstoryProperties properties;

    public MealRefreshScheduler(MealCacheService cache, WelstoryProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Scheduled(cron = "0 */5 6-10 * * MON-FRI", zone = "Asia/Seoul")
    public void pollMorning() {
        if (!LocalTime.now(SEOUL).isAfter(LocalTime.of(10, 40))) {
            refreshTodayIfWorkingDay();
        }
    }

    private void refreshTodayIfWorkingDay() {
        LocalDate today = LocalDate.now(SEOUL);
        if (today.getDayOfWeek() != DayOfWeek.SATURDAY
                && today.getDayOfWeek() != DayOfWeek.SUNDAY
                && !properties.holidays().contains(today)) {
            cache.refresh(today);
        }
    }
}
