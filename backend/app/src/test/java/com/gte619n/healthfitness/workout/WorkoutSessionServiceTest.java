package com.gte619n.healthfitness.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte619n.healthfitness.core.workout.Block;
import com.gte619n.healthfitness.core.workout.BlockType;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.PrescribedExercise;
import com.gte619n.healthfitness.core.workout.PrescribedSet;
import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WeightUnit;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.gte619n.healthfitness.core.workoutaggregate.WeeklyWorkoutAggregate;
import com.gte619n.healthfitness.testsupport.workoutaggregate.InMemoryWeeklyWorkoutAggregateRepository;
import com.gte619n.healthfitness.testsupport.workout.InMemoryWorkoutSessionRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkoutSessionServiceTest {

    private static final String USER = "user-1";
    private static final String SESSION = "ses-1";
    private static final LocalDate SCHEDULED = LocalDate.of(2026, 5, 25); // a Monday

    private InMemoryWorkoutSessionRepository sessions;
    private InMemoryWeeklyWorkoutAggregateRepository aggregates;
    private WorkoutSessionService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryWorkoutSessionRepository();
        aggregates = new InMemoryWeeklyWorkoutAggregateRepository();
        service = new WorkoutSessionService(sessions, aggregates);
    }

    @Test
    void startTransitionsScheduledToInProgress() {
        sessions.save(scheduledSession(Map.of()));

        WorkoutSession started = service.start(USER, SESSION);

        assertThat(started.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isNotNull();
    }

    @Test
    void startIsIdempotentOnceInProgress() {
        sessions.save(scheduledSession(Map.of()));
        WorkoutSession first = service.start(USER, SESSION);

        WorkoutSession second = service.start(USER, SESSION);

        assertThat(second.startedAt()).isEqualTo(first.startedAt());
        assertThat(second.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void logSetStartsSessionAndStoresResult() {
        sessions.save(scheduledSession(Map.of()));

        service.logSet(USER, SESSION, new LoggedSet("s1", 10, 135.0, true, Instant.now()));

        WorkoutSession after = sessions.findById(USER, SESSION).orElseThrow();
        assertThat(after.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(after.loggedSets()).containsKey("s1");
        assertThat(after.loggedSets().get("s1").actualWeight()).isEqualTo(135.0);
    }

    @Test
    void completeComputesSummaryAndBumpsAggregate() {
        Instant startedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        Map<String, LoggedSet> logged = new LinkedHashMap<>();
        logged.put("s1", new LoggedSet("s1", 10, 135.0, true, Instant.now()));
        logged.put("s2", new LoggedSet("s2", 8, 135.0, true, Instant.now()));
        sessions.save(inProgressSession(logged, startedAt));

        SessionSummary summary = service.complete(USER, SESSION);

        assertThat(summary.setsPrescribed()).isEqualTo(2);
        assertThat(summary.setsCompleted()).isEqualTo(2);
        assertThat(summary.totalVolume()).isEqualTo(10 * 135.0 + 8 * 135.0); // 2430
        assertThat(summary.durationSeconds()).isBetween(1790, 1810);
        assertThat(summary.estimatedCalories()).isEqualTo(180); // 30 min * 6 kcal/min
        assertThat(summary.perExercise()).hasSize(1);
        assertThat(summary.perExercise().get(0).name()).isEqualTo("Barbell Row");
        assertThat(summary.perExercise().get(0).topSet()).isEqualTo("10 × 135 lb");

        assertThat(sessions.findById(USER, SESSION).orElseThrow().status())
            .isEqualTo(SessionStatus.COMPLETED);

        LocalDate weekStart = SCHEDULED.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyWorkoutAggregate agg = aggregates.findByWeekStart(USER, weekStart).orElseThrow();
        assertThat(agg.totalTonnage()).isEqualTo(2430.0);
        assertThat(agg.sessionCount()).isEqualTo(1);
    }

    @Test
    void completeIsIdempotentAndDoesNotDoubleCountAggregate() {
        Map<String, LoggedSet> logged = Map.of(
            "s1", new LoggedSet("s1", 10, 135.0, true, Instant.now()));
        sessions.save(inProgressSession(logged, Instant.now().minus(10, ChronoUnit.MINUTES)));

        service.complete(USER, SESSION);
        service.complete(USER, SESSION); // second call

        LocalDate weekStart = SCHEDULED.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyWorkoutAggregate agg = aggregates.findByWeekStart(USER, weekStart).orElseThrow();
        assertThat(agg.totalTonnage()).isEqualTo(1350.0);
        assertThat(agg.sessionCount()).isEqualTo(1);
    }

    @Test
    void incompleteSetsAreNotCountedInVolume() {
        Map<String, LoggedSet> logged = new LinkedHashMap<>();
        logged.put("s1", new LoggedSet("s1", 10, 135.0, true, Instant.now()));
        logged.put("s2", new LoggedSet("s2", null, null, false, Instant.now())); // skipped
        sessions.save(inProgressSession(logged, Instant.now().minus(5, ChronoUnit.MINUTES)));

        SessionSummary summary = service.complete(USER, SESSION);

        assertThat(summary.setsCompleted()).isEqualTo(1);
        assertThat(summary.totalVolume()).isEqualTo(1350.0);
    }

    // ---- fixtures ----

    private static WorkoutSession scheduledSession(Map<String, LoggedSet> logged) {
        return session(SessionStatus.SCHEDULED, logged, null);
    }

    private static WorkoutSession inProgressSession(Map<String, LoggedSet> logged, Instant startedAt) {
        return session(SessionStatus.IN_PROGRESS, logged, startedAt);
    }

    private static WorkoutSession session(SessionStatus status, Map<String, LoggedSet> logged, Instant startedAt) {
        PrescribedSet s1 = new PrescribedSet("s1", 10, null, 135.0, WeightUnit.LB);
        PrescribedSet s2 = new PrescribedSet("s2", 10, null, 135.0, WeightUnit.LB);
        PrescribedExercise row = new PrescribedExercise(
            "ex-row", "Barbell Row", List.of(s1, s2), 90, "Squeeze the shoulder blades.");
        Block block = new Block("b1", BlockType.STRENGTH, "Main", 1, 120, List.of(row));
        Instant now = Instant.now();
        return new WorkoutSession(
            USER, SESSION, SCHEDULED, "Pull Day", "Back & biceps", status, 45,
            List.of(block), logged, startedAt, null, null, now, now);
    }
}
