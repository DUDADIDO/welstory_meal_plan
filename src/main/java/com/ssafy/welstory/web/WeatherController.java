package com.ssafy.welstory.web;

import com.ssafy.welstory.weather.WeatherService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherController {
    private final WeatherService weather;
    public WeatherController(WeatherService weather) { this.weather = weather; }

    @GetMapping("/api/time")
    public ResponseEntity<Map<String, Instant>> time() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("serverTime", Instant.now()));
    }

    @GetMapping("/api/weather")
    public ResponseEntity<WeatherService.Snapshot> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(weather.current());
    }
}
