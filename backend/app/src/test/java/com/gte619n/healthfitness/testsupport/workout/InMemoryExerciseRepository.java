package com.gte619n.healthfitness.testsupport.workout;

import com.gte619n.healthfitness.core.exercise.Exercise;
import com.gte619n.healthfitness.core.exercise.ExerciseRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryExerciseRepository implements ExerciseRepository {

    private final Map<String, Exercise> byId = new ConcurrentHashMap<>();

    public void clear() {
        byId.clear();
    }

    public void put(Exercise exercise) {
        byId.put(exercise.exerciseId(), exercise);
    }

    @Override
    public Optional<Exercise> findById(String exerciseId) {
        return Optional.ofNullable(byId.get(exerciseId));
    }
}
