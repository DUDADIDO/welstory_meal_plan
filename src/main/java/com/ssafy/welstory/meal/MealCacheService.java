package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.config.WelstoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MealCacheService {
    private static final Logger log = LoggerFactory.getLogger(MealCacheService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime POLL_START = LocalTime.of(6, 0);
    private static final LocalTime POLL_END = LocalTime.of(10, 40);

    private final WelstoryGateway gateway;
    private final WelstoryProperties properties;
    private final ObjectMapper objectMapper;
    private final ImagePlaceholderDetector placeholderDetector;
    private final Clock clock;
    private final Map<LocalDate, MealModels.CachedMealDay> memory = new ConcurrentHashMap<>();
    private final Map<LocalDate, Instant> lastAttempts = new ConcurrentHashMap<>();
    private final Map<LocalDate, String> lastErrors = new ConcurrentHashMap<>();
    private final Map<LocalDate, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public MealCacheService(WelstoryGateway gateway, WelstoryProperties properties, ObjectMapper objectMapper,
                            ImagePlaceholderDetector placeholderDetector) {
        this(gateway, properties, objectMapper, placeholderDetector, Clock.system(SEOUL));
    }

    MealCacheService(WelstoryGateway gateway, WelstoryProperties properties, ObjectMapper objectMapper,
                     ImagePlaceholderDetector placeholderDetector, Clock clock) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.placeholderDetector = placeholderDetector;
        this.clock = clock;
    }

    public MealModels.MealDayResponse get(LocalDate date) {
        MealModels.CachedMealDay cached = load(date).orElse(null);
        if (cached != null && cached.complete()) {
            return response(cached, MealModels.Status.READY, null);
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        if (isClosedDate(date, now.toLocalDate())) {
            if (cached != null && !cached.meals().isEmpty()) {
                return response(cached, MealModels.Status.WAITING, null);
            }
            return empty(date, MealModels.Status.UNAVAILABLE, "이 날짜에는 등록된 식단이 없습니다.", null);
        }
        if (shouldAttempt(date, now.toInstant(), now.toLocalTime())) {
            refresh(date);
            cached = load(date).orElse(cached);
        }
        if (cached != null && cached.complete()) {
            return response(cached, MealModels.Status.READY, null);
        }
        if (cached == null) {
            Instant next = nextAttempt(date, now.toInstant(), now.toLocalTime());
            String error = lastErrors.get(date);
            if (error != null) {
                return empty(date, MealModels.Status.ERROR, error, next);
            }
            return empty(date, MealModels.Status.WAITING, "식단 사진이 올라오기를 기다리고 있어요.", next);
        }
        return response(cached, MealModels.Status.WAITING,
                nextAttempt(date, now.toInstant(), now.toLocalTime()));
    }

    public void refresh(LocalDate date) {
        ReentrantLock lock = locks.computeIfAbsent(date, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return;
        }
        try {
            MealModels.CachedMealDay existing = load(date).orElse(null);
            if (existing != null && existing.complete()) {
                return;
            }
            lastAttempts.put(date, Instant.now(clock));
            List<MealModels.UpstreamMeal> upstream = gateway.fetchLunch(date);
            MealModels.CachedMealDay updated = cacheMeals(date, upstream, existing);
            memory.put(date, updated);
            persist(updated);
            lastErrors.remove(date);
            log.info("Meal cache refreshed: date={}, meals={}, complete={}", date, updated.meals().size(), updated.complete());
        } catch (Exception error) {
            lastErrors.put(date, safeErrorMessage(error));
            log.warn("Meal refresh failed for {}: {}", date, error.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public Optional<ImageAsset> image(LocalDate date, String mealId) {
        return load(date)
                .filter(MealModels.CachedMealDay::complete)
                .flatMap(day -> day.meals().stream().filter(meal -> meal.id().equals(mealId)).findFirst())
                .filter(MealModels.CachedMeal::hasCachedImage)
                .map(meal -> new ImageAsset(dateDir(date).resolve(meal.imageFile()).normalize(), meal.imageContentType()))
                .filter(asset -> asset.path().startsWith(dateDir(date).normalize()) && Files.isRegularFile(asset.path()));
    }

    public boolean mealExists(LocalDate date, String mealId) {
        return load(date).map(day -> day.meals().stream().anyMatch(meal -> meal.id().equals(mealId))).orElse(false);
    }

    private MealModels.CachedMealDay cacheMeals(LocalDate date, List<MealModels.UpstreamMeal> upstream,
                                                MealModels.CachedMealDay existing) {
        List<MealModels.CachedMeal> cached = new ArrayList<>();
        for (int index = 0; index < upstream.size(); index++) {
            MealModels.UpstreamMeal meal = upstream.get(index);
            String id = "meal-%02d".formatted(index + 1);
            MealModels.CachedMeal previous = findExisting(existing, id);
            if (previous != null && !java.util.Objects.equals(previous.originalImageUrl(), meal.photoUrl())) {
                previous = null;
            }
            String imageFile = previous == null ? null : previous.imageFile();
            String contentType = previous == null ? null : previous.imageContentType();
            String imageHash = previous == null ? null : previous.imageHash();
            boolean placeholder = previous != null && previous.placeholder();

            if ((placeholder || imageFile == null || !Files.isRegularFile(dateDir(date).resolve(imageFile)))
                    && meal.photoUrl() != null && !meal.photoUrl().isBlank()) {
                try {
                    MealModels.DownloadedImage image = gateway.downloadImage(meal.photoUrl());
                    ImagePlaceholderDetector.Analysis analysis = placeholderDetector.analyze(image.bytes());
                    imageFile = id + extension(image.contentType());
                    writeAtomically(dateDir(date).resolve(imageFile), image.bytes());
                    contentType = image.contentType();
                    imageHash = analysis.hash();
                    placeholder = analysis.placeholder();
                } catch (Exception error) {
                    log.warn("Image cache failed for {}/{}: {}", date, id, error.getMessage());
                    imageFile = null;
                    contentType = null;
                    imageHash = null;
                    placeholder = false;
                }
            }
            cached.add(new MealModels.CachedMeal(id, meal.courseName(), meal.name(), meal.description(),
                    meal.photoUrl(), imageFile, contentType, imageHash, placeholder));
        }
        cached = markDuplicateImagesAsPlaceholders(cached);
        boolean complete = !cached.isEmpty() && cached.stream().allMatch(MealModels.CachedMeal::hasCachedImage);
        long placeholderCount = cached.stream().filter(MealModels.CachedMeal::placeholder).count();
        String message = complete
                ? "오늘의 식단이 준비되었습니다."
                : placeholderCount > 0
                    ? "메뉴 이미지 준비 중 화면을 감지해 실제 사진을 기다리고 있어요."
                    : "메뉴는 확인됐지만 사진을 기다리고 있어요.";
        return new MealModels.CachedMealDay(date, properties.restaurantName(), complete,
                List.copyOf(cached), message, Instant.now(clock));
    }

    private Optional<MealModels.CachedMealDay> load(LocalDate date) {
        MealModels.CachedMealDay present = memory.get(date);
        if (present != null) {
            return Optional.of(present);
        }
        Path metadata = dateDir(date).resolve("cache.json");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        try {
            MealModels.CachedMealDay loaded = objectMapper.readValue(metadata.toFile(), MealModels.CachedMealDay.class);
            loaded = upgradeLegacyCache(loaded);
            memory.put(date, loaded);
            return Optional.of(loaded);
        } catch (IOException error) {
            log.warn("Ignoring unreadable cache {}: {}", metadata, error.getMessage());
            return Optional.empty();
        }
    }

    private MealModels.CachedMealDay upgradeLegacyCache(MealModels.CachedMealDay day) {
        boolean needsAnalysis = day.meals().stream()
                .anyMatch(meal -> meal.hasImageFile() && (meal.imageHash() == null || meal.imageHash().isBlank()));
        if (!needsAnalysis) return day;

        List<MealModels.CachedMeal> analyzed = day.meals().stream().map(meal -> {
            if (!meal.hasImageFile() || (meal.imageHash() != null && !meal.imageHash().isBlank())) return meal;
            try {
                byte[] bytes = Files.readAllBytes(dateDir(day.date()).resolve(meal.imageFile()));
                ImagePlaceholderDetector.Analysis result = placeholderDetector.analyze(bytes);
                return new MealModels.CachedMeal(meal.id(), meal.courseName(), meal.name(), meal.description(),
                        meal.originalImageUrl(), meal.imageFile(), meal.imageContentType(), result.hash(), result.placeholder());
            } catch (IOException ignored) {
                return new MealModels.CachedMeal(meal.id(), meal.courseName(), meal.name(), meal.description(),
                        meal.originalImageUrl(), null, null, null, false);
            }
        }).toList();
        analyzed = markDuplicateImagesAsPlaceholders(analyzed);
        boolean complete = !analyzed.isEmpty() && analyzed.stream().allMatch(MealModels.CachedMeal::hasCachedImage);
        long placeholders = analyzed.stream().filter(MealModels.CachedMeal::placeholder).count();
        String message = complete ? "오늘의 식단이 준비되었습니다."
                : placeholders > 0 ? "메뉴 이미지 준비 중 화면을 감지해 실제 사진을 기다리고 있어요."
                : "메뉴는 확인됐지만 사진을 기다리고 있어요.";
        MealModels.CachedMealDay upgraded = new MealModels.CachedMealDay(day.date(), day.restaurantName(), complete,
                analyzed, message, day.lastUpdatedAt());
        try {
            persist(upgraded);
        } catch (IOException error) {
            log.warn("Could not persist upgraded cache {}: {}", day.date(), error.getMessage());
        }
        return upgraded;
    }

    private void persist(MealModels.CachedMealDay day) throws IOException {
        Files.createDirectories(dateDir(day.date()));
        Path target = dateDir(day.date()).resolve("cache.json");
        Path temporary = dateDir(day.date()).resolve("cache.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), day);
        moveAtomically(temporary, target);
    }

    private void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        moveAtomically(temporary, target);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private MealModels.MealDayResponse response(MealModels.CachedMealDay cached, MealModels.Status status,
                                                Instant nextCheckAt) {
        List<MealModels.MealItem> meals = cached.meals().stream()
                .map(meal -> new MealModels.MealItem(meal.id(), meal.courseName(), meal.name(), meal.description(),
                        cached.complete() && meal.hasCachedImage()
                                ? "/api/meals/%s/images/%s".formatted(cached.date(), meal.id()) : null))
                .toList();
        return new MealModels.MealDayResponse(cached.date(), cached.restaurantName(), status, meals,
                cached.message(), cached.lastUpdatedAt(), nextCheckAt);
    }

    private MealModels.MealDayResponse empty(LocalDate date, MealModels.Status status, String message, Instant next) {
        return new MealModels.MealDayResponse(date, properties.restaurantName(), status, List.of(),
                message, null, next);
    }

    private boolean shouldAttempt(LocalDate date, Instant now, LocalTime localTime) {
        if (!date.equals(LocalDate.now(clock)) || isNonWorkingDay(date)) {
            return false;
        }
        Instant last = lastAttempts.get(date);
        Duration interval = isPollingWindow(localTime) ? properties.retryInterval() : properties.offHoursRetryInterval();
        return last == null || now.isAfter(last.plus(interval));
    }

    private Instant nextAttempt(LocalDate date, Instant now, LocalTime localTime) {
        if (!date.equals(LocalDate.now(clock)) || isNonWorkingDay(date)) {
            return null;
        }
        Instant last = lastAttempts.get(date);
        Duration interval = isPollingWindow(localTime) ? properties.retryInterval() : properties.offHoursRetryInterval();
        return last == null ? now : last.plus(interval);
    }

    private boolean isClosedDate(LocalDate date, LocalDate today) {
        return date.isBefore(today) || date.isAfter(today) || isNonWorkingDay(date);
    }

    private boolean isNonWorkingDay(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || properties.holidays().contains(date);
    }

    private static boolean isPollingWindow(LocalTime time) {
        return !time.isBefore(POLL_START) && !time.isAfter(POLL_END);
    }

    private Path dateDir(LocalDate date) {
        return properties.cacheDir().toAbsolutePath().normalize().resolve(date.toString());
    }

    private static MealModels.CachedMeal findExisting(MealModels.CachedMealDay day, String id) {
        if (day == null) return null;
        return day.meals().stream().filter(meal -> meal.id().equals(id)).findFirst().orElse(null);
    }

    private static List<MealModels.CachedMeal> markDuplicateImagesAsPlaceholders(List<MealModels.CachedMeal> meals) {
        Map<String, Integer> hashCounts = new HashMap<>();
        meals.stream().map(MealModels.CachedMeal::imageHash).filter(hash -> hash != null && !hash.isBlank())
                .forEach(hash -> hashCounts.merge(hash, 1, Integer::sum));
        return meals.stream().map(meal -> {
            boolean duplicatePlaceholder = meal.imageHash() != null && hashCounts.getOrDefault(meal.imageHash(), 0) >= 2;
            if (!duplicatePlaceholder || meal.placeholder()) return meal;
            return new MealModels.CachedMeal(meal.id(), meal.courseName(), meal.name(), meal.description(),
                    meal.originalImageUrl(), meal.imageFile(), meal.imageContentType(), meal.imageHash(), true);
        }).toList();
    }

    public List<MealModels.CacheEntry> inspectCaches() {
        Set<LocalDate> dates = new HashSet<>(memory.keySet());
        Path root = properties.cacheDir().toAbsolutePath().normalize();
        if (Files.isDirectory(root)) {
            try (var directories = Files.list(root)) {
                directories.filter(Files::isDirectory).map(path -> path.getFileName().toString()).forEach(name -> {
                    try { dates.add(LocalDate.parse(name)); } catch (Exception ignored) { }
                });
            } catch (IOException error) {
                log.warn("Could not inspect cache directory: {}", error.getMessage());
            }
        }
        dates.add(LocalDate.now(clock));
        return dates.stream().sorted(Comparator.reverseOrder()).map(this::cacheEntry).toList();
    }

    private MealModels.CacheEntry cacheEntry(LocalDate date) {
        MealModels.CachedMealDay day = load(date).orElse(null);
        List<MealModels.CachedMeal> meals = day == null ? List.of() : day.meals();
        int ready = (int) meals.stream().filter(MealModels.CachedMeal::hasCachedImage).count();
        int placeholders = (int) meals.stream().filter(MealModels.CachedMeal::placeholder).count();
        int missing = meals.size() - ready - placeholders;
        return new MealModels.CacheEntry(date, day != null && day.complete(), meals.size(), ready, placeholders,
                Math.max(0, missing), directorySize(dateDir(date)), day == null ? null : day.lastUpdatedAt(),
                lastAttempts.get(date), lastErrors.get(date), day == null ? "캐시 없음" : day.message());
    }

    private static long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) return 0;
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (IOException ignored) { return 0; }
            }).sum();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String extension(String contentType) {
        if (contentType == null) return ".jpg";
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("gif")) return ".gif";
        return ".jpg";
    }

    private static String safeErrorMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && message.contains("WELSTORY_USERNAME")) {
            return "서버에 웰스토리 계정 설정이 필요합니다.";
        }
        return "웰스토리 식단을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }

    public record ImageAsset(Path path, String contentType) {}
}
