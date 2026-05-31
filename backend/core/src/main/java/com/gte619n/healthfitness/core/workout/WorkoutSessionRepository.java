package com.gte619n.healthfitness.core.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Read + persist planned/live workout sessions. Sessions are authored upstream
// (created elsewhere); this feature reads them, records execution, and writes a
// summary. `save` upserts the whole document (merge semantics), mirroring the
// LocationRepository convention.
public interface WorkoutSessionRepository {
    Optional<WorkoutSession> findById(String userId, String sessionId);

    // The session scheduled for the given day, if any (the dashboard's "today").
    // When multiple exist, prefer a non-completed one, then the most recent.
    Optional<WorkoutSession> findByDate(String userId, LocalDate date);

    List<WorkoutSession> findByDateRange(String userId, LocalDate from, LocalDate to);

    // Most recently completed session (for the web results card).
    Optional<WorkoutSession> findLatestCompleted(String userId);

    void save(WorkoutSession session);
}
