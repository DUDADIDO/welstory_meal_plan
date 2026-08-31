package com.ssafy.welstory.meal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MealRefreshSchedulerTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void refreshesTodayThroughSevenDaysAhead() {
        MealCacheService cache = mock(MealCacheService.class);
        when(cache.refresh(any(LocalDate.class), eq(true)))
                .thenReturn(new MealCacheService.RefreshResult(
                        MealCacheService.RefreshState.PARTIAL,
                        true,
                        "메뉴는 확인됐지만 사진을 기다리고 있어요."
                ));
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), SEOUL);
        MealRefreshScheduler scheduler = new MealRefreshScheduler(cache, clock);

        scheduler.refreshUpcomingWeek();

        LocalDate today = LocalDate.of(2026, 8, 31);
        for (int offset = 0; offset <= 7; offset++) {
            verify(cache).refresh(today.plusDays(offset), true);
        }
        verifyNoMoreInteractions(cache);
    }
}
