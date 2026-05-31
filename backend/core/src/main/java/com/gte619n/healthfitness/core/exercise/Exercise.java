package com.gte619n.healthfitness.core.exercise;

import java.time.Instant;
import java.util.List;

// Shared exercise catalog entry (not user-scoped). Authored upstream; read-only
// to the workout feature. Provides the demonstration media and form cues the
// player overlays. `equipmentId` links to the equipment catalog (IMPL-GYM-001).
// Stored at exercises/{exerciseId}.
public record Exercise(
    String exerciseId,
    String name,
    String primaryMuscle,
    String equipmentId,
    String demoVideoUrl,
    String demoImageUrl,
    List<String> cues,
    Instant createdAt,
    Instant updatedAt
) {}
