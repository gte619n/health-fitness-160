package com.gte619n.healthfitness.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Pure-JVM unit tests for [BodyCompositionMapper]. Don't use any
 * Android machinery — the mapper has none. `now` is passed explicitly
 * so the tests aren't flaky across midnight.
 */
class BodyCompositionMapperTest {

    private val now = Instant.parse("2026-05-20T08:00:00Z")

    @Test
    fun `empty readings returns null`() {
        val summary = BodyCompositionMapper.toWeightSummary(emptyList(), now)
        assertNull(summary)
    }

    @Test
    fun `non-weight-only readings returns null`() {
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(bodyFat(now, 17.0)),
            now,
        )
        assertNull(summary)
    }

    @Test
    fun `single weight reading produces summary with latest only`() {
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(now, 85.0)),
            now,
        )
        assertNotNull(summary)
        summary!!
        assertCloseTo(85.0 * BodyCompositionMapper.KG_TO_LB, summary.latestLb, 0.001)
        // Single point ⇒ delta vs mean(window) = 0.
        assertCloseTo(0.0, summary.ninetyDayDeltaLb!!, 0.001)
        // No reading earlier than now-7d ⇒ no 7-day anchor.
        assertNull(summary.sevenDayDeltaLb)
    }

    @Test
    fun `kg to lb conversion uses 2_20462`() {
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(now, 100.0)),
            now,
        )!!
        assertCloseTo(220.462, summary.latestLb, 0.001)
    }

    @Test
    fun `seven day delta uses reading at or before seven days ago`() {
        val older = now.minusSeconds(8L * 24 * 60 * 60)
        val recent = now
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(older, 90.0), weight(recent, 88.0)),
            now,
        )!!
        val expected = (88.0 - 90.0) * BodyCompositionMapper.KG_TO_LB
        assertCloseTo(expected, summary.sevenDayDeltaLb!!, 0.001)
    }

    @Test
    fun `ninety day window falls back to full series when fewer than two in window`() {
        // Both readings are >90 days old — should still produce a summary
        // by falling back to the all-time series.
        val a = now.minusSeconds(200L * 24 * 60 * 60)
        val b = now.minusSeconds(150L * 24 * 60 * 60)
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(a, 90.0), weight(b, 88.0)),
            now,
        )
        assertNotNull(summary)
    }

    @Test
    fun `downsample reduces dense series to roughly thirty points`() {
        val series = (0 until 90).map { it.toDouble() }
        val down = BodyCompositionMapper.downsample(series, 30)
        assertEquals(30, down.size)
        // Mean of bucket [0..2] = 1.0
        assertCloseTo(1.0, down.first(), 0.001)
        // Mean of last bucket [87..89] = 88.0
        assertCloseTo(88.0, down.last(), 0.001)
    }

    @Test
    fun `downsample passes short series through unchanged`() {
        val series = listOf(1.0, 2.0, 3.0)
        val down = BodyCompositionMapper.downsample(series, 30)
        assertEquals(series, down)
    }

    @Test
    fun `lean mass derived when body-fat pairs with weight within six hours`() {
        val bfAt = now
        val weightAt = now.minusSeconds(2L * 60 * 60) // 2h apart, within 6h
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(
                weight(weightAt, 100.0),       // 220.462 lb
                bodyFat(bfAt, 20.0),
            ),
            now,
        )!!
        assertNotNull(summary.latestLeanMassLb)
        // 220.462 * (1 - 0.20) = 176.3696
        assertCloseTo(176.3696, summary.latestLeanMassLb!!, 0.01)
        assertCloseTo(20.0, summary.latestBodyFatPct!!, 0.001)
    }

    @Test
    fun `lean mass null when no weight within six hours of body-fat`() {
        val bfAt = now
        val weightAt = now.minusSeconds(12L * 60 * 60) // 12h apart
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(
                weight(weightAt, 100.0),
                bodyFat(bfAt, 20.0),
            ),
            now,
        )!!
        assertNull(summary.latestLeanMassLb)
    }

    @Test
    fun `x labels are produced when window has at least two points`() {
        val a = now.minusSeconds(60L * 24 * 60 * 60)
        val b = now.minusSeconds(30L * 24 * 60 * 60)
        val c = now
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(a, 90.0), weight(b, 89.0), weight(c, 88.0)),
            now,
        )!!
        assertEquals(4, summary.xLabels.size)
        // Last label should be the latest reading (May 20, 2026 UTC),
        // uppercased to match web.
        assertEquals("MAY 20", summary.xLabels.last().label)
    }

    @Test
    fun `y bounds include padding around the range`() {
        val a = now.minusSeconds(2L * 24 * 60 * 60)
        val b = now
        val summary = BodyCompositionMapper.toWeightSummary(
            listOf(weight(a, 100.0), weight(b, 102.0)),
            now,
        )!!
        // Both values in lb are 220.462 and 224.871. Padding ≥1, so the
        // bounds should sit outside the data range.
        assertTrue(summary.yMin <= 220.0)
        assertTrue(summary.yMax >= 225.0)
    }

    // ---- helpers ----

    private fun weight(at: Instant, kg: Double): BodyCompositionDto = BodyCompositionDto(
        recordId = "w-${at.epochSecond}",
        metric = "WEIGHT_KG",
        value = kg,
        sampleTime = at,
        sourcePlatform = "test",
        recordingMethod = null,
    )

    private fun bodyFat(at: Instant, pct: Double): BodyCompositionDto = BodyCompositionDto(
        recordId = "bf-${at.epochSecond}",
        metric = "BODY_FAT_PERCENT",
        value = pct,
        sampleTime = at,
        sourcePlatform = "test",
        recordingMethod = null,
    )

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) <= epsilon)
    }
}
