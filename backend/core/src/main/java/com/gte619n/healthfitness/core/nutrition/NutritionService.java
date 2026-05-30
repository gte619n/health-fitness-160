package com.gte619n.healthfitness.core.nutrition;

import com.gte619n.healthfitness.core.goals.eval.MetricKey;
import com.gte619n.healthfitness.core.goals.events.MetricChangedPublisher;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Writes and reads the user's daily nutrition log. A day is keyed by
 * {@code (userId, date)} — {@link #logDay} upserts that day's
 * {@link NutritionDailyLog} rather than appending duplicates.
 *
 * <p>Timestamps follow the codebase convention: the record carries
 * {@code null} for created/updated and the Firestore repo stamps the
 * server timestamp on write. After a successful save this service
 * publishes one {@link MetricChangedEvent} for each of the four
 * nutrition metric keys (protein/carbs/fat/calories) so any bound Steps
 * re-evaluate — published AFTER the save, never before.
 */
@Service
public class NutritionService {

    private static final List<MetricKey> NUTRITION_KEYS = List.of(
        MetricKey.NUTRITION_PROTEIN_AVG_7D,
        MetricKey.NUTRITION_CARBS_AVG_7D,
        MetricKey.NUTRITION_FAT_AVG_7D,
        MetricKey.NUTRITION_CALORIES_AVG_7D
    );

    private final NutritionDailyLogRepository repository;
    private final MetricChangedPublisher metricChangedPublisher;

    public NutritionService(
        NutritionDailyLogRepository repository,
        MetricChangedPublisher metricChangedPublisher
    ) {
        this.repository = repository;
        this.metricChangedPublisher = metricChangedPublisher;
    }

    /**
     * Upsert the nutrition log for {@code date}. Any existing row for
     * that day is replaced. {@code caloriesKcal} is optional — when
     * null, the calories metric is derived from macros at read time.
     */
    public NutritionDailyLog logDay(
        String userId,
        LocalDate date,
        Double proteinGrams,
        Double carbsGrams,
        Double fatGrams,
        Double caloriesKcal
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        NutritionDailyLog log = new NutritionDailyLog(
            userId, date, proteinGrams, carbsGrams, fatGrams, caloriesKcal, null, null);
        repository.save(log);
        // Publish after the save so a failed save never fires events.
        metricChangedPublisher.publishAll(userId, NUTRITION_KEYS);
        return log;
    }

    public Optional<NutritionDailyLog> findByDate(String userId, LocalDate date) {
        return repository.findByDate(userId, date);
    }

    public List<NutritionDailyLog> findRange(String userId, LocalDate from, LocalDate to) {
        return repository.findByDateRange(userId, from, to);
    }
}
