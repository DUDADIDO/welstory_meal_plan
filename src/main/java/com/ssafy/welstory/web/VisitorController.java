package com.ssafy.welstory.web;

import com.ssafy.welstory.analytics.VisitorStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/visitors")
public class VisitorController {

    private final VisitorStatsService visitors;

    public VisitorController(
            VisitorStatsService visitors
    ) {
        this.visitors = visitors;
    }

    @PostMapping
    public ResponseEntity<Void> record(
            @RequestBody VisitorRequest request
    ) {
        visitors.record(request.clientId());
        return ResponseEntity.noContent().build();
    }

    public record VisitorRequest(
            String clientId
    ) {}
}