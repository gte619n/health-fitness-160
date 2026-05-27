package com.gte619n.healthfitness.data.dashboard

import retrofit2.http.GET

/**
 * Retrofit service for the three dashboard endpoints. Internal so the
 * Compose layer can't reach for it directly; everything flows through
 * the repositories.
 */
internal interface DashboardApi {

    /** Full body-composition history (server returns all readings). */
    @GET("api/me/body-composition")
    suspend fun bodyComposition(): List<BodyCompositionDto>

    /** Full blood-reading history. */
    @GET("api/me/blood")
    suspend fun bloodReadings(): List<BloodReadingDto>

    /** All scheduled doses for today (sorted by time window on the backend). */
    @GET("api/me/medications/today")
    suspend fun todaysDoses(): List<TodaysDoseDto>
}
