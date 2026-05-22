package com.gte619n.healthfitness.core.workout;

import java.util.List;
import java.util.Optional;

public interface WorkoutRepository {
    Optional<Workout> findById(String userId, String workoutId);
    List<Workout> findByUser(String userId);
    void save(Workout workout);
}
