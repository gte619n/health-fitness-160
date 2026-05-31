package com.gte619n.healthfitness.workout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte619n.healthfitness.core.workout.Block;
import com.gte619n.healthfitness.core.workout.BlockType;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.PrescribedExercise;
import com.gte619n.healthfitness.core.workout.PrescribedSet;
import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.WeightUnit;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.gte619n.healthfitness.core.workout.WorkoutSessionRepository;
import com.gte619n.healthfitness.testsupport.TestPersistenceConfig;
import com.gte619n.healthfitness.testsupport.workout.InMemoryWorkoutSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestPersistenceConfig.class)
class WorkoutSessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired WorkoutSessionRepository repository;
    @Autowired ObjectMapper objectMapper;

    private static final String USER = "user-123";

    @BeforeEach
    void setUp() {
        ((InMemoryWorkoutSessionRepository) repository).clear();
    }

    @Test
    void todayReturns204WhenNothingScheduled() throws Exception {
        mvc.perform(get("/api/me/workouts/sessions/today").header("X-Dev-User", USER))
            .andExpect(status().isNoContent());
    }

    @Test
    void todayReturnsScheduledSession() throws Exception {
        repository.save(session("ses-today", LocalDate.now(), SessionStatus.SCHEDULED, Map.of(), null));

        mvc.perform(get("/api/me/workouts/sessions/today").header("X-Dev-User", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("ses-today"))
            .andExpect(jsonPath("$.title").value("Pull Day"))
            .andExpect(jsonPath("$.blocks[0].exercises[0].name").value("Barbell Row"));
    }

    @Test
    void startTransitionsToInProgress() throws Exception {
        repository.save(session("ses-1", LocalDate.now(), SessionStatus.SCHEDULED, Map.of(), null));

        mvc.perform(post("/api/me/workouts/sessions/ses-1/start").header("X-Dev-User", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.startedAt").exists());
    }

    @Test
    void logSetRecordsResult() throws Exception {
        repository.save(session("ses-1", LocalDate.now(), SessionStatus.IN_PROGRESS, Map.of(), Instant.now()));
        String body = objectMapper.writeValueAsString(Map.of("actualReps", 10, "actualWeight", 135.0, "completed", true));

        mvc.perform(patch("/api/me/workouts/sessions/ses-1/sets/s1")
                .header("X-Dev-User", USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setId").value("s1"))
            .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void completeReturnsSummaryWithVolume() throws Exception {
        Map<String, LoggedSet> logged = Map.of(
            "s1", new LoggedSet("s1", 10, 135.0, true, Instant.now()),
            "s2", new LoggedSet("s2", 8, 135.0, true, Instant.now()));
        repository.save(session("ses-1", LocalDate.now(), SessionStatus.IN_PROGRESS, logged, Instant.now()));

        mvc.perform(post("/api/me/workouts/sessions/ses-1/complete").header("X-Dev-User", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("ses-1"))
            .andExpect(jsonPath("$.summary.setsCompleted").value(2))
            .andExpect(jsonPath("$.summary.totalVolume").value(2430.0));
    }

    @Test
    void summaryIs404BeforeCompletion() throws Exception {
        repository.save(session("ses-1", LocalDate.now(), SessionStatus.IN_PROGRESS, Map.of(), Instant.now()));

        mvc.perform(get("/api/me/workouts/sessions/ses-1/summary").header("X-Dev-User", USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMissingSessionIs404() throws Exception {
        mvc.perform(get("/api/me/workouts/sessions/nope").header("X-Dev-User", USER))
            .andExpect(status().isNotFound());
    }

    private static WorkoutSession session(
        String sessionId, LocalDate date, SessionStatus status,
        Map<String, LoggedSet> logged, Instant startedAt
    ) {
        PrescribedSet s1 = new PrescribedSet("s1", 10, null, 135.0, WeightUnit.LB);
        PrescribedSet s2 = new PrescribedSet("s2", 10, null, 135.0, WeightUnit.LB);
        PrescribedExercise row = new PrescribedExercise(
            "ex-row", "Barbell Row", List.of(s1, s2), 90, "Squeeze.");
        Block block = new Block("b1", BlockType.STRENGTH, "Main", 1, 120, List.of(row));
        Instant now = Instant.now();
        return new WorkoutSession(
            USER, sessionId, date, "Pull Day", "Back & biceps", status, 45,
            List.of(block), logged, startedAt, null, null, now, now);
    }
}
