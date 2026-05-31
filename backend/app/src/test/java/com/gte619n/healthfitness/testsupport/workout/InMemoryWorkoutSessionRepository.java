package com.gte619n.healthfitness.testsupport.workout;

import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.gte619n.healthfitness.core.workout.WorkoutSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkoutSessionRepository implements WorkoutSessionRepository {

    // userId -> sessionId -> session
    private final Map<String, Map<String, WorkoutSession>> byUser = new ConcurrentHashMap<>();

    public void clear() {
        byUser.clear();
    }

    @Override
    public Optional<WorkoutSession> findById(String userId, String sessionId) {
        return Optional.ofNullable(byUser.getOrDefault(userId, Map.of()).get(sessionId));
    }

    @Override
    public Optional<WorkoutSession> findByDate(String userId, LocalDate date) {
        List<WorkoutSession> matches = byUser.getOrDefault(userId, Map.of()).values().stream()
            .filter(s -> date.equals(s.scheduledDate()))
            .toList();
        return matches.stream()
            .filter(s -> s.status() != SessionStatus.COMPLETED && s.status() != SessionStatus.SKIPPED)
            .findFirst()
            .or(() -> matches.stream().max(Comparator.comparing(
                s -> s.updatedAt() == null ? Instant.EPOCH : s.updatedAt())));
    }

    @Override
    public List<WorkoutSession> findByDateRange(String userId, LocalDate from, LocalDate to) {
        return byUser.getOrDefault(userId, Map.of()).values().stream()
            .filter(s -> s.scheduledDate() != null
                && !s.scheduledDate().isBefore(from) && !s.scheduledDate().isAfter(to))
            .sorted(Comparator.comparing(WorkoutSession::scheduledDate))
            .toList();
    }

    @Override
    public Optional<WorkoutSession> findLatestCompleted(String userId) {
        return byUser.getOrDefault(userId, Map.of()).values().stream()
            .filter(s -> s.status() == SessionStatus.COMPLETED)
            .max(Comparator.comparing(s -> s.completedAt() == null ? Instant.EPOCH : s.completedAt()));
    }

    @Override
    public void save(WorkoutSession session) {
        byUser.computeIfAbsent(session.userId(), k -> new ConcurrentHashMap<>())
            .put(session.sessionId(), session);
    }
}
