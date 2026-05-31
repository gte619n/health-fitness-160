package com.gte619n.healthfitness.workout;

import com.gte619n.healthfitness.core.workout.Block;
import com.gte619n.healthfitness.core.workout.ExerciseResult;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.PrescribedExercise;
import com.gte619n.healthfitness.core.workout.PrescribedSet;
import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.gte619n.healthfitness.core.workout.WorkoutSessionRepository;
import com.gte619n.healthfitness.core.workoutaggregate.WeeklyWorkoutAggregate;
import com.gte619n.healthfitness.core.workoutaggregate.WeeklyWorkoutAggregateRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

// Execution logic for guided workout sessions: start, set logging, and
// completion (summary math + weekly aggregate bump). Session content
// (blocks/exercises/sets/weights) is authored upstream, so there is no create
// path here. AI recap generation is intentionally deferred (see
// IMPL-WORKOUT-001 phasing); summaries carry a null aiRecap for now.
@Service
public class WorkoutSessionService {

    // Rough resistance-training energy estimate (~360 kcal/hr). A heuristic to
    // be refined by HR / body-weight enrichment in a later spec.
    private static final double CALORIES_PER_MINUTE = 6.0;

    private final WorkoutSessionRepository sessions;
    private final WeeklyWorkoutAggregateRepository aggregates;

    public WorkoutSessionService(
        WorkoutSessionRepository sessions,
        WeeklyWorkoutAggregateRepository aggregates
    ) {
        this.sessions = sessions;
        this.aggregates = aggregates;
    }

    public Optional<WorkoutSession> findToday(String userId, LocalDate today) {
        return sessions.findByDate(userId, today);
    }

