package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.config.WelstoryProperties;
import com.ssafy.welstory.meal.persistence.RatingVoteEntity;
import com.ssafy.welstory.meal.persistence.RatingVoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RatingService {
    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final RatingVoteRepository repository;
    private final ReentrantLock lock = new ReentrantLock();
    private RatingStore store;

    @Autowired
    public RatingService(ObjectMapper objectMapper, WelstoryProperties properties, RatingVoteRepository repository) {
        this.objectMapper = objectMapper;
        this.storePath = null;
        this.repository = repository;
        this.store = new RatingStore(Map.of());
    }

    RatingService(ObjectMapper objectMapper, WelstoryProperties properties) {
        this.objectMapper = objectMapper;
        this.storePath = properties.cacheDir().toAbsolutePath().normalize().resolve("ratings.json");
        this.repository = null;
        this.store = readStore();
    }

    public RatingDayResponse ratings(LocalDate date, String clientId) {
        lock.lock();
        try {
            if (repository != null) return dbRatings(date, clientId);
            Map<String, RatingSummary> summaries = new HashMap<>();
            String prefix = date + ":";
            store.meals().forEach((key, bucket) -> {
                if (key.startsWith(prefix)) {
                    summaries.put(key.substring(prefix.length()), summary(bucket, clientId));
                }
            });
            return new RatingDayResponse(date, Map.copyOf(summaries));
        } finally {
            lock.unlock();
        }
    }

    public RatingSummary rate(LocalDate date, String mealId, String clientId, int stars) {
        lock.lock();
        try {
            if (repository != null) return dbRate(date, mealId, clientId, stars);
            String key = date + ":" + mealId;
            Map<String, RatingBucket> meals = new HashMap<>(store.meals());
            RatingBucket previous = meals.getOrDefault(key, new RatingBucket(Map.of(), null));
            Map<String, Integer> votes = new HashMap<>(previous.votes());
            votes.put(clientId, stars);
            RatingBucket updated = new RatingBucket(Map.copyOf(votes), Instant.now());
            meals.put(key, updated);
            store = new RatingStore(Map.copyOf(meals));
            persist();
            return summary(updated, clientId);
        } finally {
            lock.unlock();
        }
    }

    public RatingStats stats() {
        lock.lock();
        try {
            if (repository != null) {
                var votes = repository.findAll();
                return new RatingStats((int) votes.stream().map(v -> v.getMealDate() + ":" + v.getMealId()).distinct().count(),
                        votes.size(), 0);
            }
            int mealCount = store.meals().size();
            int voteCount = store.meals().values().stream().mapToInt(bucket -> bucket.votes().size()).sum();
            long diskBytes = 0;
            try { if (Files.isRegularFile(storePath)) diskBytes = Files.size(storePath); } catch (IOException ignored) { }
            return new RatingStats(mealCount, voteCount, diskBytes);
        } finally {
            lock.unlock();
        }
    }

    private static RatingSummary summary(RatingBucket bucket, String clientId) {
        int count = bucket.votes().size();
        double average = count == 0 ? 0 : bucket.votes().values().stream().mapToInt(Integer::intValue).average().orElse(0);
        return new RatingSummary(Math.round(average * 10.0) / 10.0, count,
                clientId == null ? null : bucket.votes().get(clientId), bucket.updatedAt());
    }

    private RatingDayResponse dbRatings(LocalDate date, String clientId) {
        Map<String, List<RatingVoteEntity>> grouped = new HashMap<>();
        repository.findByMealDate(date).forEach(vote -> grouped.computeIfAbsent(vote.getMealId(), ignored -> new java.util.ArrayList<>()).add(vote));
        Map<String, RatingSummary> summaries = new HashMap<>();
        grouped.forEach((mealId, votes) -> summaries.put(mealId, dbSummary(votes, clientId)));
        return new RatingDayResponse(date, Map.copyOf(summaries));
    }

    private RatingSummary dbRate(LocalDate date, String mealId, String clientId, int stars) {
        RatingVoteEntity vote = repository.findByMealDateAndMealIdAndClientId(date, mealId, clientId)
                .orElseGet(() -> new RatingVoteEntity(date, mealId, clientId, stars, Instant.now()));
        vote.setStars(stars);
        vote.setUpdatedAt(Instant.now());
        repository.save(vote);
        return dbSummary(repository.findByMealDate(date).stream().filter(item -> item.getMealId().equals(mealId)).toList(), clientId);
    }

    private static RatingSummary dbSummary(java.util.List<RatingVoteEntity> votes, String clientId) {
        int count = votes.size();
        double average = count == 0 ? 0 : votes.stream().mapToInt(RatingVoteEntity::getStars).average().orElse(0);
        Instant updatedAt = votes.stream().map(RatingVoteEntity::getUpdatedAt).max(Instant::compareTo).orElse(null);
        Integer mine = votes.stream().filter(vote -> vote.getClientId().equals(clientId)).map(RatingVoteEntity::getStars).findFirst().orElse(null);
        return new RatingSummary(Math.round(average * 10.0) / 10.0, count, mine, updatedAt);
    }

    private RatingStore readStore() {
        if (!Files.isRegularFile(storePath)) return new RatingStore(Map.of());
        try {
            RatingStore loaded = objectMapper.readValue(storePath.toFile(), RatingStore.class);
            return loaded.meals() == null ? new RatingStore(Map.of()) : loaded;
        } catch (IOException ignored) {
            return new RatingStore(Map.of());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            Path temporary = storePath.resolveSibling("ratings.json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), store);
            try {
                Files.move(temporary, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("별점을 저장하지 못했습니다.", error);
        }
    }

    public record RatingSummary(double average, int count, Integer myRating, Instant updatedAt) {}
    public record RatingDayResponse(LocalDate date, Map<String, RatingSummary> ratings) {}
    public record RatingBucket(Map<String, Integer> votes, Instant updatedAt) {}
    public record RatingStore(Map<String, RatingBucket> meals) {}
    public record RatingStats(int ratedMealCount, int voteCount, long diskBytes) {}
}
