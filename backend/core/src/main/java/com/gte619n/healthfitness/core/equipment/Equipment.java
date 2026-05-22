package com.gte619n.healthfitness.core.equipment;

import java.time.Instant;
import java.util.Map;

public record Equipment(
    String equipmentId,
    String ownerId,
    String name,
    String category,
    Map<String, Object> specs,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {}
