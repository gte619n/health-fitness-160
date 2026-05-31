package com.gte619n.healthfitness.workout;

import com.gte619n.healthfitness.api.workout.CompletedSessionResponse;
import com.gte619n.healthfitness.api.workout.LogSetRequest;
import com.gte619n.healthfitness.api.workout.WorkoutSessionResponse;
import com.gte619n.healthfitness.core.auth.CurrentUserProvider;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.gte619n.healthfitness.core.workout.WorkoutSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Guided workout session execution. Reads upstream-authored sessions and
// records the user's progress. No create endpoint — sessions are authored
// elsewhere.
@RestController
@RequestMapping("/api/me/workouts/sessions")
public class WorkoutSessionController {

    private final CurrentUserProvider currentUser;
    private final WorkoutSessionService service;
    private final WorkoutSessionRepository repository;

    public WorkoutSessionController(
        CurrentUserProvider currentUser,
        WorkoutSessionService service,
        WorkoutSessionRepository repository
    ) {
        this.currentUser = currentUser;
        this.service = service;
        this.repository = repository;
    }

    // Today's scheduled session for the dashboard start card. 204 on a rest day.
    // Uses the server's local date; per-user timezone handling is future work.
    @GetMapping("/today")
    public ResponseEntity<WorkoutSessionResponse> today() {
        String userId = currentUser.get().userId();
        return service.findToday(userId, LocalDate.now())
            .map(s -> ResponseEntity.ok(WorkoutSessionResponse.from(s)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<WorkoutSessionResponse> range(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = currentUser.get().userId();
        return repository.findByDateRange(userId, from, to).stream()
            .map(WorkoutSessionResponse::from)
            .toList();
    }

    @GetMapping("/latest-completed")
    public ResponseEntity<CompletedSessionResponse> latestCompleted() {
        String userId = currentUser.get().userId();
        return repository.findLatestCompleted(userId)
            .map(s -> ResponseEntity.ok(CompletedSessionResponse.from(s)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public WorkoutSessionResponse get(@PathVariable String sessionId) {
        String userId = currentUser.get().userId();
        return WorkoutSessionResponse.from(require(userId, sessionId));
    }

    @PostMapping("/{sessionId}/start")
    public WorkoutSessionResponse start(@PathVariable String sessionId) {
        String userId = currentUser.get().userId();
        require(userId, sessionId);
        return WorkoutSessionResponse.from(service.start(userId, sessionId));
    }

    @PatchMapping("/{sessionId}/sets/{setId}")
    public LoggedSet logSet(
        @PathVariable String sessionId,
        @PathVariable String setId,
        @RequestBody LogSetRequest body
    ) {
        String userId = currentUser.get().userId();
        require(userId, sessionId);
        LoggedSet log = new LoggedSet(
            setId, body.actualReps(), body.actualWeight(), body.completedOrDefault(), Instant.now());
        return service.logSet(userId, sessionId, log);
    }

    @PostMapping("/{sessionId}/complete")
    public CompletedSessionResponse complete(@PathVariable String sessionId) {
        String userId = currentUser.get().userId();
        require(userId, sessionId);
        SessionSummary summary = service.complete(userId, sessionId);
        WorkoutSession completed = require(userId, sessionId);
        return CompletedSessionResponse.of(completed, summary);
    }

    @GetMapping("/{sessionId}/summary")
    public CompletedSessionResponse summary(@PathVariable String sessionId) {
        String userId = currentUser.get().userId();
        WorkoutSession session = require(userId, sessionId);
        if (session.summary() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not completed");
        }
        return CompletedSessionResponse.from(session);
    }

    private WorkoutSession require(String userId, String sessionId) {
        return repository.findById(userId, sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
