package com.gte619n.healthfitness.domain.workout

// Guided-workout domain models (IMPL-WORKOUT-001). These mirror the backend
// JSON contract exactly — see backend api/workout/*Response records — and
// double as the Moshi wire types. Following the Goals convention, dates and
// timestamps are kept as ISO-8601 strings and parsed in the UI layer; enums
// match the backend enum names so Moshi's default enum adapter round-trips.

enum class SessionStatus { SCHEDULED, IN_PROGRESS, COMPLETED, SKIPPED }

enum class BlockType { WARMUP, STRENGTH, SUPERSET, CIRCUIT, CARDIO, COOLDOWN }

enum class WeightUnit { LB, KG }

/** A single prescribed set. Exactly one of [targetReps] / [targetSeconds] is set. */
data class PrescribedSet(
    val setId: String,
    val targetReps: Int? = null,
    val targetSeconds: Int? = null,
    val targetWeight: Double? = null,
    val weightUnit: WeightUnit = WeightUnit.LB,
) {
    val isTimed: Boolean get() = targetSeconds != null
}

data class PrescribedExercise(
    val exerciseId: String,
    val name: String,
    val prescribedSets: List<PrescribedSet> = emptyList(),
    val restSecondsBetweenSets: Int = 0,
    val notes: String? = null,
)

data class Block(
    val blockId: String,
    val type: BlockType,
    val label: String? = null,
    val rounds: Int = 1,
    val restSecondsAfter: Int = 0,
    val exercises: List<PrescribedExercise> = emptyList(),
) {
    val isInterleaved: Boolean get() = type == BlockType.SUPERSET || type == BlockType.CIRCUIT
}

/** What the user actually did for a prescribed set. */
data class LoggedSet(
    val setId: String,
    val actualReps: Int? = null,
    val actualWeight: Double? = null,
    val completed: Boolean = false,
    val loggedAt: String? = null,
)

data class ExerciseResult(
    val name: String,
    val topSet: String,
    val volume: Double,
)

data class SessionSummary(
    val durationSeconds: Int,
    val totalVolume: Double,
    val setsCompleted: Int,
    val setsPrescribed: Int,
    val estimatedCalories: Int,
    val perExercise: List<ExerciseResult> = emptyList(),
    val aiRecap: String? = null,
)

/** Planned + live session. `blocks` are authored upstream; the rest accrues. */
data class WorkoutSession(
    val sessionId: String,
    val scheduledDate: String,
    val title: String,
    val focus: String? = null,
    val status: SessionStatus,
    val estimatedMinutes: Int = 0,
    val blocks: List<Block> = emptyList(),
    val loggedSets: Map<String, LoggedSet> = emptyMap(),
    val startedAt: String? = null,
    val completedAt: String? = null,
    val summary: SessionSummary? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val exerciseCount: Int get() = blocks.sumOf { it.exercises.size }
}

/** Completed-session headline + summary (mirrors CompletedSessionResponse). */
data class CompletedSession(
    val sessionId: String,
    val title: String,
    val focus: String? = null,
    val scheduledDate: String,
    val completedAt: String? = null,
    val summary: SessionSummary,
)

/** Shared exercise catalog entry — demo media + cues (mirrors ExerciseResponse). */
data class Exercise(
    val exerciseId: String,
    val name: String,
    val primaryMuscle: String? = null,
    val equipmentId: String? = null,
    val demoVideoUrl: String? = null,
    val demoImageUrl: String? = null,
    val cues: List<String> = emptyList(),
)

/** Body for PATCH .../sets/{setId}. */
data class LogSetRequest(
    val actualReps: Int? = null,
    val actualWeight: Double? = null,
    val completed: Boolean? = null,
)
