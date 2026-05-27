package com.gte619n.healthfitness.data.workouts

import com.gte619n.healthfitness.domain.workouts.Amenity
import com.gte619n.healthfitness.domain.workouts.CreateEquipmentRequest
import com.gte619n.healthfitness.domain.workouts.CreateLocationRequest
import com.gte619n.healthfitness.domain.workouts.DayOfWeek
import com.gte619n.healthfitness.domain.workouts.Equipment
import com.gte619n.healthfitness.domain.workouts.EquipmentSpec
import com.gte619n.healthfitness.domain.workouts.EquipmentStatus
import com.gte619n.healthfitness.domain.workouts.HoursSlot
import com.gte619n.healthfitness.domain.workouts.ImageStatus
import com.gte619n.healthfitness.domain.workouts.Location
import com.gte619n.healthfitness.domain.workouts.SpecSchemaTag
import com.gte619n.healthfitness.domain.workouts.UpdateLocationRequest

/**
 * DTO <-> domain projections for the workouts feature.
 *
 * Key shape choices:
 *   - DayOfWeek strings on the wire are uppercase ("MON") matching the
 *     backend's enum names; `valueOf` round-trips them.
 *   - Amenity ids on the wire are lowercase ("lockers"); unknown ids
 *     are dropped rather than crashing the screen.
 *   - Equipment `specs` is a free-form `Map<String, Any?>` on the
 *     wire and is projected into the typed [EquipmentSpec] sealed
 *     class based on the sibling [SpecSchemaTag]. Unknown spec schemas
 *     degrade to [EquipmentSpec.Bodyweight] so future backend
 *     additions don't crash this build (the override sheet will refuse
 *     to edit in that case — see [EquipmentSpec] kdoc).
 */
object WorkoutsMappers {

