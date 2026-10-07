package com.ssafy.welstory.meal;

import com.ssafy.welstory.config.WelstoryProperties;
import com.ssafy.welstory.meal.persistence.MealCacheStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuHistoryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final MealCacheStore store;
    private final Path imageRoot;

    public MenuHistoryService(JdbcTemplate jdbc, MealCacheStore store, WelstoryProperties properties) {
        this.jdbc = jdbc;
        this.store = store;
        this.imageRoot = properties.cacheDir().toAbsolutePath().normalize();
    }

    public Map<String, Entry> references(LocalDate date) {
        Map<String, Entry> references = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate before = date.isBefore(today) ? date : today;
        store.find(date).ifPresent(day -> {
            for (var meal : day.meals()) {
                if (meal.hasCachedImage() && existingImage(date, meal.imageFile())) continue;
                // Compare normalized whole names; dish words and ingredients must still match.
                for (var previous : findPhotos(meal.name(), before)) {
                    if (previous.imageUrl() != null) {
                        references.put(meal.id(), previous);
                        break;
                    }
                }
            }
        });
        return references;
    }

    private List<Entry> findPhotos(String name, LocalDate before) {
        return jdbc.query("""
                SELECT d.meal_date, m->>'id' AS meal_id,
                       m->>'imageFile' AS image_file, m->>'imageHash' AS image_hash
                FROM meal_days d CROSS JOIN LATERAL jsonb_array_elements(d.meals_json::jsonb) m
                WHERE d.meal_date < ?
                  AND welstory_normalize_menu_name(m->>'name') = welstory_normalize_menu_name(?)
                  AND welstory_normalize_menu_name(m->>'name') <> ''
                  AND COALESCE((m->>'placeholder')::boolean, false) = false
                  AND m->>'imageFile' IS NOT NULL AND m->>'imageHash' IS NOT NULL
                ORDER BY d.meal_date DESC, m->>'id' ASC LIMIT 30
                """, (rs, row) -> {
            LocalDate date = rs.getObject("meal_date", LocalDate.class);
            String id = rs.getString("meal_id");
            String hash = rs.getString("image_hash");
            String imageUrl = existingImage(date, rs.getString("image_file"))
                    ? "/api/meals/%s/images/%s?v=%s".formatted(date, id, hash) : null;
            return new Entry(date, imageUrl);
        }, before, name);
    }

    private boolean existingImage(LocalDate date, String file) {
        if (file == null || file.isBlank()) return false;
        Path directory = imageRoot.resolve(date.toString());
        Path path = directory.resolve(file).normalize();
        return path.startsWith(directory) && Files.isRegularFile(path);
    }

    public record Entry(LocalDate date, String imageUrl) {}
}
