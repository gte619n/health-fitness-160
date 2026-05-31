package com.gte619n.healthfitness.api.workout;

import com.gte619n.healthfitness.core.workout.Block;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Full session payload for the overview + player. Embeds the core Block /
// LoggedSet / SessionSummary records directly (same approach LocationResponse
// takes with HoursSlot) — no parallel DTO tree. `userId` is intentionally
// omitted; it's implied by the authenticated caller.
public record WorkoutSessionResponse(
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
) {
    public static WorkoutSessionResponse from(WorkoutSession s) {
        return new WorkoutSessionResponse(
            s.sessionId(),
            s.scheduledDate(),
            s.title(),
            s.focus(),
            s.status(),
            s.estimatedMinutes(),
            s.blocks() == null ? List.of() : s.blocks(),
            s.loggedSets() == null ? Map.of() : s.loggedSets(),
            s.startedAt(),
            s.completedAt(),
            s.summary(),
            s.createdAt(),
            s.updatedAt()
        );
    }
}
