package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.welstory.config.WelstoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

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
                return new MealModels.DownloadedImage(imageBytes(false), "image/png");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse first = service.get(date);
        MealModels.MealDayResponse second = service.get(date);

        assertThat(first.status()).isEqualTo(MealModels.Status.READY);
        assertThat(second.status()).isEqualTo(MealModels.Status.READY);
        assertThat(fetches).hasValue(1);
        assertThat(second.meals().getFirst().imageUrl()).startsWith("/api/meals/2026-08-26/images/meal-01?v=");
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
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse first = service.get(date);
        MealModels.MealDayResponse second = service.get(date);

        assertThat(first.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(second.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(fetches).hasValue(1);
    }

    @Test
    void duplicatePreparingImagesDoNotCompleteCache() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        byte[] preparing = imageBytes(true);
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                return List.of(
                        new MealModels.UpstreamMeal("한식", "메뉴1", null, "https://image.test/one.jpg"),
                        new MealModels.UpstreamMeal("일품", "메뉴2", null, "https://image.test/two.jpg"));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                return new MealModels.DownloadedImage(preparing, "image/png");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse response = service.get(date);
        MealModels.CacheEntry entry = service.inspectCaches().getFirst();

        assertThat(response.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(entry.complete()).isFalse();
        assertThat(entry.placeholderImageCount()).isEqualTo(2);
        assertThat(entry.readyImageCount()).isZero();
    }

    @Test
    void readyImageIsExposedWhileOtherMealsAreStillPreparing() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        byte[] actual = imageBytes(false);
        byte[] preparing = imageBytes(true);
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                return List.of(
                        new MealModels.UpstreamMeal("한식", "완료 메뉴", null, "https://image.test/actual.jpg"),
                        new MealModels.UpstreamMeal("일품", "준비 메뉴 1", null, "https://image.test/preparing-1.jpg"),
                        new MealModels.UpstreamMeal("일품", "준비 메뉴 2", null, "https://image.test/preparing-2.jpg"));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                return new MealModels.DownloadedImage(url.contains("actual") ? actual : preparing, "image/png");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse response = service.get(date);
        MealModels.CacheEntry entry = service.inspectCaches().getFirst();

        assertThat(response.status()).isEqualTo(MealModels.Status.WAITING);
        assertThat(response.meals()).extracting(MealModels.MealItem::imageUrl)
                .satisfiesExactly(
                        imageUrl -> assertThat(imageUrl).startsWith("/api/meals/2026-08-26/images/meal-01?v="),
                        imageUrl -> assertThat(imageUrl).isNull(),
                        imageUrl -> assertThat(imageUrl).isNull());
        assertThat(service.image(date, "meal-01")).isPresent();
        assertThat(service.image(date, "meal-02")).isEmpty();
        assertThat(entry.readyImageCount()).isEqualTo(1);
        assertThat(entry.placeholderImageCount()).isEqualTo(2);
        assertThat(entry.complete()).isFalse();
    }

    @Test
    void publicRequestForUncachedPastDateDoesNotCallUpstream() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        LocalDate pastDate = today.minusDays(1);
        AtomicInteger fetches = new AtomicInteger();
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                fetches.incrementAndGet();
                return List.of();
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                throw new AssertionError("과거 미캐시 날짜는 공개 요청으로 다운로드하면 안 됩니다.");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse response = service.get(pastDate);

        assertThat(response.status()).isEqualTo(MealModels.Status.UNAVAILABLE);
        assertThat(fetches).hasValue(0);
    }

    @Test
    void futureDateWithAdminCachedImagesIsReportedReady() {
        LocalDate futureDate = LocalDate.of(2026, 8, 28);
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                return List.of(new MealModels.UpstreamMeal(
                        "한식",
                        "미리 준비된 메뉴",
                        null,
                        "https://image.test/future-menu.jpg"
                ));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                return new MealModels.DownloadedImage(imageBytes(false), "image/png");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealCacheService.RefreshResult refresh = service.refreshWithImages(futureDate);
        MealModels.MealDayResponse response = service.get(futureDate);

        assertThat(refresh.state()).isEqualTo(MealCacheService.RefreshState.COMPLETE);
        assertThat(response.status()).isEqualTo(MealModels.Status.READY);
        assertThat(response.meals()).hasSize(1);
        assertThat(response.meals().getFirst().imageUrl()).startsWith("/api/meals/2026-08-28/images/meal-01?v=");
    }

   @Test
    void menuRefreshBeforePhotoWindowDoesNotDownloadImages() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        AtomicInteger downloads = new AtomicInteger();

        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                return List.of(
                        new MealModels.UpstreamMeal(
                                "한식",
                                "메뉴",
                                null,
                                "https://image.test/menu.jpg"
                        )
                );
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                downloads.incrementAndGet();
                return new MealModels.DownloadedImage(
                        imageBytes(false),
                        "image/png"
                );
            }
        };

        WelstoryProperties properties = new WelstoryProperties(
                null,
                "user",
                "password",
                null,
                null,
                null,
                tempDir,
                Duration.ofMinutes(5),
                Duration.ofMinutes(30)
        );

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        // 2026-08-26 07:00 KST
        // 이미지 수집 시간(09:00~18:00) 이전
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-25T22:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        MealCacheService service = new MealCacheService(
                gateway,
                properties,
                mapper,
                new ImagePlaceholderDetector(),
                clock
        );

        MealCacheService.RefreshResult menuResult =
                service.refreshMenu(date);

        assertThat(menuResult.state())
                .isEqualTo(MealCacheService.RefreshState.PARTIAL);

        assertThat(downloads)
                .hasValue(0);

        MealCacheService.RefreshResult refreshResult =
                service.refresh(date);

        // 07:00이므로 일반 refresh를 호출해도 이미지를 받지 않아야 함
        assertThat(downloads)
                .hasValue(0);

        // 이미지를 받지 않았으므로 아직 캐시 완료 상태가 아님
        assertThat(refreshResult.state())
                .isEqualTo(MealCacheService.RefreshState.PARTIAL);
    }

    @Test
    void emptyHistoricalResponseIsCachedAsUnavailable() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        LocalDate pastDate = today.minusDays(1);
        AtomicInteger fetches = new AtomicInteger();
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                fetches.incrementAndGet();
                return List.of();
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                throw new AssertionError("빈 식단은 이미지를 다운로드하면 안 됩니다.");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealCacheService.RefreshResult first = service.refresh(pastDate);
        MealModels.MealDayResponse response = service.get(pastDate);
        MealCacheService.RefreshResult second = service.refresh(pastDate);

        assertThat(first.state()).isEqualTo(MealCacheService.RefreshState.COMPLETE);
        assertThat(response.status()).isEqualTo(MealModels.Status.UNAVAILABLE);
        assertThat(response.message()).isEqualTo("해당 날짜에는 등록된 식단이 없습니다.");
        assertThat(second.state()).isEqualTo(MealCacheService.RefreshState.ALREADY_COMPLETE);
        assertThat(fetches).hasValue(1);
    }

    @Test
    void weekendTodayIsFetchedInsteadOfBeingPreemptivelySkipped() {
        LocalDate sunday = LocalDate.of(2026, 8, 30);
        AtomicInteger fetches = new AtomicInteger();
        WelstoryGateway gateway = new WelstoryGateway() {
            @Override
            public List<MealModels.UpstreamMeal> fetchLunch(LocalDate requestedDate) {
                fetches.incrementAndGet();
                return List.of(new MealModels.UpstreamMeal("주말식", "주말 메뉴", null, "https://image.test/menu.jpg"));
            }

            @Override
            public MealModels.DownloadedImage downloadImage(String url) {
                return new MealModels.DownloadedImage(imageBytes(false), "image/png");
            }
        };
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

        MealModels.MealDayResponse response = service.get(sunday);

        assertThat(response.status()).isEqualTo(MealModels.Status.READY);
        assertThat(response.meals()).hasSize(1);
        assertThat(fetches).hasValue(1);
    }

    private static byte[] imageBytes(boolean placeholder) {
        try {
            BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (placeholder) {
                        image.setRGB(x, y, Color.WHITE.getRGB());
                    } else {
                        image.setRGB(x, y, new Color((x * 3) % 255, (y * 7) % 255, (x + y * 2) % 255).getRGB());
                    }
                }
            }
            if (placeholder) {
                var graphics = image.createGraphics();
                graphics.setColor(new Color(210, 85, 35));
                graphics.fillRect(42, 44, 76, 12);
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
