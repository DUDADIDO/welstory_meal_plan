package com.ssafy.welstory.meal.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface MealDayRepository extends JpaRepository<MealDayEntity, LocalDate> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO meal_days
                (meal_date, restaurant_name, complete, meals_json, message, last_updated_at)
            VALUES
                (:mealDate, :restaurantName, :complete, :mealsJson, :message, :lastUpdatedAt)
            ON CONFLICT (meal_date) DO UPDATE SET
                restaurant_name = EXCLUDED.restaurant_name,
                complete = EXCLUDED.complete,
                meals_json = EXCLUDED.meals_json,
                message = EXCLUDED.message,
                last_updated_at = EXCLUDED.last_updated_at
            """, nativeQuery = true)
    int upsert(@Param("mealDate") LocalDate mealDate,
               @Param("restaurantName") String restaurantName,
               @Param("complete") boolean complete,
               @Param("mealsJson") String mealsJson,
               @Param("message") String message,
               @Param("lastUpdatedAt") java.time.Instant lastUpdatedAt);
}
