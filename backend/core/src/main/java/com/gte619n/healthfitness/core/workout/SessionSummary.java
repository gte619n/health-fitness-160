package com.gte619n.healthfitness.core.workout;

import java.util.List;

// Finalized recap computed at completion. `aiRecap` is an optional
// AI-generated note (null when generation is disabled or fails).
public record SessionSummary(
    int durationSeconds,
    double totalVolume,
    int setsCompleted,
    int setsPrescribed,
    int estimatedCalories,
    List<ExerciseResult> perExercise,
    String aiRecap
) {}
