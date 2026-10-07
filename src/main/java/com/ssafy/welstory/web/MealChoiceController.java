package com.ssafy.welstory.web;

import com.ssafy.welstory.meal.MealCacheService;
import com.ssafy.welstory.meal.MealChoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/chat/meal-votes")
public class MealChoiceController {
    private final MealChoiceService choices;
    private final MealCacheService meals;
    public MealChoiceController(MealChoiceService choices, MealCacheService meals) { this.choices = choices; this.meals = meals; }

    @GetMapping
    public ResponseEntity<MealChoiceService.Summary> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CookieValue(name = "welstory-chat-user", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(choices.summary(date, token));
    }

    @PostMapping
    public ResponseEntity<MealChoiceService.Summary> choose(
            @Valid @RequestBody ChoiceRequest body,
            @CookieValue(name = "welstory-chat-user", required = false) String token,
            @RequestHeader(name = "X-Chat-Request", defaultValue = "") String header) {
        if (!"1".equals(header)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 투표 요청입니다.");
        if (body.mealId() != null && !meals.mealExists(body.date(), body.mealId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 식단입니다.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(choices.choose(body.date(), token, body.mealId()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> error(ResponseStatusException exception) {
        var detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        var response = ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore());
        if (exception instanceof MealChoiceService.RateLimited limited) {
            detail.setProperty("summary", limited.summary);
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds));
        }
        return response.body(detail);
    }

    public record ChoiceRequest(@NotNull LocalDate date, @Pattern(regexp = "meal-[0-9]{2}") String mealId) {}
}
