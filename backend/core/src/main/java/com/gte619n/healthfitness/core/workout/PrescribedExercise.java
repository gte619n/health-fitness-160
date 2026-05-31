package com.gte619n.healthfitness.core.workout;

import java.util.List;

// One exercise slot within a block. `name` is denormalized so clients can
// render offline; `exerciseId` links to the shared exercise catalog for the
// demo video + cues. `notes` carries any pre-authored coaching cue text.
public record PrescribedExercise(
    String exerciseId,
    String name,
    List<PrescribedSet> prescribedSets,
    int restSecondsBetweenSets,
    String notes
) {}
