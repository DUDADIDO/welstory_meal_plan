package com.ssafy.welstory.meal.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.meal.MealModels;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MealCacheStore {
    private final MealDayRepository repository;
    private final ObjectMapper objectMapper;

    public MealCacheStore(MealDayRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<MealModels.CachedMealDay> find(LocalDate date) {
        return repository.findById(date).flatMap(this::toModel);
    }

    public Set<LocalDate> dates() {
        return repository.findAll().stream()
                .map(MealDayEntity::getDate)
                .collect(Collectors.toSet());
    }

    public void save(MealModels.CachedMealDay day) {
        try {
            String mealsJson = objectMapper.writeValueAsString(day.meals());
            repository.save(new MealDayEntity(day.date(), day.restaurantName(), day.complete(), mealsJson,
                    day.message(), day.lastUpdatedAt()));
        } catch (IOException error) {
            throw new IllegalStateException("식단 캐시를 DB에 저장하지 못했습니다.", error);
        }
    }

    private Optional<MealModels.CachedMealDay> toModel(MealDayEntity entity) {
        try {
            MealModels.CachedMeal[] meals = objectMapper.readValue(entity.getMealsJson(), MealModels.CachedMeal[].class);
            return Optional.of(new MealModels.CachedMealDay(entity.getDate(), entity.getRestaurantName(),
                    entity.isComplete(), java.util.List.of(meals), entity.getMessage(), entity.getLastUpdatedAt()));
        } catch (IOException error) {
            return Optional.empty();
        }
    }
}
