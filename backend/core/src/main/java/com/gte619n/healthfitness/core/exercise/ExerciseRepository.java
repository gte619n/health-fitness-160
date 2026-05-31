package com.gte619n.healthfitness.core.exercise;

import java.util.Optional;

// Read access to the shared exercise catalog. Catalog authoring happens
// upstream; this feature only resolves an exercise's demo + cues by id.
public interface ExerciseRepository {
    Optional<Exercise> findById(String exerciseId);
}
