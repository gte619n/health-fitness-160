package com.gte619n.healthfitness.api.workout;

import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import java.time.Instant;
import java.time.LocalDate;

// A completed session's headline fields plus its summary. Returned by
// `complete`, `{id}/summary`, and `latest-completed` so clients (the Android
// summary screen and the web results card) get the title/date alongside the
// numbers without a second fetch.
public record CompletedSessionResponse(
    String sessionId,
    String title,
    String focus,
    LocalDate scheduledDate,
    Instant completedAt,
    SessionSummary summary
) {
    public static CompletedSessionResponse from(WorkoutSession s) {
        return new CompletedSessionResponse(
            s.sessionId(),
            s.title(),
            s.focus(),
            s.scheduledDate(),
            s.completedAt(),
            s.summary()
        );
    }

    public static CompletedSessionResponse of(WorkoutSession s, SessionSummary summary) {
        return new CompletedSessionResponse(
            s.sessionId(),
            s.title(),
            s.focus(),
            s.scheduledDate(),
            s.completedAt(),
            summary
        );
    }
}
