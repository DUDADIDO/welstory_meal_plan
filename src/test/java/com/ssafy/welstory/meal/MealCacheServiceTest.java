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
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30), Set.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        MealCacheService service = new MealCacheService(gateway, properties, mapper, new ImagePlaceholderDetector(), clock);

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
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30), Set.of());
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
