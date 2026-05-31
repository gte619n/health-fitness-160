package com.gte619n.healthfitness.core.workout;

// A single prescribed set authored upstream. Exactly one of targetReps /
// targetSeconds is non-null (rep-based vs. time-based). targetWeight is null
// for bodyweight movements.
public record PrescribedSet(
    String setId,
    Integer targetReps,
    Integer targetSeconds,
    Double targetWeight,
    WeightUnit weightUnit
) {}
