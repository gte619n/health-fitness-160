package com.gte619n.healthfitness.core.workout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// A planned workout session plus the live execution data the player accrues.
// The blocks/exercises/sets are authored upstream; `status`, `startedAt`,
// `loggedSets`, `completedAt`, and `summary` are written as the user executes.
// Stored at users/{userId}/workoutSessions/{sessionId}.
public record WorkoutSession(
    String userId,
    String sessionId,
    LocalDate scheduledDate,
    String title,
    String focus,
    SessionStatus status,
    int estimatedMinutes,
    List<Block> blocks,
    Map<String, LoggedSet> loggedSets,
    Instant startedAt,
    Instant completedAt,
    SessionSummary summary,
    Instant createdAt,
    Instant updatedAt
) {}
