package com.gte619n.healthfitness.core.user;

import java.time.Instant;

public record User(
    String userId,
    String email,
    String displayName,
    GoogleHealthConnection googleHealth,
    Instant createdAt,
    Instant updatedAt
) {}