    fun LocationDto.toDomain(): Location = Location(
        locationId = locationId,
        name = name,
        address = address,
        coverPhotoUrl = coverPhotoUrl,
        is24Hours = is24Hours,
        hours = hours?.mapNotNull { (key, value) ->
            runCatching { DayOfWeek.valueOf(key.uppercase()) }
                .getOrNull()
                ?.let { it to HoursSlot(value.open, value.close) }
        }?.toMap(),
        amenities = amenities.orEmpty().mapNotNull { Amenity.fromId(it) },
        equipmentIds = equipmentIds.orEmpty(),
        equipmentSpecs = equipmentSpecs.orEmpty(),
        isDefault = isDefault,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun CreateLocationRequest.toDto(): CreateLocationDto = CreateLocationDto(
        name = name,
        address = address,
        is24Hours = is24Hours,
        hours = hours?.mapKeys { it.key.name }
            ?.mapValues { HoursSlotDto(it.value.open, it.value.close) },
        amenities = amenities,
        equipmentIds = equipmentIds,
    )

    fun UpdateLocationRequest.toDto(): UpdateLocationDto = UpdateLocationDto(
        name = name,
        address = address,
        is24Hours = is24Hours,
        hours = hours?.mapKeys { it.key.name }
            ?.mapValues { HoursSlotDto(it.value.open, it.value.close) },
        amenities = amenities,
        equipmentIds = equipmentIds,
    )

    fun EquipmentDto.toDomain(): Equipment {
        val schema = runCatching { SpecSchemaTag.valueOf(specSchema.uppercase()) }
            .getOrDefault(SpecSchemaTag.BODYWEIGHT)
        val specs = specsFromMap(schema, this.specs)
        return Equipment(
            equipmentId = equipmentId,
            name = name,
            category = category,
            subcategory = subcategory,
            specSchema = schema,
            specs = specs,
            imageUrl = imageUrl,
            imageStatus = runCatching { ImageStatus.valueOf(imageStatus?.uppercase().orEmpty()) }
                .getOrDefault(ImageStatus.PENDING),
            ownerId = ownerId,
            status = runCatching { EquipmentStatus.valueOf(status?.uppercase().orEmpty()) }
                .getOrDefault(EquipmentStatus.ACTIVE),
            contributorId = contributorId,
            exerciseCount = exerciseCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun CreateEquipmentRequest.toDto(): CreateEquipmentDto = CreateEquipmentDto(
        name = name,
        category = category,
        subcategory = subcategory,
        specSchema = specSchema.name,
        specs = specsToMap(specs),
    )

    // --- spec round-trip helpers ---------------------------------------

    /**
     * Project a free-form spec map into the typed sealed class. Missing
     * or wrongly-typed fields fall back to safe defaults so a corrupt
     * row doesn't kill the gym detail screen.
     */
    fun specsFromMap(schema: SpecSchemaTag, map: Map<String, Any?>?): EquipmentSpec {
        val src = map.orEmpty()
        return when (schema) {
            SpecSchemaTag.SELECTORIZED -> EquipmentSpec.Selectorized(
                minWeight = src.numberOrDefault("minWeight", 0.0),
                maxWeight = src.numberOrDefault("maxWeight", 0.0),
                increment = src.numberOrDefault("increment", 0.0),
            )
            SpecSchemaTag.PLATE_LOADED -> EquipmentSpec.PlateLoaded(
                barWeight = src.numberOrDefault("barWeight", 0.0),
                availablePlates = src.numberListOrEmpty("availablePlates"),
            )
            SpecSchemaTag.BODYWEIGHT -> EquipmentSpec.Bodyweight
            SpecSchemaTag.CABLE -> EquipmentSpec.Cable(
                weightStack = src.numberOrDefault("weightStack", 0.0),
                numStations = src.numberOrDefault("numStations", 0.0).toInt(),
            )
            SpecSchemaTag.CARDIO -> EquipmentSpec.Cardio(
                resistanceLevels = src.numberOrDefault("resistanceLevels", 0.0).toInt(),
                hasIncline = src["hasIncline"] as? Boolean ?: false,
            )
            SpecSchemaTag.WEIGHT_SET -> EquipmentSpec.WeightSet(
                minWeight = src.numberOrNull("minWeight"),
                maxWeight = src.numberOrNull("maxWeight"),
                increment = src.numberOrNull("increment"),
                weights = src.numberListOrNull("weights"),
            )
        }
    }

    /**
     * Inverse — project the typed sealed class back into the raw map
     * the backend expects. Used both for submitting new equipment and
     * for serialising per-location override edits.
     */
    fun specsToMap(spec: EquipmentSpec): Map<String, Any?> = when (spec) {
        is EquipmentSpec.Selectorized -> mapOf(
            "minWeight" to spec.minWeight,
            "maxWeight" to spec.maxWeight,
            "increment" to spec.increment,
        )
        is EquipmentSpec.PlateLoaded -> mapOf(
            "barWeight" to spec.barWeight,
            "availablePlates" to spec.availablePlates,
        )
        EquipmentSpec.Bodyweight -> emptyMap()
        is EquipmentSpec.Cable -> mapOf(
            "weightStack" to spec.weightStack,
            "numStations" to spec.numStations,
        )
        is EquipmentSpec.Cardio -> mapOf(
            "resistanceLevels" to spec.resistanceLevels,
            "hasIncline" to spec.hasIncline,
        )
        is EquipmentSpec.WeightSet -> buildMap {
            spec.minWeight?.let { put("minWeight", it) }
            spec.maxWeight?.let { put("maxWeight", it) }
            spec.increment?.let { put("increment", it) }
            spec.weights?.let { put("weights", it) }
        }
    }

    private fun Map<String, Any?>.numberOrDefault(key: String, default: Double): Double =
        numberOrNull(key) ?: default

    private fun Map<String, Any?>.numberOrNull(key: String): Double? =
        when (val v = this[key]) {
            null -> null
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

    private fun Map<String, Any?>.numberListOrEmpty(key: String): List<Double> =
        numberListOrNull(key) ?: emptyList()

    private fun Map<String, Any?>.numberListOrNull(key: String): List<Double>? {
        val raw = this[key] ?: return null
        if (raw !is List<*>) return null
        return raw.mapNotNull {
            when (it) {
                is Number -> it.toDouble()
                is String -> it.toDoubleOrNull()
                else -> null
            }
        }
    }
}
