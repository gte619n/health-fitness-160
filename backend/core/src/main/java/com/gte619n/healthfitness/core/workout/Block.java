package com.gte619n.healthfitness.core.workout;

import java.util.List;

// An ordered group of exercises within a session. `rounds` is informational
// for straight blocks (1) and the round count for SUPERSET / CIRCUIT blocks.
// `restSecondsAfter` is the rest taken after the whole block.
public record Block(
    String blockId,
    BlockType type,
    String label,
    int rounds,
    int restSecondsAfter,
    List<PrescribedExercise> exercises
) {}
