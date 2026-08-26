package com.ssafy.welstory.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VisitorVisitRepository extends JpaRepository<VisitorVisitEntity, Long> {
    boolean existsByVisitDateAndClientId(LocalDate date, String clientId);
    List<VisitorVisitEntity> findByVisitDateBetween(LocalDate start, LocalDate end);
}
