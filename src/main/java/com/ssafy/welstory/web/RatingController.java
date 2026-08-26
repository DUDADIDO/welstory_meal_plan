package com.ssafy.welstory.web;

import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {
    private final RatingService ratings;
    private final MealCacheService meals;

    public RatingController(RatingService ratings, MealCacheService meals) {
        this.ratings = ratings;
        this.meals = meals;
    }

    @GetMapping
    public ResponseEntity<RatingService.RatingDayResponse> ratings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Size(max = 80) String clientId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ratings.ratings(date, clientId));
    }

    @PostMapping
    public ResponseEntity<RatingService.RatingSummary> rate(@Valid @RequestBody RatingRequest request) {
        if (!meals.mealExists(request.date(), request.mealId())) {
            throw new ResponseStatusException(BAD_REQUEST, "존재하지 않는 식단입니다.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ratings.rate(request.date(), request.mealId(), request.clientId(), request.stars()));
    }

    public record RatingRequest(
            @NotNull LocalDate date,
            @NotNull @Pattern(regexp = "meal-[0-9]{2}") String mealId,
            @NotNull @Size(min = 8, max = 80) String clientId,
            @Min(1) @Max(5) int stars
    ) {}
}
