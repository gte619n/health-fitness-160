package com.gte619n.healthfitness.data.workout

import com.gte619n.healthfitness.domain.workout.CompletedSession
import com.gte619n.healthfitness.domain.workout.Exercise
import com.gte619n.healthfitness.domain.workout.LogSetRequest
import com.gte619n.healthfitness.domain.workout.LoggedSet
import com.gte619n.healthfitness.domain.workout.WorkoutSession
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

// Thin wrapper over WorkoutApi exposing suspend functions for the ViewModels.
// Networking errors propagate as exceptions; the ViewModels map them to UI
// error state (same convention as GoalsRepository). 204s surface as null.
@Singleton
class WorkoutRepository @Inject constructor(
    private val api: WorkoutApi,
) {
    /** Today's scheduled session, or null on a rest day (HTTP 204). */
    suspend fun today(): WorkoutSession? = api.today().bodyOr204()

    suspend fun get(sessionId: String): WorkoutSession = api.get(sessionId)

    suspend fun range(from: String, to: String): List<WorkoutSession> = api.range(from, to)

    /** Most recently completed session, or null if the user has none yet. */
    suspend fun latestCompleted(): CompletedSession? = api.latestCompleted().bodyOr204()

    suspend fun start(sessionId: String): WorkoutSession = api.start(sessionId)

    suspend fun logSet(
        sessionId: String,
        setId: String,
        actualReps: Int?,
        actualWeight: Double?,
        completed: Boolean,
    ): LoggedSet = api.logSet(sessionId, setId, LogSetRequest(actualReps, actualWeight, completed))

    suspend fun complete(sessionId: String): CompletedSession = api.complete(sessionId)

    suspend fun summary(sessionId: String): CompletedSession = api.summary(sessionId)

    // Treat a successful empty 204 as null; any non-2xx as an error.
    private fun <T> Response<T>.bodyOr204(): T? =
        if (isSuccessful) body() else throw HttpException(this)
}

// Read-only access to the shared exercise catalog.
@Singleton
class ExerciseRepository @Inject constructor(
    private val api: ExerciseApi,
) {
    suspend fun get(exerciseId: String): Exercise = api.get(exerciseId)
}
