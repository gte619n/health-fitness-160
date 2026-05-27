package com.gte619n.healthfitness.core.metric;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// TODO(IMPL-12 follow-up): when a writer for {vitals.restingHr, vitals.hrv, vitals.sleepScore} exists, publish MetricChangedEvent via MetricChangedPublisher.
public interface DailyMetricRepository {
    Optional<DailyMetric> findByDate(String userId, LocalDate date);
    List<DailyMetric> findByDateRange(String userId, LocalDate from, LocalDate to);
    void save(DailyMetric metric);
}
