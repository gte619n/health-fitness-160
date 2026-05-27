package com.gte619n.healthfitness.data.dashboard

import retrofit2.http.GET

/**
 * Retrofit service for the dashboard's remaining own endpoints
 * (blood readings + today's doses). The body-composition endpoint
 * moved to `data.bodycomposition.BodyCompositionApi` in Round 2
 * Stage C — the canonical repository fetches readings itself.
 *
 * Internal so the Compose layer can't reach for it directly;
 * everything flows through the repositories.
 */
internal interface DashboardApi {

    /** Full blood-reading history. */
    @GET("api/me/blood")
    suspend fun bloodReadings(): List<BloodReadingDto>

    /** All scheduled doses for today (sorted by time window on the backend). */
    @GET("api/me/medications/today")
    suspend fun todaysDoses(): List<TodaysDoseDto>
}
