package com.ssafy.welstory.meal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class MealRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(MealRefreshScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int UPCOMING_DAYS = 7;
    private final MealCacheService cache;
    private final Clock clock;

    @Autowired
    public MealRefreshScheduler(MealCacheService cache) {
        this(cache, Clock.system(SEOUL));
    }

    MealRefreshScheduler(MealCacheService cache, Clock clock) {
        this.cache = cache;
        this.clock = clock;
    }

    /**
     * 자정마다 오늘부터 7일 뒤까지의 메뉴 캐시를 강제로 갱신한다.
     * 미래 식단 변경을 놓치지 않도록 완료 캐시도 다시 확인한다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void refreshUpcomingWeek() {
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = today.plusDays(UPCOMING_DAYS);

        for (LocalDate date = today; !date.isAfter(endDate); date = date.plusDays(1)) {
            MealCacheService.RefreshResult result = cache.refresh(date, true);
            if (!result.successful()) {
                log.warn("Upcoming meal cache refresh did not complete: date={}, state={}, message={}",
                        date, result.state(), result.message());
            }
        }
    }

    @Scheduled(cron = "0 */5 6-8 * * *", zone = "Asia/Seoul")
    public void ensureTodayMenu() {
        LocalDate today = LocalDate.now(clock);
        if (!cache.hasMealData(today)) {
            cache.refreshMenu(today);
        }
    }

    @Scheduled(cron = "0 */5 9-18 * * *", zone = "Asia/Seoul")
    public void pollTodayPhotos() {
        if (LocalTime.now(clock).isAfter(LocalTime.of(18, 0))) return;
        cache.refreshPhotosIfDue(LocalDate.now(clock));
    }
}
