package com.ssafy.welstory.meal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RatingVoteRepository extends JpaRepository<RatingVoteEntity, Long> {
    List<RatingVoteEntity> findByMealDate(LocalDate mealDate);
    Optional<RatingVoteEntity> findByMealDateAndMealIdAndClientId(LocalDate date, String mealId, String clientId);
}