    // Transition SCHEDULED -> IN_PROGRESS. Idempotent: an already-started or
    // completed session is returned unchanged.
    public WorkoutSession start(String userId, String sessionId) {
        WorkoutSession existing = sessions.findById(userId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (existing.status() != SessionStatus.SCHEDULED) {
            return existing;
        }
        Instant now = Instant.now();
        WorkoutSession started = withStatus(existing, SessionStatus.IN_PROGRESS, now, existing.completedAt(),
            existing.summary(), now);
        sessions.save(started);
        return started;
    }

    // Record (or overwrite) the actual result for one prescribed set. Starts the
    // session if it was still SCHEDULED so the first logged set implies it began.
    public LoggedSet logSet(String userId, String sessionId, LoggedSet log) {
        WorkoutSession existing = sessions.findById(userId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        Map<String, LoggedSet> logged = new LinkedHashMap<>(
            existing.loggedSets() == null ? Map.of() : existing.loggedSets());
        logged.put(log.setId(), log);

        Instant now = Instant.now();
        SessionStatus status = existing.status() == SessionStatus.SCHEDULED
            ? SessionStatus.IN_PROGRESS : existing.status();
        Instant startedAt = existing.startedAt() != null ? existing.startedAt() : now;

        WorkoutSession updated = new WorkoutSession(
            existing.userId(), existing.sessionId(), existing.scheduledDate(),
            existing.title(), existing.focus(), status, existing.estimatedMinutes(),
            existing.blocks(), logged, startedAt, existing.completedAt(),
            existing.summary(), existing.createdAt(), now
        );
        sessions.save(updated);
        return log;
    }

    // Finalize the session: compute the summary, mark COMPLETED, and bump the
    // user's weekly tonnage aggregate. Idempotent — completing again recomputes
    // the summary but does not double-count the aggregate.
    public SessionSummary complete(String userId, String sessionId) {
        WorkoutSession existing = sessions.findById(userId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        Instant now = Instant.now();
        Instant completedAt = existing.completedAt() != null ? existing.completedAt() : now;
        SessionSummary summary = summarize(existing, completedAt);

        WorkoutSession completed = new WorkoutSession(
            existing.userId(), existing.sessionId(), existing.scheduledDate(),
            existing.title(), existing.focus(), SessionStatus.COMPLETED,
            existing.estimatedMinutes(), existing.blocks(), existing.loggedSets(),
            existing.startedAt(), completedAt, summary, existing.createdAt(), now
        );
        sessions.save(completed);

        // Only contribute to the weekly aggregate on the first completion.
        if (existing.status() != SessionStatus.COMPLETED) {
            bumpWeeklyAggregate(userId, existing.scheduledDate(), summary.totalVolume());
        }
        return summary;
    }

    // ---- summary math (package-private + static so it's unit-testable) ----

    static SessionSummary summarize(WorkoutSession session, Instant completedAt) {
        Map<String, LoggedSet> logged = session.loggedSets() == null ? Map.of() : session.loggedSets();
        List<Block> blocks = session.blocks() == null ? List.of() : session.blocks();

        int setsPrescribed = 0;
        int setsCompleted = 0;
        double totalVolume = 0.0;
        List<ExerciseResult> perExercise = new ArrayList<>();

        for (Block block : blocks) {
            List<PrescribedExercise> exercises = block.exercises() == null ? List.of() : block.exercises();
            for (PrescribedExercise exercise : exercises) {
                List<PrescribedSet> prescribed = exercise.prescribedSets() == null
                    ? List.of() : exercise.prescribedSets();
                setsPrescribed += prescribed.size();

                double exerciseVolume = 0.0;
                String topSet = null;
                double topWeight = Double.NEGATIVE_INFINITY;

                for (PrescribedSet set : prescribed) {
                    LoggedSet result = logged.get(set.setId());
                    if (result == null || !result.completed()) continue;
                    setsCompleted++;
                    if (result.actualReps() != null && result.actualWeight() != null) {
                        double v = result.actualReps() * result.actualWeight();
                        exerciseVolume += v;
                        if (result.actualWeight() > topWeight) {
                            topWeight = result.actualWeight();
                            topSet = formatTopSet(result, set.weightUnit());
                        }
                    } else if (topSet == null && result.actualReps() != null) {
                        // Bodyweight or untracked-weight set still has a "top set".
                        topSet = result.actualReps() + " reps";
                    }
                }

                totalVolume += exerciseVolume;
                perExercise.add(new ExerciseResult(
                    exercise.name(),
                    topSet != null ? topSet : "—",
                    exerciseVolume
                ));
            }
        }

        int durationSeconds = session.startedAt() != null && completedAt != null
            ? (int) Math.max(0, Duration.between(session.startedAt(), completedAt).getSeconds())
            : 0;
        int estimatedCalories = (int) Math.round((durationSeconds / 60.0) * CALORIES_PER_MINUTE);

        return new SessionSummary(
            durationSeconds, totalVolume, setsCompleted, setsPrescribed,
            estimatedCalories, perExercise, null /* aiRecap deferred */
        );
    }

    private static String formatTopSet(LoggedSet set, com.gte619n.healthfitness.core.workout.WeightUnit unit) {
        String weight = trimWeight(set.actualWeight());
        String u = unit == null ? "lb" : unit.name().toLowerCase();
        return set.actualReps() + " × " + weight + " " + u;
    }

    private static String trimWeight(double weight) {
        if (weight == Math.rint(weight)) {
            return String.valueOf((long) weight);
        }
        return String.valueOf(weight);
    }

    private void bumpWeeklyAggregate(String userId, LocalDate scheduledDate, double volume) {
        LocalDate weekStart = scheduledDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyWorkoutAggregate existing = aggregates.findByWeekStart(userId, weekStart).orElse(null);
        double tonnage = (existing != null && existing.totalTonnage() != null ? existing.totalTonnage() : 0.0) + volume;
        int count = (existing != null && existing.sessionCount() != null ? existing.sessionCount() : 0) + 1;
        Instant now = Instant.now();
        Instant createdAt = existing != null && existing.createdAt() != null ? existing.createdAt() : now;
        aggregates.save(new WeeklyWorkoutAggregate(userId, weekStart, tonnage, count, createdAt, now));
    }

    private static WorkoutSession withStatus(
        WorkoutSession s, SessionStatus status, Instant startedAt, Instant completedAt,
        SessionSummary summary, Instant updatedAt
    ) {
        return new WorkoutSession(
            s.userId(), s.sessionId(), s.scheduledDate(), s.title(), s.focus(), status,
            s.estimatedMinutes(), s.blocks(), s.loggedSets(), startedAt, completedAt,
            summary, s.createdAt(), updatedAt
        );
    }
}
