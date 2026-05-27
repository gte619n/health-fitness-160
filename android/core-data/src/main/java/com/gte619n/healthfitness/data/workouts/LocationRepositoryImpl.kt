package com.gte619n.healthfitness.data.workouts

import com.gte619n.healthfitness.data.workouts.WorkoutsMappers.toDomain
import com.gte619n.healthfitness.data.workouts.WorkoutsMappers.toDto
import com.gte619n.healthfitness.domain.workouts.CreateLocationRequest
import com.gte619n.healthfitness.domain.workouts.Location
import com.gte619n.healthfitness.domain.workouts.LocationRepository
import com.gte619n.healthfitness.domain.workouts.UpdateLocationRequest
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.upload.MultipartUploadClient
import com.gte619n.healthfitness.network.upload.PendingUpload
import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the gym list / detail / mutation surface.
 *
 *   - [LocationApi] for JSON CRUD.
 *   - [MultipartUploadClient] for the cover-photo multipart upload —
 *     the same helper introduced in IMPL-AND-06 (`POST /photo` returns
 *     the updated LocationResponse).
 */
@Singleton
internal class LocationRepositoryImpl @Inject constructor(
    private val api: LocationApi,
    private val multipart: MultipartUploadClient,
    private val baseUrl: BackendBaseUrlProvider,
    private val moshi: Moshi,
) : LocationRepository {

    private val locationAdapter = moshi.adapter(LocationDto::class.java)

    override suspend fun list(includeInactive: Boolean): Result<List<Location>> = runCatching {
        api.list(include = if (includeInactive) "inactive" else null)
            .map { it.toDomain() }
    }

    override suspend fun get(locationId: String): Result<Location> = runCatching {
        api.get(locationId).toDomain()
    }

    override suspend fun create(req: CreateLocationRequest): Result<Location> = runCatching {
        api.create(req.toDto()).toDomain()
    }

    override suspend fun update(
        locationId: String,
        req: UpdateLocationRequest,
    ): Result<Location> = runCatching {
        api.update(locationId, req.toDto()).toDomain()
    }

    override suspend fun delete(locationId: String): Result<Unit> = runCatching {
        val response = api.delete(locationId)
        if (!response.isSuccessful) {
            throw IOException("Delete failed: HTTP ${response.code()}")
        }
    }

    override suspend fun setDefault(locationId: String): Result<Unit> = runCatching {
        val response = api.setDefault(locationId)
        if (!response.isSuccessful) {
            throw IOException("Set-default failed: HTTP ${response.code()}")
        }
    }

    override suspend fun uploadCoverPhoto(
        locationId: String,
        filename: String,
        mimeType: String,
        source: () -> InputStream,
    ): Result<Location> {
        val url = (baseUrl.baseUrl.trimEnd('/') + "/api/me/gyms/$locationId/photo").toHttpUrl()
        return multipart.upload(
            url = url,
            upload = PendingUpload(
                filename = filename,
                mimeType = mimeType,
                source = source,
            ),
            fieldName = "file",
        ) { body ->
            val text = body.string()
            val dto = locationAdapter.fromJson(text)
                ?: throw IOException("Empty LocationResponse on cover-photo upload")
            dto.toDomain()
        }
    }

    override suspend fun deleteCoverPhoto(locationId: String): Result<Location> = runCatching {
        api.deleteCoverPhoto(locationId).toDomain()
    }

    override suspend fun updateEquipmentSpecs(
        locationId: String,
        equipmentId: String,
        specs: Map<String, Any?>,
    ): Result<Location> = runCatching {
        api.updateEquipmentSpecs(
            id = locationId,
            equipmentId = equipmentId,
            body = UpdateEquipmentSpecsDto(specs),
        ).toDomain()
    }
}
