package com.gte619n.healthfitness.domain.workout

// One step in the player's flattened timeline.
sealed interface PlayerStep {
    val blockId: String

    data class PerformSet(
        override val blockId: String,
        val exercise: PrescribedExercise,
        val set: PrescribedSet,
        val round: Int,        // 1-based; meaningful for supersets/circuits
        val setOrdinal: Int,   // "Set X of Y" within this exercise
        val setCount: Int,
    ) : PlayerStep

    data class Rest(
        override val blockId: String,
        val seconds: Int,
        val upNext: PerformSet,
    ) : PlayerStep
}

// Flattens an authored session into the ordered list of steps the player walks.
// Pure Kotlin (no Android deps) so the superset/circuit ordering — the trickiest
// part of the experience — is unit-testable in isolation.
object SessionEngine {

    // Internal: a set to perform plus the rest (seconds) to take immediately
    // after it. A rest is only materialized into the step list when a next set
    // exists, so the timeline never ends on a rest.
    private data class Unit(val step: PlayerStep.PerformSet, val restAfter: Int)

    fun steps(session: WorkoutSession): List<PlayerStep> {
        val units = mutableListOf<Unit>()

        for (block in session.blocks) {
            val blockUnits = if (block.isInterleaved) {
                interleavedUnits(block)
            } else {
                straightUnits(block)
            }
            if (blockUnits.isEmpty()) continue
            // The last set of a block rests for the block's after-rest before the
            // next block begins, overriding any intra-block rest.
            blockUnits[blockUnits.lastIndex] = blockUnits.last().copy(restAfter = block.restSecondsAfter)
            units += blockUnits
        }

        val out = mutableListOf<PlayerStep>()
        units.forEachIndexed { i, unit ->
            out += unit.step
            val next = units.getOrNull(i + 1)?.step
            if (unit.restAfter > 0 && next != null) {
                out += PlayerStep.Rest(unit.step.blockId, unit.restAfter, next)
            }
        }
        return out
    }

    // Straight sets: every set of each exercise in order, resting between sets
    // (and between exercises) by the exercise's between-set rest.
    private fun straightUnits(block: Block): MutableList<Unit> {
        val units = mutableListOf<Unit>()
        block.exercises.forEachIndexed { ei, exercise ->
            val count = exercise.prescribedSets.size
            exercise.prescribedSets.forEachIndexed { si, set ->
                val step = PlayerStep.PerformSet(
                    blockId = block.blockId,
                    exercise = exercise,
                    set = set,
                    round = 1,
                    setOrdinal = si + 1,
                    setCount = count,
                )
                val isLastSetOfExercise = si == count - 1
                val isLastExercise = ei == block.exercises.lastIndex
                val restAfter = if (isLastSetOfExercise && isLastExercise) 0 else exercise.restSecondsBetweenSets
                units += Unit(step, restAfter)
            }
        }
        return units
    }

    // Supersets / circuits: one set of each exercise per round, back-to-back,
    // resting only after a full round completes.
    private fun interleavedUnits(block: Block): MutableList<Unit> {
        val units = mutableListOf<Unit>()
        val rounds = block.exercises.maxOfOrNull { it.prescribedSets.size } ?: 0
        val betweenRoundRest = block.exercises.firstOrNull()?.restSecondsBetweenSets ?: 0
        for (r in 0 until rounds) {
            val inRound = block.exercises.filter { r < it.prescribedSets.size }
            inRound.forEachIndexed { idx, exercise ->
                val step = PlayerStep.PerformSet(
                    blockId = block.blockId,
                    exercise = exercise,
                    set = exercise.prescribedSets[r],
                    round = r + 1,
                    setOrdinal = r + 1,
                    setCount = exercise.prescribedSets.size,
                )
                val isLastInRound = idx == inRound.lastIndex
                val restAfter = if (isLastInRound && r < rounds - 1) betweenRoundRest else 0
                units += Unit(step, restAfter)
            }
        }
        return units
    }

    /** First step index whose set has not been completed; [steps].size when done. */
    fun resumeIndex(steps: List<PlayerStep>, logged: Map<String, LoggedSet>): Int {
        steps.forEachIndexed { i, step ->
            if (step is PlayerStep.PerformSet) {
                val log = logged[step.set.setId]
                if (log == null || !log.completed) return i
            }
        }
        return steps.size
    }

    /** Running tonnage from completed logged sets (Σ reps × weight). */
    fun volume(logged: Map<String, LoggedSet>): Double =
        logged.values.sumOf { l ->
            if (l.completed && l.actualReps != null && l.actualWeight != null) {
                l.actualReps * l.actualWeight
            } else {
                0.0
            }
        }
}
