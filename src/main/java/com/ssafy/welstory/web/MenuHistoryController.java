package com.ssafy.welstory.web;

import com.ssafy.welstory.meal.MenuHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/menu-history")
public class MenuHistoryController {
    private final MenuHistoryService history;

    public MenuHistoryController(MenuHistoryService history) { this.history = history; }

    @GetMapping("/references")
    public ResponseEntity<Map<String, MenuHistoryService.Entry>> references(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.references(date));
    }
}
