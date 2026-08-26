package com.ssafy.welstory.analytics;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "visitor_visits", uniqueConstraints = @UniqueConstraint(name = "uq_visitor_visit", columnNames = {"visit_date", "client_id"}))
public class VisitorVisitEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "visit_date", nullable = false) private LocalDate visitDate;
    @Column(name = "client_id", nullable = false, length = 128) private String clientId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected VisitorVisitEntity() {}
    public VisitorVisitEntity(LocalDate visitDate, String clientId, Instant createdAt) { this.visitDate = visitDate; this.clientId = clientId; this.createdAt = createdAt; }
    public LocalDate getVisitDate() { return visitDate; }
    public String getClientId() { return clientId; }
}
