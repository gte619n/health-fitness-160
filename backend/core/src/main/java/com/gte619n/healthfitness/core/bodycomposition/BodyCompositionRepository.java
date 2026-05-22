package com.gte619n.healthfitness.core.bodycomposition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BodyCompositionRepository {
    Optional<BodyCompositionMeasurement> findById(String userId, String recordId);
    List<BodyCompositionMeasurement> findByUserAndRange(
        String userId,
        BodyCompositionMetric metric,
        Instant from,
        Instant to
    );
    List<BodyCompositionMeasurement> findByUser(String userId);
    void save(BodyCompositionMeasurement measurement);
    void saveAll(List<BodyCompositionMeasurement> measurements);
    void delete(String userId, String recordId);
    void deleteByUserMetricAndRange(
        String userId,
        BodyCompositionMetric metric,
        Instant from,
        Instant to
    );
}
