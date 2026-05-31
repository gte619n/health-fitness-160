package com.gte619n.healthfitness.workout;

import com.gte619n.healthfitness.api.workout.ExerciseResponse;
import com.gte619n.healthfitness.core.exercise.ExerciseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Read access to the shared exercise catalog (demo media + cues) for the
// player's exercise-preview sheet. Catalog is global, not user-scoped.
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseRepository repository;

    public ExerciseController(ExerciseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{exerciseId}")
    public ExerciseResponse get(@PathVariable String exerciseId) {
        return repository.findById(exerciseId)
            .map(ExerciseResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
