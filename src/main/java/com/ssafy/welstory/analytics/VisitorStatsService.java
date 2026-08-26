package com.ssafy.welstory.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.config.WelstoryProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class VisitorStatsService {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final Path statsFile;

    private final Map<String, Set<String>> visitors =
            new HashMap<>();

    public VisitorStatsService(
            ObjectMapper objectMapper,
            WelstoryProperties properties
    ) {
        this.objectMapper = objectMapper;

        this.statsFile = properties
                .cacheDir()
                .toAbsolutePath()
                .normalize()
                .resolve("visitor-stats.json");

        load();
    }

    public synchronized void record(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }

        LocalDate today =
                LocalDate.now(SEOUL);

        visitors
                .computeIfAbsent(
                        today.toString(),
                        ignored -> new HashSet<>()
                )
                .add(clientId);

        persist();
    }

    public synchronized Stats stats() {
        LocalDate today =
                LocalDate.now(SEOUL);

        YearMonth month =
                YearMonth.from(today);

        int dailyVisitors =
                visitors
                        .getOrDefault(
                                today.toString(),
                                Set.of()
                        )
                        .size();

        Set<String> monthlyUnique =
                new HashSet<>();

        visitors.forEach((dateText, ids) -> {
            try {
                LocalDate date =
                        LocalDate.parse(dateText);

                if (YearMonth.from(date).equals(month)) {
                    monthlyUnique.addAll(ids);
                }
            } catch (Exception ignored) {
            }
        });

        Map<String, Integer> daily =
                new HashMap<>();

        visitors.forEach((date, ids) ->
                daily.put(date, ids.size())
        );

        return new Stats(
                dailyVisitors,
                monthlyUnique.size(),
                Map.copyOf(daily)
        );
    }

    private void load() {
        if (!Files.isRegularFile(statsFile)) {
            return;
        }

        try {
            Map<String, Set<String>> loaded =
                    objectMapper.readValue(
                            statsFile.toFile(),
                            new TypeReference<>() {}
                    );

            visitors.putAll(loaded);
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            Files.createDirectories(
                    statsFile.getParent()
            );

            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(
                            statsFile.toFile(),
                            visitors
                    );
        } catch (IOException ignored) {
        }
    }

    public record Stats(
            int dailyVisitors,
            int monthlyVisitors,
            Map<String, Integer> daily
    ) {}
}