package com.gte619n.healthfitness.api.workout;

// Partial log for a single set. `actualReps` / `actualWeight` are null for
// bodyweight or untracked-weight sets. `completed` defaults to true when
// omitted (logging a set implies it was performed).
public record LogSetRequest(
    Integer actualReps,
    Double actualWeight,
    Boolean completed
) {
    public boolean completedOrDefault() {
        return completed == null || completed;
    }
}
