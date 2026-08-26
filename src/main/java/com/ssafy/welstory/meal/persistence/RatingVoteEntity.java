package com.ssafy.welstory.meal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rating_votes", uniqueConstraints = @UniqueConstraint(name = "uq_rating_vote", columnNames = {"meal_date", "meal_id", "client_id"}))
public class RatingVoteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "meal_date", nullable = false) private LocalDate mealDate;
    @Column(name = "meal_id", nullable = false, length = 64) private String mealId;
    @Column(name = "client_id", nullable = false, length = 128) private String clientId;
    @Column(nullable = false) private int stars;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RatingVoteEntity() {}

    public RatingVoteEntity(LocalDate mealDate, String mealId, String clientId, int stars, Instant updatedAt) {
        this.mealDate = mealDate; this.mealId = mealId; this.clientId = clientId; this.stars = stars; this.updatedAt = updatedAt;
    }

    public LocalDate getMealDate() { return mealDate; }
    public String getMealId() { return mealId; }
    public String getClientId() { return clientId; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
