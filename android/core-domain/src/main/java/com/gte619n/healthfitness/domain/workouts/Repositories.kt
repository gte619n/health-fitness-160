package com.gte619n.healthfitness.domain.workouts

import java.io.InputStream

/**
 * Backend gym/equipment operations exposed to feature-workouts. All
 * methods return [Result] so the ViewModel layer can `fold` on
 * success/failure without `try/catch` plumbing.
 */
interface LocationRepository {
    suspend fun list(includeInactive: Boolean = false): Result<List<Location>>
    suspend fun get(locationId: String): Result<Location>
    suspend fun create(req: CreateLocationRequest): Result<Location>
    suspend fun update(locationId: String, req: UpdateLocationRequest): Result<Location>
    suspend fun delete(locationId: String): Result<Unit>
    suspend fun setDefault(locationId: String): Result<Unit>

    /**
     * Multipart-uploads the cover photo and returns the updated
     * [Location] (the backend's `POST /api/me/gyms/{id}/photo` endpoint
     * returns LocationResponse).
     */
    suspend fun uploadCoverPhoto(
        locationId: String,
        filename: String,
        mimeType: String,
        source: () -> InputStream,
    ): Result<Location>

    suspend fun deleteCoverPhoto(locationId: String): Result<Location>

    /**
     * Per-location spec override. Sends the full new specs map; the
     * backend folds it into `Location.equipmentSpecs[equipmentId]`.
     */
    suspend fun updateEquipmentSpecs(
        locationId: String,
        equipmentId: String,
        specs: Map<String, Any?>,
    ): Result<Location>
}

/**
 * Catalog + user-submission operations.
 *
 * Note: The backend does not have a dedicated "add equipment to gym"
 * endpoint. Adding/removing equipment from a location is implemented by
 * PATCHing the [Location] with the new [Location.equipmentIds] list —
 * see [LocationRepository.update].
 */
interface EquipmentRepository {
    suspend fun searchCatalog(
        search: String? = null,
        category: String? = null,
        subcategory: String? = null,
    ): Result<List<Equipment>>

    suspend fun get(equipmentId: String): Result<Equipment>
    suspend fun categories(): Result<Map<String, List<String>>>
    suspend fun submit(req: CreateEquipmentRequest): Result<Equipment>
    suspend fun mySubmissions(): Result<List<Equipment>>
    suspend fun deleteSubmission(equipmentId: String): Result<Unit>
}

/**
 * Request payloads. Kept in the domain so the ViewModel can build
 * them without leaking DTOs from `core-data`.
 */
data class CreateLocationRequest(
    val name: String,
    val address: String?,
    val is24Hours: Boolean,
    val hours: Map<DayOfWeek, HoursSlot>?,
    val amenities: List<String>,
    val equipmentIds: List<String> = emptyList(),
)

data class UpdateLocationRequest(
    val name: String? = null,
    val address: String? = null,
    val is24Hours: Boolean? = null,
    val hours: Map<DayOfWeek, HoursSlot>? = null,
    val amenities: List<String>? = null,
    val equipmentIds: List<String>? = null,
)

data class CreateEquipmentRequest(
    val name: String,
    val category: String,
    val subcategory: String,
    val specSchema: SpecSchemaTag,
    val specs: EquipmentSpec,
)
