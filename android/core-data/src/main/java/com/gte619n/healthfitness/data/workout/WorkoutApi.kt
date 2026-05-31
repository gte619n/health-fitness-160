package com.gte619n.healthfitness.data.workout

import com.gte619n.healthfitness.domain.workout.CompletedSession
import com.gte619n.healthfitness.domain.workout.Exercise
import com.gte619n.healthfitness.domain.workout.LogSetRequest
import com.gte619n.healthfitness.domain.workout.LoggedSet
import com.gte619n.healthfitness.domain.workout.WorkoutSession
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Guided-workout session REST surface (IMPL-WORKOUT-001). Base path is
// /api/me/workouts/sessions. Sessions are authored upstream, so there is no
// create call — only read + execute + finalize. `today` and `latest-completed`
// return 204 (no body) when there's nothing, so they use Response<> to detect it.
interface WorkoutApi {

    @GET("api/me/workouts/sessions/today")
    suspend fun today(): Response<WorkoutSession>

    @GET("api/me/workouts/sessions/{id}")
    suspend fun get(@Path("id") sessionId: String): WorkoutSession

    @GET("api/me/workouts/sessions")
    suspend fun range(@Query("from") from: String, @Query("to") to: String): List<WorkoutSession>

    @GET("api/me/workouts/sessions/latest-completed")
    suspend fun latestCompleted(): Response<CompletedSession>

    @POST("api/me/workouts/sessions/{id}/start")
    suspend fun start(@Path("id") sessionId: String): WorkoutSession

    @PATCH("api/me/workouts/sessions/{id}/sets/{setId}")
    suspend fun logSet(
        @Path("id") sessionId: String,
        @Path("setId") setId: String,
        @Body body: LogSetRequest,
    ): LoggedSet

    @POST("api/me/workouts/sessions/{id}/complete")
    suspend fun complete(@Path("id") sessionId: String): CompletedSession

    @GET("api/me/workouts/sessions/{id}/summary")
    suspend fun summary(@Path("id") sessionId: String): CompletedSession
}

// Shared exercise catalog (demo media + cues) for the player's preview sheet.
interface ExerciseApi {
    @GET("api/exercises/{id}")
    suspend fun get(@Path("id") exerciseId: String): Exercise
}
