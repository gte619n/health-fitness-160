package com.gte619n.healthfitness.data.workouts

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for `/api/me/gyms`. The PATCH-based update path
 * matches the backend; equipment add/remove is done by PATCHing the
 * location with the new `equipmentIds[]` list, not via a separate
 * endpoint.
 *
 * Cover-photo upload is NOT here — the multipart POST is handled by
 * `MultipartUploadClient` directly in [LocationRepositoryImpl].
 */
interface LocationApi {
    @GET("api/me/gyms")
    suspend fun list(@Query("include") include: String? = null): List<LocationDto>

    @GET("api/me/gyms/{id}")
    suspend fun get(@Path("id") id: String): LocationDto

    @POST("api/me/gyms")
    suspend fun create(@Body body: CreateLocationDto): LocationDto

    @PATCH("api/me/gyms/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateLocationDto): LocationDto

    @DELETE("api/me/gyms/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @POST("api/me/gyms/{id}/default")
    suspend fun setDefault(@Path("id") id: String): Response<Unit>

    @DELETE("api/me/gyms/{id}/photo")
    suspend fun deleteCoverPhoto(@Path("id") id: String): LocationDto

    @PATCH("api/me/gyms/{id}/equipment/{equipmentId}")
    suspend fun updateEquipmentSpecs(
        @Path("id") id: String,
        @Path("equipmentId") equipmentId: String,
        @Body body: UpdateEquipmentSpecsDto,
    ): LocationDto
}

/**
 * Retrofit binding for `/api/equipment` (catalog) and `/api/me/equipment`
 * (user submissions).
 */
interface EquipmentApi {
    @GET("api/equipment")
    suspend fun search(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("subcategory") sub: String? = null,
    ): List<EquipmentDto>

    @GET("api/equipment/{id}")
    suspend fun get(@Path("id") id: String): EquipmentDto

    @GET("api/equipment/categories")
    suspend fun categories(): CategoryTreeDto

    @POST("api/me/equipment")
    suspend fun submit(@Body body: CreateEquipmentDto): EquipmentDto

    @GET("api/me/equipment")
    suspend fun mySubmissions(): List<EquipmentDto>

    @DELETE("api/me/equipment/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
