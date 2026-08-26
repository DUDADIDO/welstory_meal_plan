package com.ssafy.welstory.meal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_days")
public class MealDayEntity {
    @Id
    @Column(name = "meal_date", nullable = false)
    private LocalDate date;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column(nullable = false)
    private boolean complete;

    @Column(name = "meals_json", nullable = false, columnDefinition = "TEXT")
    private String mealsJson;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    protected MealDayEntity() {}

    public MealDayEntity(LocalDate date, String restaurantName, boolean complete, String mealsJson,
                         String message, Instant lastUpdatedAt) {
        this.date = date;
        this.restaurantName = restaurantName;
        this.complete = complete;
        this.mealsJson = mealsJson;
        this.message = message;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public LocalDate getDate() { return date; }
    public String getRestaurantName() { return restaurantName; }
    public boolean isComplete() { return complete; }
    public String getMealsJson() { return mealsJson; }
    public String getMessage() { return message; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
}
