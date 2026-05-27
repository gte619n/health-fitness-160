package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.buildSnapshot
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionMetric
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Unit tests for [BodyCompositionMappers.buildSnapshot]. Verifies the
 * latest-of-each-metric pick, the 7d/90d deltas, and the lean-mass
 * derivation fall-back when the API doesn't surface LEAN_MASS_KG
 * directly.
 */
class BodyCompositionSnapshotTest {

    private val now: Instant = Instant.parse("2026-05-27T12:00:00Z")

    @Test fun `empty input produces all-null snapshot`() {
        val snap = buildSnapshot(emptyList(), now = now)
        assertNull(snap.latestWeightKg)
        assertNull(snap.latestBodyFatPercent)
        assertNull(snap.latestLeanMassKg)
        assertNull(snap.latestBmi)
        assertNull(snap.sevenDayDeltaKg)
        assertNull(snap.ninetyDayDeltaKg)
        assertTrue(snap.series90d.isEmpty())
    }

    @Test fun `latest weight + 7d delta computed from points spanning the window`() {
        val points = listOf(
            weight("a", 82.0, now.minusSeconds(8 * 86_400)),
            weight("b", 81.0, now.minusSeconds(6 * 86_400)),
            weight("c", 80.0, now.minusSeconds(1 * 86_400)),
        )
        val snap = buildSnapshot(points, now = now)
        assertEquals(80.0, snap.latestWeightKg!!, 0.001)
        // 7d anchor is the point at or before (now - 7d) = the "a" point at -8d.
        assertEquals(80.0 - 82.0, snap.sevenDayDeltaKg!!, 0.001)
        assertEquals(3, snap.series90d.size)
    }

    @Test fun `90d delta falls back to mean when no 90-day anchor exists`() {
        val points = listOf(
            weight("a", 80.0, now.minusSeconds(60L * 86_400)),
            weight("b", 79.5, now.minusSeconds(30L * 86_400)),
            weight("c", 79.0, now.minusSeconds(1L * 86_400)),
        )
        val snap = buildSnapshot(points, now = now)
        // mean = 79.5; delta = 79.0 - 79.5 = -0.5
        val delta = snap.ninetyDayDeltaKg!!
        assertTrue("expected -0.5 ish, got $delta", abs(delta - (-0.5)) < 0.001)
    }

    @Test fun `lean mass derived from weight x (1 - bf-pct) when paired within 6h`() {
        val sample = now.minusSeconds(2 * 3600)
        val points = listOf(
            weight("a", 80.0, sample),
            BodyCompositionPoint("b", BodyCompositionMetric.BODY_FAT_PERCENT, 20.0, sample, null, null),
        )
        val snap = buildSnapshot(points, now = now)
        assertNotNull(snap.latestLeanMassKg)
        assertEquals(80.0 * 0.80, snap.latestLeanMassKg!!, 0.001)
    }

    @Test fun `lean mass from API takes precedence over derived`() {
        val sample = now.minusSeconds(3600)
        val points = listOf(
            weight("a", 80.0, sample),
            BodyCompositionPoint("b", BodyCompositionMetric.BODY_FAT_PERCENT, 20.0, sample, null, null),
            BodyCompositionPoint("c", BodyCompositionMetric.LEAN_MASS_KG, 65.0, sample, null, null),
        )
        val snap = buildSnapshot(points, now = now)
        assertEquals(65.0, snap.latestLeanMassKg!!, 0.001)
    }

    @Test fun `series90d excludes points outside the 90-day window`() {
        val points = listOf(
            weight("old", 80.0, now.minusSeconds(120L * 86_400)),
            weight("recent", 79.0, now.minusSeconds(5L * 86_400)),
        )
        val snap = buildSnapshot(points, now = now)
        assertEquals(1, snap.series90d.size)
        assertEquals("recent", snap.series90d.first().recordId)
    }

    private fun weight(id: String, kg: Double, at: Instant) =
        BodyCompositionPoint(id, BodyCompositionMetric.WEIGHT_KG, kg, at, null, null)
}
