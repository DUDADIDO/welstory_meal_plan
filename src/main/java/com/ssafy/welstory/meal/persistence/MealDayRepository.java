package com.ssafy.welstory.meal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MealDayRepository extends JpaRepository<MealDayEntity, LocalDate> {}
