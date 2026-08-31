package com.ssafy.welstory.meal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheRangeJobServiceTest {

    @Test
    void acceptsFutureDateForAdminCache() throws InterruptedException {
        MealCacheService cache = mock(MealCacheService.class);
        CacheRangeJobService jobs = new CacheRangeJobService(cache);
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        CountDownLatch refreshed = new CountDownLatch(1);

        when(cache.refreshWithImages(futureDate, false))
                .thenAnswer(invocation -> {
                    refreshed.countDown();
                    return new MealCacheService.RefreshResult(
                            MealCacheService.RefreshState.COMPLETE,
                            true,
                            "캐시 완료"
                    );
                });

        try {
            CacheRangeJobService.JobProgress progress =
                    jobs.start(futureDate, futureDate, false);

            assertThat(progress.startDate()).isEqualTo(futureDate);
            assertThat(refreshed.await(2, TimeUnit.SECONDS)).isTrue();
            verify(cache).refreshWithImages(futureDate, false);
        } finally {
            jobs.shutdown();
        }
    }
}
