package com.gte619n.healthfitness.core.workout;

// Per-exercise rollup shown on the post-workout summary. `topSet` is a
// human-readable best set (e.g. "10 × 185 lb"), `volume` the exercise's
// tonnage contribution.
public record ExerciseResult(
    String name,
    String topSet,
    double volume
) {}
