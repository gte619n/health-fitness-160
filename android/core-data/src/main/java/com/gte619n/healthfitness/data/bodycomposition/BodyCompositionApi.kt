package com.gte619n.healthfitness.data.bodycomposition

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Read endpoint for the Google Health-backed body composition collection.
 * Matches the backend's `BodyCompositionController` exactly.
 *
 * Internal — the Compose layer always goes through the repositories.
 */
internal interface BodyCompositionApi {

    @GET("api/me/body-composition")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("metric") metric: String? = null,
    ): List<BodyCompositionReadingDto>
}
