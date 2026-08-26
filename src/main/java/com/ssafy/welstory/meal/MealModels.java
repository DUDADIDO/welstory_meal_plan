package com.ssafy.welstory.meal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MealModels {
    private MealModels() {}

    public enum Status {
        READY, WAITING, UNAVAILABLE, ERROR
    }

    public record MealItem(
            String id,
            String courseName,
            String name,
            String description,
            String imageUrl,
            String calorie
    ) {
        public MealItem(String id, String courseName, String name, String description, String imageUrl) {
            this(id, courseName, name, description, imageUrl, null);
        }
    }

    public record MealDayResponse(
            LocalDate date,
            String restaurantName,
            Status status,
            List<MealItem> meals,
            String message,
            Instant lastUpdatedAt,
            Instant nextCheckAt
    ) {}

    public record UpstreamMeal(
            String courseName,
            String name,
            String description,
            String photoUrl,
            String calorie
    ) {
        public UpstreamMeal(String courseName, String name, String description, String photoUrl) {
            this(courseName, name, description, photoUrl, null);
        }
    }

    public record CachedMeal(
            String id,
            String courseName,
            String name,
            String description,
            String originalImageUrl,
            String imageFile,
            String imageContentType,
            String imageHash,
            boolean placeholder,
            String calorie
    ) {
        public CachedMeal(String id, String courseName, String name, String description, String originalImageUrl,
                          String imageFile, String imageContentType, String imageHash, boolean placeholder) {
            this(id, courseName, name, description, originalImageUrl, imageFile, imageContentType, imageHash, placeholder, null);
        }
        @JsonIgnore
        public boolean hasCachedImage() {
            return hasImageFile() && !placeholder;
        }

        @JsonIgnore
        public boolean hasImageFile() {
            return imageFile != null && !imageFile.isBlank();
        }
    }

    public record CachedMealDay(
            LocalDate date,
            String restaurantName,
            boolean complete,
            List<CachedMeal> meals,
            String message,
            Instant lastUpdatedAt
    ) {}

    public record DownloadedImage(byte[] bytes, String contentType) {}

    public record CacheEntry(
            LocalDate date,
            boolean complete,
            int menuCount,
            int readyImageCount,
            int placeholderImageCount,
            int missingImageCount,
            long diskBytes,
            Instant lastUpdatedAt,
            Instant lastAttemptAt,
            String lastError,
            String message
    ) {}
}
