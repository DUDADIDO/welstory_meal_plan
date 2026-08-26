package com.ssafy.welstory.meal;

import java.time.Instant;
import java.util.List;

/** Converts the cache domain model into the public API DTO without mixing HTTP concerns into cache persistence. */
public final class MealResponseMapper {
    private MealResponseMapper() {}

    public static MealModels.MealDayResponse toResponse(MealModels.CachedMealDay cached,
                                                         MealModels.Status status,
                                                         Instant nextCheckAt) {
        List<MealModels.MealItem> meals = cached.meals().stream()
                .map(meal -> new MealModels.MealItem(
                        meal.id(), meal.courseName(), meal.name(), meal.description(),
                        meal.hasCachedImage()
                                ? "/api/meals/%s/images/%s?v=%s".formatted(cached.date(), meal.id(), meal.imageHash())
                                : null,
                        meal.calorie()))
                .toList();
        return new MealModels.MealDayResponse(cached.date(), cached.restaurantName(), status, meals,
                cached.message(), cached.lastUpdatedAt(), nextCheckAt);
    }
}
