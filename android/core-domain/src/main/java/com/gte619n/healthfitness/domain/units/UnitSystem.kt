package com.gte619n.healthfitness.domain.units

/**
 * IMPL-AND-01: imperial only for now (matches the web). The enum is
 * introduced ahead of any per-user preference so call sites that
 * "currently always render lb" don't have to change shape later when the
 * preference lands.
 */
enum class UnitSystem { IMPERIAL, METRIC }
