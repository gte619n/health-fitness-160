package com.gte619n.healthfitness.core.metric;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyMetricRepository {
    Optional<DailyMetric> findByDate(String userId, LocalDate date);
    List<DailyMetric> findByDateRange(String userId, LocalDate from, LocalDate to);
    void save(DailyMetric metric);
}
