package com.gte619n.healthfitness.core.location;

import java.time.Instant;
import java.util.List;

public record Location(
    String userId,
    String locationId,
    String name,
    String description,
    Boolean isDefault,
    List<String> equipmentIds,
    Instant createdAt,
    Instant updatedAt
) {}
