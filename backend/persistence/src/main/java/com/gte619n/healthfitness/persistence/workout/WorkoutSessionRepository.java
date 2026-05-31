package com.gte619n.healthfitness.persistence.workout;

import static com.gte619n.healthfitness.persistence.FirestoreMapper.serverTimestamp;
import static com.gte619n.healthfitness.persistence.FirestoreMapper.toInstant;

import com.gte619n.healthfitness.core.workout.Block;
import com.gte619n.healthfitness.core.workout.BlockType;
import com.gte619n.healthfitness.core.workout.ExerciseResult;
import com.gte619n.healthfitness.core.workout.LoggedSet;
import com.gte619n.healthfitness.core.workout.PrescribedExercise;
import com.gte619n.healthfitness.core.workout.PrescribedSet;
import com.gte619n.healthfitness.core.workout.SessionStatus;
import com.gte619n.healthfitness.core.workout.SessionSummary;
import com.gte619n.healthfitness.core.workout.WeightUnit;
import com.gte619n.healthfitness.core.workout.WorkoutSession;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

// Firestore-backed workout session repository.
// Documents live at users/{userId}/workoutSessions/{sessionId}. Session
// content (blocks) is authored upstream; this writer only ever merges, so
// re-saving preserves whatever fields it doesn't touch.
@Repository
@ConditionalOnProperty(name = "app.persistence.firestore-enabled", havingValue = "true", matchIfMissing = true)
public class WorkoutSessionRepository
    implements com.gte619n.healthfitness.core.workout.WorkoutSessionRepository {

    private static final String SUBCOLLECTION = "workoutSessions";
    private static final int COMPLETED_SCAN_LIMIT = 50;

    private final Firestore firestore;

    public WorkoutSessionRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Optional<WorkoutSession> findById(String userId, String sessionId) {
        DocumentSnapshot snapshot = await(collection(userId).document(sessionId).get());
        if (!snapshot.exists()) return Optional.empty();
        return Optional.of(toSession(userId, snapshot));
    }

    @Override
    public Optional<WorkoutSession> findByDate(String userId, LocalDate date) {
        List<QueryDocumentSnapshot> docs = await(collection(userId)
            .whereEqualTo("scheduledDate", date.toString())
            .limit(10)
            .get()).getDocuments();
        List<WorkoutSession> matches = docs.stream().map(d -> toSession(userId, d)).toList();
        // Prefer a session still in play; otherwise the most recently updated.
        return matches.stream()
            .filter(s -> s.status() != SessionStatus.COMPLETED && s.status() != SessionStatus.SKIPPED)
            .findFirst()
            .or(() -> matches.stream().max(Comparator.comparing(
                s -> s.updatedAt() == null ? Instant.EPOCH : s.updatedAt())));
    }

    @Override
    public List<WorkoutSession> findByDateRange(String userId, LocalDate from, LocalDate to) {
        // ISO date strings sort lexicographically the same as chronologically.
        List<QueryDocumentSnapshot> docs = await(collection(userId)
            .whereGreaterThanOrEqualTo("scheduledDate", from.toString())
            .whereLessThanOrEqualTo("scheduledDate", to.toString())
            .orderBy("scheduledDate", Query.Direction.ASCENDING)
            .limit(100)
            .get()).getDocuments();
        return docs.stream().map(d -> toSession(userId, d)).toList();
    }

    @Override
    public Optional<WorkoutSession> findLatestCompleted(String userId) {
        // Equality + orderBy on different fields needs a composite index; to keep
        // Phase 0 index-free we scan recent completed docs and sort in memory.
        List<QueryDocumentSnapshot> docs = await(collection(userId)
            .whereEqualTo("status", SessionStatus.COMPLETED.name())
            .limit(COMPLETED_SCAN_LIMIT)
            .get()).getDocuments();
        return docs.stream()
            .map(d -> toSession(userId, d))
            .max(Comparator.comparing(s -> s.completedAt() == null ? Instant.EPOCH : s.completedAt()));
    }

    @Override
    public void save(WorkoutSession session) {
        DocumentReference docRef = collection(session.userId()).document(session.sessionId());
        DocumentSnapshot existing = await(docRef.get());
        Map<String, Object> body = toBody(session, !existing.exists());
        await(docRef.set(body, SetOptions.merge()));
    }

    private CollectionReference collection(String userId) {
        return firestore.collection("users").document(userId).collection(SUBCOLLECTION);
    }

    // ---- serialize ----

    private static Map<String, Object> toBody(WorkoutSession s, boolean isNew) {
        Map<String, Object> body = new HashMap<>();
        body.put("scheduledDate", s.scheduledDate() == null ? null : s.scheduledDate().toString());
        body.put("title", s.title());
        body.put("focus", s.focus());
        body.put("status", s.status() == null ? null : s.status().name());
        body.put("estimatedMinutes", s.estimatedMinutes());
        body.put("blocks", blocksToList(s.blocks()));
        body.put("loggedSets", loggedSetsToMap(s.loggedSets()));
        body.put("startedAt", toTimestamp(s.startedAt()));
        body.put("completedAt", toTimestamp(s.completedAt()));
        body.put("summary", summaryToMap(s.summary()));
        body.put("updatedAt", serverTimestamp());
        if (isNew) {
            body.put("createdAt", serverTimestamp());
        }
        return body;
    }

    private static List<Map<String, Object>> blocksToList(List<Block> blocks) {
        if (blocks == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Block b : blocks) {
            Map<String, Object> m = new HashMap<>();
            m.put("blockId", b.blockId());
            m.put("type", b.type() == null ? null : b.type().name());
            m.put("label", b.label());
            m.put("rounds", b.rounds());
            m.put("restSecondsAfter", b.restSecondsAfter());
            List<Map<String, Object>> ex = new ArrayList<>();
            if (b.exercises() != null) {
                for (PrescribedExercise e : b.exercises()) {
                    Map<String, Object> em = new HashMap<>();
                    em.put("exerciseId", e.exerciseId());
                    em.put("name", e.name());
                    em.put("restSecondsBetweenSets", e.restSecondsBetweenSets());
                    em.put("notes", e.notes());
                    List<Map<String, Object>> sets = new ArrayList<>();
                    if (e.prescribedSets() != null) {
                        for (PrescribedSet ps : e.prescribedSets()) {
                            Map<String, Object> sm = new HashMap<>();
                            sm.put("setId", ps.setId());
                            sm.put("targetReps", ps.targetReps());
                            sm.put("targetSeconds", ps.targetSeconds());
                            sm.put("targetWeight", ps.targetWeight());
                            sm.put("weightUnit", ps.weightUnit() == null ? null : ps.weightUnit().name());
                            sets.add(sm);
                        }
                    }
                    em.put("prescribedSets", sets);
                    ex.add(em);
                }
            }
            m.put("exercises", ex);
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> loggedSetsToMap(Map<String, LoggedSet> logged) {
        Map<String, Object> out = new HashMap<>();
        if (logged == null) return out;
        for (Map.Entry<String, LoggedSet> entry : logged.entrySet()) {
            LoggedSet l = entry.getValue();
            Map<String, Object> m = new HashMap<>();
            m.put("setId", l.setId());
            m.put("actualReps", l.actualReps());
            m.put("actualWeight", l.actualWeight());
            m.put("completed", l.completed());
            m.put("loggedAt", toTimestamp(l.loggedAt()));
            out.put(entry.getKey(), m);
        }
        return out;
    }

    private static Map<String, Object> summaryToMap(SessionSummary s) {
        if (s == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("durationSeconds", s.durationSeconds());
        m.put("totalVolume", s.totalVolume());
        m.put("setsCompleted", s.setsCompleted());
        m.put("setsPrescribed", s.setsPrescribed());
        m.put("estimatedCalories", s.estimatedCalories());
        m.put("aiRecap", s.aiRecap());
        List<Map<String, Object>> per = new ArrayList<>();
        if (s.perExercise() != null) {
            for (ExerciseResult r : s.perExercise()) {
                Map<String, Object> rm = new HashMap<>();
                rm.put("name", r.name());
                rm.put("topSet", r.topSet());
                rm.put("volume", r.volume());
                per.add(rm);
            }
        }
        m.put("perExercise", per);
        return m;
    }

    private static Timestamp toTimestamp(Instant instant) {
        if (instant == null) return null;
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    // ---- deserialize ----

    private static WorkoutSession toSession(String userId, DocumentSnapshot doc) {
        String scheduled = doc.getString("scheduledDate");
        return new WorkoutSession(
            userId,
            doc.getId(),
            scheduled == null ? null : LocalDate.parse(scheduled),
            doc.getString("title"),
            doc.getString("focus"),
            parseStatus(doc.getString("status")),
            toInt(doc.get("estimatedMinutes")),
            blocksFromList(doc.get("blocks")),
            loggedSetsFromMap(doc.get("loggedSets")),
            toInstant(doc.get("startedAt")),
            toInstant(doc.get("completedAt")),
            summaryFromMap(doc.get("summary")),
            toInstant(doc.get("createdAt")),
            toInstant(doc.get("updatedAt"))
        );
    }

    private static SessionStatus parseStatus(String raw) {
        if (raw == null) return SessionStatus.SCHEDULED;
        try {
            return SessionStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SessionStatus.SCHEDULED;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Block> blocksFromList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Block> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            BlockType type;
            try {
                type = m.get("type") == null ? BlockType.STRENGTH
                    : BlockType.valueOf(m.get("type").toString());
            } catch (IllegalArgumentException e) {
                type = BlockType.STRENGTH;
            }
            out.add(new Block(
                str(m.get("blockId")),
                type,
                str(m.get("label")),
                toInt(m.get("rounds")),
                toInt(m.get("restSecondsAfter")),
                exercisesFromList(m.get("exercises"))
            ));
        }
        return out;
    }

    private static List<PrescribedExercise> exercisesFromList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<PrescribedExercise> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            out.add(new PrescribedExercise(
                str(m.get("exerciseId")),
                str(m.get("name")),
                setsFromList(m.get("prescribedSets")),
                toInt(m.get("restSecondsBetweenSets")),
                str(m.get("notes"))
            ));
        }
        return out;
    }

    private static List<PrescribedSet> setsFromList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<PrescribedSet> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            WeightUnit unit;
            try {
                unit = m.get("weightUnit") == null ? WeightUnit.LB
                    : WeightUnit.valueOf(m.get("weightUnit").toString());
            } catch (IllegalArgumentException e) {
                unit = WeightUnit.LB;
            }
            out.add(new PrescribedSet(
                str(m.get("setId")),
                toIntOrNull(m.get("targetReps")),
                toIntOrNull(m.get("targetSeconds")),
                toDoubleOrNull(m.get("targetWeight")),
                unit
            ));
        }
        return out;
    }

    private static Map<String, LoggedSet> loggedSetsFromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return Map.of();
        Map<String, LoggedSet> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Map<?, ?> v)) continue;
            String setId = entry.getKey().toString();
            Boolean completed = v.get("completed") instanceof Boolean b ? b : Boolean.FALSE;
            out.put(setId, new LoggedSet(
                v.get("setId") != null ? v.get("setId").toString() : setId,
                toIntOrNull(v.get("actualReps")),
                toDoubleOrNull(v.get("actualWeight")),
                completed,
                toInstant(v.get("loggedAt"))
            ));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static SessionSummary summaryFromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        List<ExerciseResult> per = new ArrayList<>();
        if (m.get("perExercise") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rm) {
                    per.add(new ExerciseResult(
                        str(rm.get("name")),
                        str(rm.get("topSet")),
                        toDouble(rm.get("volume"))
                    ));
                }
            }
        }
        return new SessionSummary(
            toInt(m.get("durationSeconds")),
            toDouble(m.get("totalVolume")),
            toInt(m.get("setsCompleted")),
            toInt(m.get("setsPrescribed")),
            toInt(m.get("estimatedCalories")),
            per,
            str(m.get("aiRecap"))
        );
    }

    // ---- primitive coercion (Firestore numbers arrive as Long / Double) ----

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static Integer toIntOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static Double toDoubleOrNull(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore call interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore call failed", e.getCause());
        }
    }
}
