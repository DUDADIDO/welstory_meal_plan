package com.ssafy.welstory.meal.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.meal.MealModels;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Filesystem adapter for legacy cache migration and downloaded image assets.
 * Persistent meal metadata is stored by {@link MealCacheStore}; this class
 * intentionally owns only the cache volume/file concerns.
 */
public final class MealCacheFileStore {
    private final Path root;
    private final ObjectMapper objectMapper;

    public MealCacheFileStore(Path root, ObjectMapper objectMapper) {
        this.root = root.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public Path dateDir(LocalDate date) {
        return root.resolve(date.toString());
    }

    public Optional<MealModels.CachedMealDay> read(LocalDate date) throws IOException {
        Path metadata = dateDir(date).resolve("cache.json");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(metadata.toFile(), MealModels.CachedMealDay.class));
    }

    public void write(MealModels.CachedMealDay day) throws IOException {
        Path directory = dateDir(day.date());
        Files.createDirectories(directory);
        Path target = directory.resolve("cache.json");
        Path temporary = directory.resolve("cache.json.tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), day);
        moveAtomically(temporary, target);
    }

    public void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        moveAtomically(temporary, target);
    }

    public long directorySize(LocalDate date) {
        Path directory = dateDir(date);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException ignored) {
                    return 0;
                }
            }).sum();
        } catch (IOException ignored) {
            return 0;
        }
    }

    public static String extension(String contentType) {
        if (contentType == null) return ".jpg";
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("gif")) return ".gif";
        return ".jpg";
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
