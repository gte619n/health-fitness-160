package com.gte619n.healthfitness.persistence.metric;

import com.gte619n.healthfitness.core.metric.DailyMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Scaffold. Persists to users/{userId}/dailyMetrics/{yyyy-MM-dd}. Real
// reads and writes land alongside Google Health ingestion in a later IMPL.
@Repository
public class DailyMetricRepository implements com.gte619n.healthfitness.core.metric.DailyMetricRepository {

    @Override
    public Optional<DailyMetric> findByDate(String userId, LocalDate date) {
        throw new UnsupportedOperationException("daily-metric reads land with a later IMPL");
    }

    @Override
    public List<DailyMetric> findByDateRange(String userId, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("daily-metric reads land with a later IMPL");
    }

    @Override
    public void save(DailyMetric metric) {
        throw new UnsupportedOperationException("daily-metric writes land with a later IMPL");
    }
}
