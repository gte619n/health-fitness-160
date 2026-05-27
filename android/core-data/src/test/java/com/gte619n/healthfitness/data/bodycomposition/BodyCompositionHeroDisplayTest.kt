package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.buildSnapshot
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionMetric
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionPoint
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Ports the downsample / xLabels / lb-conversion / padding coverage from
 * the retired `core-data/dashboard/BodyCompositionMapperTest`. The math
 * is now a derivation over [BodyCompositionSnapshot] living in
 * [WeightHeroDisplay.from]; these tests pin it at parity with the old
 * mapper so the dashboard hero numbers don't shift visually across the
 * consolidation.
 *
 * `now` is passed explicitly so tests aren't flaky across midnight.
 */
class BodyCompositionHeroDisplayTest {

    private val now: Instant = Instant.parse("2026-05-20T08:00:00Z")

    @Test fun `empty snapshot returns null display`() {
        val display = WeightHeroDisplay.from(emptySnapshot(), now = now)
        assertNull(display)
    }

    @Test fun `snapshot without weight readings returns null display`() {
        val points = listOf(bodyFat(now, 17.0))
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)
        assertNull(display)
    }

    @Test fun `single weight reading produces display with latest only`() {
        val points = listOf(weight(now, 85.0))
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        assertCloseTo(85.0 * WeightHeroDisplay.KG_TO_LB, display.latestLb, 0.001)
        // No reading earlier than now-7d ⇒ no 7-day anchor.
        assertNull(display.sevenDayDeltaLb)
    }

    @Test fun `kg to lb conversion uses 2_20462`() {
        val snap = buildSnapshot(listOf(weight(now, 100.0)), now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        assertCloseTo(220.462, display.latestLb, 0.001)
    }

    @Test fun `seven day delta uses reading at or before seven days ago`() {
        val older = now.minusSeconds(8L * 24 * 60 * 60)
        val recent = now
        val snap = buildSnapshot(
            listOf(weight(older, 90.0), weight(recent, 88.0)),
            now = now,
        )
        val display = WeightHeroDisplay.from(snap, now = now)!!
        val expected = (88.0 - 90.0) * WeightHeroDisplay.KG_TO_LB
        assertCloseTo(expected, display.sevenDayDeltaLb!!, 0.001)
    }

    @Test fun `downsample reduces dense series to roughly thirty points`() {
        val series = (0 until 90).map { it.toDouble() }
        val down = WeightHeroDisplay.downsample(series, 30)
        assertEquals(30, down.size)
        // Mean of bucket [0..2] = 1.0
        assertCloseTo(1.0, down.first(), 0.001)
        // Mean of last bucket [87..89] = 88.0
        assertCloseTo(88.0, down.last(), 0.001)
    }

    @Test fun `downsample passes short series through unchanged`() {
        val series = listOf(1.0, 2.0, 3.0)
        val down = WeightHeroDisplay.downsample(series, 30)
        assertEquals(series, down)
    }

    @Test fun `lean mass derived when body-fat pairs with weight within six hours`() {
        val bfAt = now
        val weightAt = now.minusSeconds(2L * 60 * 60) // 2h apart, within 6h
        val points = listOf(
            weight(weightAt, 100.0),
            bodyFat(bfAt, 20.0),
        )
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        assertNotNull(display.latestLeanMassLb)
        // 100 kg × (1 − 0.20) = 80 kg lean → 176.3696 lb
        assertCloseTo(176.3696, display.latestLeanMassLb!!, 0.01)
        assertCloseTo(20.0, display.latestBodyFatPct!!, 0.001)
    }

    @Test fun `lean mass null when no weight within six hours of body-fat`() {
        val bfAt = now
        val weightAt = now.minusSeconds(12L * 60 * 60) // 12h apart
        val points = listOf(
            weight(weightAt, 100.0),
            bodyFat(bfAt, 20.0),
        )
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        assertNull(display.latestLeanMassLb)
    }

    @Test fun `x labels are produced when window has at least two points`() {
        val a = now.minusSeconds(60L * 24 * 60 * 60)
        val b = now.minusSeconds(30L * 24 * 60 * 60)
        val c = now
        val points = listOf(weight(a, 90.0), weight(b, 89.0), weight(c, 88.0))
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        assertEquals(4, display.xLabels.size)
        // Last label should be the latest reading (May 20, 2026 UTC),
        // uppercased to match web.
        assertEquals("MAY 20", display.xLabels.last().label)
    }

    @Test fun `y bounds include padding around the range`() {
        val a = now.minusSeconds(2L * 24 * 60 * 60)
        val b = now
        val points = listOf(weight(a, 100.0), weight(b, 102.0))
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        // Both values in lb are 220.462 and 224.871. Padding ≥1, so the
        // bounds should sit outside the data range.
        assertTrue("yMin=${display.yMin}", display.yMin <= 220.0)
        assertTrue("yMax=${display.yMax}", display.yMax >= 225.0)
    }

    @Test fun `ninety day delta converted from snapshot kg delta`() {
        val a = now.minusSeconds(60L * 86_400)
        val b = now.minusSeconds(30L * 86_400)
        val c = now.minusSeconds(1L * 86_400)
        val points = listOf(weight(a, 80.0), weight(b, 79.5), weight(c, 79.0))
        val snap = buildSnapshot(points, now = now)
        val display = WeightHeroDisplay.from(snap, now = now)!!
        // Snapshot delta in kg is 79.0 − 79.5 (mean fallback) = −0.5.
        // In lb: −0.5 × 2.20462 ≈ −1.10231.
        assertCloseTo(-0.5 * WeightHeroDisplay.KG_TO_LB, display.ninetyDayDeltaLb!!, 0.001)
    }

    // ---- helpers ----

    private fun weight(at: Instant, kg: Double) = BodyCompositionPoint(
        recordId = "w-${at.epochSecond}",
        metric = BodyCompositionMetric.WEIGHT_KG,
        value = kg,
        sampleTime = at,
        sourcePlatform = "test",
        recordingMethod = null,
    )

    private fun bodyFat(at: Instant, pct: Double) = BodyCompositionPoint(
        recordId = "bf-${at.epochSecond}",
        metric = BodyCompositionMetric.BODY_FAT_PERCENT,
        value = pct,
        sampleTime = at,
        sourcePlatform = "test",
        recordingMethod = null,
    )

    private fun emptySnapshot() = BodyCompositionSnapshot(
        latestWeightKg = null,
        latestBodyFatPercent = null,
        latestLeanMassKg = null,
        latestBmi = null,
        latestSampleTime = null,
        sevenDayDeltaKg = null,
        ninetyDayDeltaKg = null,
        series90d = emptyList(),
    )

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) <= epsilon)
    }
}
