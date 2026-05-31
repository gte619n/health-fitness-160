package com.gte619n.healthfitness.core.workout;

// Lifecycle of a planned workout session. Sessions are authored upstream as
// SCHEDULED; the player drives SCHEDULED -> IN_PROGRESS -> COMPLETED. SKIPPED
// is set when a scheduled day passes without being started (reserved; not yet
// written by this IMPL).
public enum SessionStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}
