package com.ssafy.welstory.web;

import com.ssafy.welstory.chat.ChatService;
import com.ssafy.welstory.meal.MealCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final String COOKIE = "welstory-chat-user";
    private final ChatService chat;
    private final MealCacheService meals;

    public ChatController(ChatService chat, MealCacheService meals) {
        this.chat = chat;
        this.meals = meals;
    }

    @GetMapping("/identity")
    public ResponseEntity<ChatService.Quota> identity(
            @CookieValue(name = COOKIE, required = false) String token, HttpServletRequest request) {
        ChatService.Identity identity = chat.identity(token);
        ResponseCookie cookie = ResponseCookie.from(COOKIE, identity.token()).httpOnly(true)
                .secure(request.isSecure()).sameSite("Strict").path("/api/chat")
                .maxAge(Duration.ofDays(365)).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString()).body(identity.user());
    }

    @GetMapping("/messages")
    public ResponseEntity<ChatService.Room> room(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @Pattern(regexp = "meal-[0-9]{2}") String mealId,
            @RequestParam(required = false) Long before,
            @CookieValue(name = COOKIE, required = false) String token) {
        requireMeal(date, mealId);
        if (before != null && before < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 메시지 번호입니다.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.room(date, mealId, token, before));
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatService.Sent> send(
            @Valid @RequestBody SendRequest body,
            @CookieValue(name = COOKIE, required = false) String token,
            @RequestHeader(name = "X-Chat-Request", defaultValue = "") String chatRequest) {
        // Custom header requires a same-origin request or an explicitly allowed CORS preflight.
        if (!"1".equals(chatRequest)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 채팅 요청입니다.");
        requireMeal(body.date(), body.mealId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(chat.send(body.date(), body.mealId(), token, body.content()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> error(ResponseStatusException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore());
        if (exception instanceof ChatService.RateLimited limited) {
            detail.setProperty("user", limited.user);
            detail.setProperty("retryAfterSeconds", limited.retryAfterSeconds);
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds));
        }
        return response.body(detail);
    }

    private void requireMeal(LocalDate date, String mealId) {
        if (!meals.mealExists(date, mealId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 식단입니다.");
    }

    public record SendRequest(@NotNull LocalDate date,
                              @NotNull @Pattern(regexp = "meal-[0-9]{2}") String mealId,
                              @NotBlank @Size(max = 500) String content) {}
}
