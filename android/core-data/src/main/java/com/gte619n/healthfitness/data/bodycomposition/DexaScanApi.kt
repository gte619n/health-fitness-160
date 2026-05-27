package com.gte619n.healthfitness.data.bodycomposition

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * Retrofit interface for DEXA scan CRUD + inline-edit.
 *
 *   GET    /api/me/dexa/scans              → list
 *   GET    /api/me/dexa/scans/{id}         → detail
 *   PATCH  /api/me/dexa/scans/{id}/field   → inline numeric edit
 *   DELETE /api/me/dexa/scans/{id}         → 204 on success
 *
 * The multipart-SSE upload (POST /api/me/dexa/scans) and the binary PDF
 * download (GET /api/me/dexa/scans/{id}/pdf) bypass Retrofit and go
 * through [com.gte619n.healthfitness.network.sse.MultipartSseClient] and
 * a raw OkHttp call respectively — see [DexaScanRepositoryImpl].
 */
internal interface DexaScanApi {

    @GET("api/me/dexa/scans")
    suspend fun list(): List<DexaScanDto>

    @GET("api/me/dexa/scans/{scanId}")
    suspend fun get(@Path("scanId") scanId: String): DexaScanDto

    @PATCH("api/me/dexa/scans/{scanId}/field")
    suspend fun patchField(
        @Path("scanId") scanId: String,
        @Body body: PatchFieldRequest,
    ): DexaScanDto

    @DELETE("api/me/dexa/scans/{scanId}")
    suspend fun delete(@Path("scanId") scanId: String): Response<Unit>
}
