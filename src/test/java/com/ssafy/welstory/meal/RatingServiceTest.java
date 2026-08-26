package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.welstory.config.WelstoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RatingServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void oneClientCanUpdateVoteAndRatingsPersist() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        WelstoryProperties properties = new WelstoryProperties(null, "user", "password", null, null,
                null, tempDir, Duration.ofMinutes(5), Duration.ofMinutes(30), Set.of());
        LocalDate date = LocalDate.of(2026, 8, 26);
        RatingService service = new RatingService(mapper, properties);

        service.rate(date, "meal-01", "client-one", 5);
        service.rate(date, "meal-01", "client-one", 3);
        RatingService.RatingSummary summary = service.rate(date, "meal-01", "client-two", 4);

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.average()).isEqualTo(3.5);
        assertThat(service.stats().voteCount()).isEqualTo(2);

        RatingService reloaded = new RatingService(mapper, properties);
        assertThat(reloaded.ratings(date, "client-one").ratings().get("meal-01").myRating()).isEqualTo(3);
    }
}
