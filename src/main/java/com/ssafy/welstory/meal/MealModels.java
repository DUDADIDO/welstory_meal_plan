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
            String imageUrl
    ) {}

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
            String photoUrl
    ) {}

    public record CachedMeal(
            String id,
            String courseName,
            String name,
            String description,
            String originalImageUrl,
            String imageFile,
            String imageContentType
    ) {
        @JsonIgnore
        public boolean hasCachedImage() {
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
}
