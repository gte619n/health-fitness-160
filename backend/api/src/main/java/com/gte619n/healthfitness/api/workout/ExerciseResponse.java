package com.gte619n.healthfitness.api.workout;

import com.gte619n.healthfitness.core.exercise.Exercise;
import java.util.List;

// Exercise catalog payload for the preview sheet: demo media + form cues.
public record ExerciseResponse(
    String exerciseId,
    String name,
    String primaryMuscle,
    String equipmentId,
    String demoVideoUrl,
    String demoImageUrl,
    List<String> cues
) {
    public static ExerciseResponse from(Exercise e) {
        return new ExerciseResponse(
            e.exerciseId(),
            e.name(),
            e.primaryMuscle(),
            e.equipmentId(),
            e.demoVideoUrl(),
            e.demoImageUrl(),
            e.cues() == null ? List.of() : e.cues()
        );
    }
}
