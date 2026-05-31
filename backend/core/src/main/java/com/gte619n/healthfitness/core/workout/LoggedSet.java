package com.gte619n.healthfitness.core.workout;

import java.time.Instant;

// What the user actually did for a given prescribed set, written by the player
// as they progress. Keyed by setId within the session's loggedSets map.
public record LoggedSet(
    String setId,
    Integer actualReps,
    Double actualWeight,
    boolean completed,
    Instant loggedAt
) {}
