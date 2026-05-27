package com.gte619n.healthfitness.data.workouts

import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * Wire-format mirrors of the backend's LocationResponse + EquipmentResponse.
 *
 * Note the equipment `specs` field is intentionally a raw
 * `Map<String, Any?>` — the backend stores per-category specs as
 * `Map<String, Object>` and the discriminator (`specSchema`) lives in
 * a sibling field rather than inside the spec object. We do the typed
 * projection to [com.gte619n.healthfitness.domain.workouts.EquipmentSpec]
 * in [WorkoutsMappers], not via Moshi's polymorphic factory.
 */
@JsonClass(generateAdapter = false)
data class HoursSlotDto(val open: String, val close: String)

@JsonClass(generateAdapter = false)
data class LocationDto(
    val locationId: String,
    val name: String,
    val address: String?,
    val coverPhotoUrl: String?,
    val is24Hours: Boolean,
    /** Keys are uppercase 3-letter day names ("MON"). Null when [is24Hours] is true. */
    val hours: Map<String, HoursSlotDto>?,
    val amenities: List<String>?,
    val equipmentIds: List<String>?,
    val equipmentSpecs: Map<String, Map<String, Any?>>?,
    val isDefault: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JsonClass(generateAdapter = false)
data class EquipmentDto(
    val equipmentId: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val specSchema: String,
    val specs: Map<String, Any?>?,
    val imageUrl: String?,
    val imageStatus: String?,
    val ownerId: String?,
    val status: String?,
    val contributorId: String?,
    val exerciseCount: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** POST /api/me/gyms */
@JsonClass(generateAdapter = false)
data class CreateLocationDto(
    val name: String,
    val address: String?,
    val is24Hours: Boolean,
    val hours: Map<String, HoursSlotDto>?,
    val amenities: List<String>?,
    val equipmentIds: List<String>?,
)

/** PATCH /api/me/gyms/{id} — every field optional so partial updates work. */
@JsonClass(generateAdapter = false)
data class UpdateLocationDto(
    val name: String?,
    val address: String?,
    val is24Hours: Boolean?,
    val hours: Map<String, HoursSlotDto>?,
    val amenities: List<String>?,
    val equipmentIds: List<String>?,
)

/** PATCH /api/me/gyms/{locationId}/equipment/{equipmentId} */
@JsonClass(generateAdapter = false)
data class UpdateEquipmentSpecsDto(val specs: Map<String, Any?>)

/** POST /api/me/equipment */
@JsonClass(generateAdapter = false)
data class CreateEquipmentDto(
    val name: String,
    val category: String,
    val subcategory: String,
    val specSchema: String,
    val specs: Map<String, Any?>,
)

/** GET /api/equipment/categories */
@JsonClass(generateAdapter = false)
data class CategoryTreeDto(val categories: Map<String, List<String>>)
