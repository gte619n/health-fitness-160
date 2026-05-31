package com.gte619n.healthfitness.persistence.exercise;

import static com.gte619n.healthfitness.persistence.FirestoreMapper.toInstant;

import com.gte619n.healthfitness.core.exercise.Exercise;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

// Firestore-backed exercise catalog. Documents live at exercises/{exerciseId}
// (global, not user-scoped). Read-only here — authoring happens upstream.
@Repository
@ConditionalOnProperty(name = "app.persistence.firestore-enabled", havingValue = "true", matchIfMissing = true)
public class ExerciseRepository implements com.gte619n.healthfitness.core.exercise.ExerciseRepository {

    private static final String COLLECTION = "exercises";

    private final Firestore firestore;

    public ExerciseRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Optional<Exercise> findById(String exerciseId) {
        DocumentSnapshot snapshot = await(firestore.collection(COLLECTION).document(exerciseId).get());
        if (!snapshot.exists()) return Optional.empty();
        return Optional.of(toExercise(snapshot));
    }

    private static Exercise toExercise(DocumentSnapshot doc) {
        return new Exercise(
            doc.getId(),
            doc.getString("name"),
            doc.getString("primaryMuscle"),
            doc.getString("equipmentId"),
            doc.getString("demoVideoUrl"),
            doc.getString("demoImageUrl"),
            toStringList(doc.get("cues")),
            toInstant(doc.get("createdAt")),
            toInstant(doc.get("updatedAt"))
        );
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) out.add(item.toString());
        }
        return out;
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
