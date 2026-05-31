package com.gte619n.healthfitness.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private fun set(id: String, reps: Int? = 10, seconds: Int? = null, weight: Double? = 100.0) =
        PrescribedSet(setId = id, targetReps = reps, targetSeconds = seconds, targetWeight = weight)

    private fun session(vararg blocks: Block) = WorkoutSession(
        sessionId = "s", scheduledDate = "2026-05-25", title = "T",
        status = SessionStatus.SCHEDULED, blocks = blocks.toList(),
    )

    @Test
    fun straightBlockAlternatesSetsAndRestsWithoutTrailingRest() {
        val ex = PrescribedExercise("e1", "Row", listOf(set("a"), set("b"), set("c")), restSecondsBetweenSets = 90)
        val steps = SessionEngine.steps(session(Block("b1", BlockType.STRENGTH, exercises = listOf(ex))))

        // PerformSet, Rest, PerformSet, Rest, PerformSet  — no trailing rest.
        assertEquals(5, steps.size)
        assertTrue(steps[0] is PlayerStep.PerformSet)
        assertTrue(steps[1] is PlayerStep.Rest)
        assertTrue(steps[2] is PlayerStep.PerformSet)
        assertTrue(steps[3] is PlayerStep.Rest)
        assertTrue(steps[4] is PlayerStep.PerformSet)
        assertEquals(90, (steps[1] as PlayerStep.Rest).seconds)
        assertEquals("b", (steps[1] as PlayerStep.Rest).upNext.set.setId)
        assertEquals(3, (steps[0] as PlayerStep.PerformSet).setCount)
        assertEquals(2, (steps[2] as PlayerStep.PerformSet).setOrdinal)
    }

    @Test
    fun supersetCyclesOneSetOfEachExercisePerRound() {
        val a = PrescribedExercise("eA", "A", listOf(set("a1"), set("a2"), set("a3")), restSecondsBetweenSets = 60)
        val b = PrescribedExercise("eB", "B", listOf(set("b1"), set("b2"), set("b3")), restSecondsBetweenSets = 60)
        val steps = SessionEngine.steps(session(Block("sup", BlockType.SUPERSET, rounds = 3, exercises = listOf(a, b))))

        // A1, B1, Rest, A2, B2, Rest, A3, B3  (back-to-back within a round).
        val performIds = steps.filterIsInstance<PlayerStep.PerformSet>().map { it.set.setId }
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3", "b3"), performIds)

        // Exactly 2 rests, both between rounds, none trailing.
        assertEquals(2, steps.count { it is PlayerStep.Rest })
        assertTrue(steps.last() is PlayerStep.PerformSet)
        // round numbers track the cycle.
        assertEquals(2, (steps.first { it is PlayerStep.PerformSet && it.set.setId == "a2" } as PlayerStep.PerformSet).round)
    }

    @Test
    fun blockBoundaryInsertsRestUsingAfterRest() {
        val warm = PrescribedExercise("w", "Warm", listOf(set("w1")), restSecondsBetweenSets = 0)
        val work = PrescribedExercise("k", "Work", listOf(set("k1")), restSecondsBetweenSets = 0)
        val steps = SessionEngine.steps(session(
            Block("b1", BlockType.WARMUP, restSecondsAfter = 45, exercises = listOf(warm)),
            Block("b2", BlockType.STRENGTH, restSecondsAfter = 0, exercises = listOf(work)),
        ))

        // w1, Rest(45 -> k1), k1
        assertEquals(3, steps.size)
        val rest = steps[1] as PlayerStep.Rest
        assertEquals(45, rest.seconds)
        assertEquals("k1", rest.upNext.set.setId)
        assertTrue(steps.last() is PlayerStep.PerformSet)
    }

    @Test
    fun timedSetCarriesSecondsNotReps() {
        val plank = PrescribedExercise("p", "Plank", listOf(set("p1", reps = null, seconds = 45, weight = null)))
        val steps = SessionEngine.steps(session(Block("b", BlockType.STRENGTH, exercises = listOf(plank))))
        val s = (steps[0] as PlayerStep.PerformSet).set
        assertTrue(s.isTimed)
        assertEquals(45, s.targetSeconds)
        assertEquals(null, s.targetReps)
    }

    @Test
    fun resumeIndexSkipsCompletedSets() {
        val ex = PrescribedExercise("e", "E", listOf(set("a"), set("b"), set("c")), restSecondsBetweenSets = 30)
        val steps = SessionEngine.steps(session(Block("b", BlockType.STRENGTH, exercises = listOf(ex))))
        val logged = mapOf("a" to LoggedSet("a", 10, 100.0, completed = true))

        // a done -> resume at the rest? No: resumeIndex returns the first
        // PerformSet not completed, which is "b" at index 2.
        assertEquals(2, SessionEngine.resumeIndex(steps, logged))
        // fully logged -> resume past the end (finish).
        val allDone = mapOf(
            "a" to LoggedSet("a", 10, 100.0, completed = true),
            "b" to LoggedSet("b", 10, 100.0, completed = true),
            "c" to LoggedSet("c", 10, 100.0, completed = true),
        )
        assertEquals(steps.size, SessionEngine.resumeIndex(steps, allDone))
    }

    @Test
    fun volumeSumsCompletedRepsTimesWeight() {
        val logged = mapOf(
            "a" to LoggedSet("a", 10, 135.0, completed = true),
            "b" to LoggedSet("b", 8, 135.0, completed = true),
            "c" to LoggedSet("c", 5, 135.0, completed = false), // not counted
            "d" to LoggedSet("d", null, null, completed = true), // bodyweight, no volume
        )
        assertEquals(10 * 135.0 + 8 * 135.0, SessionEngine.volume(logged), 0.001)
    }
}
