package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.welstory.config.WelstoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MealCacheServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void completedDayNeverCallsUpstreamAgain() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        AtomicInteger fetches = new AtomicInteger();
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                fetches.incrementAndGet();
                return List.of(new MealModels.UpstreamMeal("한식", "돼지불백", "밥 · 국 · 반찬", "https://image.test/menu.jpg"));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                return new MealModels.DownloadedImage(new byte[]{1, 2, 3}, "image/jpeg");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30), Set.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, clock);

        MealModels.MealDayResponse first = service.get(date);
        MealModels.MealDayResponse second = service.get(date);

        assertThat(first.status()).isEqualTo(MealModels.Status.READY);
        assertThat(second.status()).isEqualTo(MealModels.Status.READY);
        assertThat(fetches).hasValue(1);
        assertThat(second.meals().getFirst().imageUrl()).isEqualTo("/api/meals/2026-08-26/images/meal-01");
        assertThat(service.image(date, "meal-01")).isPresent();
    }

    @Test
    void missingImageRemainsWaitingAndRespectsRetryInterval() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        AtomicInteger fetches = new AtomicInteger();
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                fetches.incrementAndGet();
                return List.of(new MealModels.UpstreamMeal("일품", "준비 중", null, ""));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                throw new AssertionError("사진 URL이 없으면 다운로드하면 안 됩니다.");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30), Set.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, clock);

        MealModels.MealDayResponse first = service.get(date);
        MealModels.MealDayResponse second = service.get(date);

        assertThat(first.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(second.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(fetches).hasValue(1);
    }
}
