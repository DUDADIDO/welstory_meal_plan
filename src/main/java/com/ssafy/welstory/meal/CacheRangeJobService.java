package com.ssafy.welstory.meal;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CacheRangeJobService {

    private static final int MAX_RANGE_DAYS =
            366;

    private static final Duration REQUEST_INTERVAL =
            Duration.ofSeconds(30);

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final MealCacheService cache;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    Thread.ofVirtual()
                            .name(
                                    "cache-range-job-",
                                    0
                            )
                            .factory()
            );

    private final AtomicBoolean cancelRequested =
            new AtomicBoolean();

    private volatile JobProgress current;

    public CacheRangeJobService(
            MealCacheService cache
    ) {
        this.cache = cache;
    }

    public synchronized JobProgress start(
            LocalDate startDate,
            LocalDate endDate
    ) {

        validateRange(
                startDate,
                endDate
        );

        if (
                current != null
                        && current.active()
        ) {

            throw new IllegalStateException(
                    "이미 날짜 범위 캐시 작업이 실행 중입니다."
            );
        }

        int total =
                Math.toIntExact(
                        startDate
                                .datesUntil(
                                        endDate.plusDays(1)
                                )
                                .count()
                );

        current =
                new JobProgress(
                        UUID.randomUUID().toString(),
                        JobStatus.QUEUED,
                        startDate,
                        endDate,
                        total,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        Instant.now(),
                        null
                );

        cancelRequested.set(false);

        executor.submit(
                () ->
                        run(
                                current.id(),
                                startDate,
                                endDate,
                                total
                        )
        );

        return current;
    }

    public JobProgress progress() {
        return current;
    }

    public synchronized JobProgress cancel() {

        if (
                current != null
                        && current.active()
        ) {

            cancelRequested.set(true);
        }

        return current;
    }

    private void run(
            String jobId,
            LocalDate startDate,
            LocalDate endDate,
            int total
    ) {

        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        String lastError = null;

        try {

            for (
                    LocalDate date = startDate;
                    !date.isAfter(endDate);
                    date = date.plusDays(1)
            ) {

                if (cancelRequested.get()) {

                    current =
                            progress(
                                    jobId,
                                    JobStatus.CANCELLED,
                                    startDate,
                                    endDate,
                                    total,
                                    processed,
                                    succeeded,
                                    failed,
                                    skipped,
                                    date,
                                    null,
                                    lastError,
                                    current.startedAt(),
                                    Instant.now()
                            );

                    return;
                }

                current =
                        progress(
                                jobId,
                                JobStatus.RUNNING,
                                startDate,
                                endDate,
                                total,
                                processed,
                                succeeded,
                                failed,
                                skipped,
                                date,
                                null,
                                lastError,
                                current.startedAt(),
                                null
                        );

                /*
                 * 관리자 범위 캐시는 과거 날짜도
                 * 이미지까지 전부 다운로드해야 한다.
                 */
                MealCacheService.RefreshResult result =
                        cache.refreshWithImages(date);

                boolean upstreamCalled =
                        result.upstreamCalled();

                if (result.successful()) {

                    if (
                            result.state()
                                    == MealCacheService.RefreshState.ALREADY_COMPLETE
                    ) {

                        skipped++;

                    } else {

                        succeeded++;
                    }

                } else {

                    failed++;

                    lastError =
                            date
                                    + ": "
                                    + result.message();
                }

                processed++;

                boolean hasMore =
                        date.isBefore(endDate);

                if (
                        upstreamCalled
                                && hasMore
                                && !cancelRequested.get()
                ) {

                    Instant nextAttemptAt =
                            Instant.now()
                                    .plus(
                                            REQUEST_INTERVAL
                                    );

                    current =
                            progress(
                                    jobId,
                                    JobStatus.WAITING,
                                    startDate,
                                    endDate,
                                    total,
                                    processed,
                                    succeeded,
                                    failed,
                                    skipped,
                                    date,
                                    nextAttemptAt,
                                    lastError,
                                    current.startedAt(),
                                    null
                            );

                    if (!waitForNextRequest()) {

                        current =
                                progress(
                                        jobId,
                                        JobStatus.CANCELLED,
                                        startDate,
                                        endDate,
                                        total,
                                        processed,
                                        succeeded,
                                        failed,
                                        skipped,
                                        date,
                                        null,
                                        lastError,
                                        current.startedAt(),
                                        Instant.now()
                                );

                        return;
                    }
                }
            }

            current =
                    progress(
                            jobId,
                            JobStatus.COMPLETED,
                            startDate,
                            endDate,
                            total,
                            processed,
                            succeeded,
                            failed,
                            skipped,
                            null,
                            null,
                            lastError,
                            current.startedAt(),
                            Instant.now()
                    );

        } catch (Exception error) {

            current =
                    progress(
                            jobId,
                            JobStatus.FAILED,
                            startDate,
                            endDate,
                            total,
                            processed,
                            succeeded,
                            failed + 1,
                            skipped,
                            current == null
                                    ? null
                                    : current.currentDate(),
                            null,
                            error.getMessage(),
                            current == null
                                    ? Instant.now()
                                    : current.startedAt(),
                            Instant.now()
                    );
        }
    }

    private boolean waitForNextRequest() {

        long remaining =
                REQUEST_INTERVAL.toMillis();

        while (
                remaining > 0
                        && !cancelRequested.get()
        ) {

            long slice =
                    Math.min(
                            remaining,
                            500
                    );

            try {

                Thread.sleep(slice);

            } catch (
                    InterruptedException interrupted
            ) {

                Thread.currentThread()
                        .interrupt();

                return false;
            }

            remaining -= slice;
        }

        return !cancelRequested.get();
    }

    private static JobProgress progress(
            String id,
            JobStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int total,
            int processed,
            int succeeded,
            int failed,
            int skipped,
            LocalDate currentDate,
            Instant nextAttemptAt,
            String lastError,
            Instant startedAt,
            Instant finishedAt
    ) {

        return new JobProgress(
                id,
                status,
                startDate,
                endDate,
                total,
                processed,
                succeeded,
                failed,
                skipped,
                currentDate,
                nextAttemptAt,
                lastError,
                startedAt,
                finishedAt
        );
    }

    private static void validateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (
                startDate == null
                        || endDate == null
        ) {

            throw new IllegalArgumentException(
                    "시작일과 종료일이 필요합니다."
            );
        }

        if (startDate.isAfter(endDate)) {

            throw new IllegalArgumentException(
                    "시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        long days =
                startDate
                        .datesUntil(
                                endDate.plusDays(1)
                        )
                        .count();

        if (days > MAX_RANGE_DAYS) {

            throw new IllegalArgumentException(
                    "한 번에 최대 366일까지 처리할 수 있습니다."
            );
        }

        if (
                endDate.isAfter(
                        LocalDate.now(SEOUL)
                )
        ) {

            throw new IllegalArgumentException(
                    "미래 날짜는 캐시할 수 없습니다."
            );
        }
    }

    @PreDestroy
    void shutdown() {

        cancelRequested.set(true);

        executor.shutdownNow();
    }

    public enum JobStatus {
        QUEUED,
        RUNNING,
        WAITING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public record JobProgress(
            String id,
            JobStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int total,
            int processed,
            int succeeded,
            int failed,
            int skipped,
            LocalDate currentDate,
            Instant nextAttemptAt,
            String lastError,
            Instant startedAt,
            Instant finishedAt
    ) {

        public boolean active() {

            return status == JobStatus.QUEUED
                    || status == JobStatus.RUNNING
                    || status == JobStatus.WAITING;
        }

        public int progressPercent() {

            return total == 0
                    ? 0
                    : (int) Math.floor(
                    (processed * 100.0)
                            / total
            );
        }
    }
}