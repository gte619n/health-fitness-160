package com.gte619n.healthfitness.core.workout;

// Shape of a block within a session. SUPERSET / CIRCUIT are interleaved on the
// client (one set of each exercise per round); the others are straight sets.
public enum BlockType {
    WARMUP,
    STRENGTH,
    SUPERSET,
    CIRCUIT,
    CARDIO,
    COOLDOWN
}
