package com.ssafy.welstory.analytics;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class VisitorStatsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final VisitorVisitRepository repository;

    public VisitorStatsService(VisitorVisitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        LocalDate today = LocalDate.now(SEOUL);
        if (!repository.existsByVisitDateAndClientId(today, clientId)) {
            try {
                repository.save(new VisitorVisitEntity(today, clientId, java.time.Instant.now()));
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent request may have inserted the same daily visitor first.
            }
        }
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate monthStart = YearMonth.from(today).atDay(1);
        var visits = repository.findByVisitDateBetween(monthStart, today);
        int dailyVisitors = (int) visits.stream().filter(v -> v.getVisitDate().equals(today)).count();
        Set<String> monthlyUnique = new HashSet<>();
        Map<String, Integer> daily = new HashMap<>();
        visits.forEach(visit -> {
            monthlyUnique.add(visit.getClientId());
            daily.merge(visit.getVisitDate().toString(), 1, Integer::sum);
        });
        return new Stats(dailyVisitors, monthlyUnique.size(), Map.copyOf(daily));
    }

    public record Stats(int dailyVisitors, int monthlyVisitors, Map<String, Integer> daily) {}
}
