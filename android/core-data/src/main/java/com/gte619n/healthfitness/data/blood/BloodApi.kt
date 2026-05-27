package com.gte619n.healthfitness.data.blood

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the read/CRUD blood endpoints. The PDF upload
 * lives on a separate path (multipart + SSE) and goes through
 * [com.gte619n.healthfitness.network.sse.MultipartSseClient] directly.
 * The PDF download is a binary stream we hit with a raw OkHttp call from
 * the repository — see [BloodTestReportRepositoryImpl.downloadPdf].
 */
internal interface BloodApi {

    @GET("api/me/blood")
    suspend fun listReadings(): List<BloodReadingDto>

    @POST("api/me/blood")
    suspend fun createReading(@Body body: CreateReadingRequestDto): BloodReadingDto

    @DELETE("api/me/blood/{readingId}")
    suspend fun deleteReading(@Path("readingId") id: String): Response<Unit>

    @GET("api/me/blood/reports")
    suspend fun listReports(): List<BloodTestReportDto>

    @GET("api/me/blood/reports/{reportId}")
    suspend fun getReport(@Path("reportId") id: String): BloodTestReportDto

    @DELETE("api/me/blood/reports/{reportId}")
    suspend fun deleteReport(@Path("reportId") id: String): Response<Unit>
}
