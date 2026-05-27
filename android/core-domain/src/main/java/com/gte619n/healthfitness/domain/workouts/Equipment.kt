package com.gte619n.healthfitness.domain.workouts

import java.time.Instant

/**
 * The shape of an equipment's spec depends on its category. The backend
 * stores `specs` as a free-form `Map<String,Object>` keyed by the
 * sibling [SpecSchemaTag], and the Android client mirrors that wire
 * format inside DTOs. At the domain boundary we project that map into a
 * typed sealed-class hierarchy so the UI's per-category forms can do
 * exhaustive `when` matching.
 */
enum class SpecSchemaTag { SELECTORIZED, PLATE_LOADED, BODYWEIGHT, CABLE, CARDIO, WEIGHT_SET }

enum class ImageStatus { PENDING, GENERATED, FAILED }

enum class EquipmentStatus { ACTIVE, PENDING_REVIEW, REJECTED }

/**
 * Typed view of the per-category `specs` payload. The mapper in
 * `core-data/workouts/` projects from `Map<String,Any?>` into one of
 * these subclasses based on the sibling [SpecSchemaTag], and back the
 * other way when the user submits new equipment.
 *
 * Bodyweight has no fields — kept as a `data object` so the
 * polymorphic mapping can default-fall-back to it for unknown future
 * schemas without crashing.
 */
sealed class EquipmentSpec {
    abstract val tag: SpecSchemaTag

    data class Selectorized(
        val minWeight: Double,
        val maxWeight: Double,
        val increment: Double,
    ) : EquipmentSpec() {
        override val tag = SpecSchemaTag.SELECTORIZED
    }

    data class PlateLoaded(
        val barWeight: Double,
        val availablePlates: List<Double>,
    ) : EquipmentSpec() {
        override val tag = SpecSchemaTag.PLATE_LOADED
    }

    data object Bodyweight : EquipmentSpec() {
        override val tag = SpecSchemaTag.BODYWEIGHT
    }

    data class Cable(
        val weightStack: Double,
        val numStations: Int,
    ) : EquipmentSpec() {
        override val tag = SpecSchemaTag.CABLE
    }

    data class Cardio(
        val resistanceLevels: Int,
        val hasIncline: Boolean,
    ) : EquipmentSpec() {
        override val tag = SpecSchemaTag.CARDIO
    }

    /**
     * Either a min/max/increment range or an explicit weights list, or
     * both. All fields nullable so the form can hold partial input.
     */
    data class WeightSet(
        val minWeight: Double?,
        val maxWeight: Double?,
        val increment: Double?,
        val weights: List<Double>?,
    ) : EquipmentSpec() {
        override val tag = SpecSchemaTag.WEIGHT_SET
    }
}

/**
 * A catalog or user-submitted equipment.
 *
 *  - [ownerId] is null for catalog rows, the user's id for submissions.
 *  - [status] is `ACTIVE` for catalog, `PENDING_REVIEW` for fresh
 *    submissions.
 *  - [exerciseCount] is null until exercises ship; the EquipmentRow
 *    composable tolerates null.
 */
data class Equipment(
    val equipmentId: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val specSchema: SpecSchemaTag,
    val specs: EquipmentSpec,
    val imageUrl: String?,
    val imageStatus: ImageStatus,
    val ownerId: String?,
    val status: EquipmentStatus,
    val contributorId: String?,
    val exerciseCount: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Per-location override for one piece of equipment. Carries the raw
 * map shape used by the backend so partial overrides (e.g., only
 * `maxWeight` differs from catalog default) round-trip without
 * synthesising defaults.
 */
data class EquipmentOverride(
    val equipmentId: String,
    val specs: Map<String, Any?>,
)
