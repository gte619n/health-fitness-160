package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.domain.dashboard.MarkerTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * Pure unit tests for [BloodMarkerSummaryMapper].
 */
class BloodMarkerSummaryMapperTest {

    private val today = LocalDate.of(2026, 5, 20)

    @Test
    fun `empty readings returns empty list`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(emptyList(), today)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `non-dashboard markers are filtered`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(reading("HDL", 65.0, today, lowerIsBetter = false, goodAt = 60.0, min = 0.0, max = 100.0)),
            today,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `markers returned in display order Testosterone LDL ApoB HbA1c`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(
                ldl(100.0, today),
                hba1c(5.0, today),
                apoB(80.0, today),
                testosterone(700.0, today),
            ),
            today,
        )
        assertEquals(listOf("TESTOSTERONE", "LDL", "APO_B", "HBA1C"), out.map { it.markerKey })
        assertEquals(listOf("Testosterone", "LDL", "ApoB", "HbA1c"), out.map { it.displayName })
    }

    @Test
    fun `markers without readings are omitted`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(100.0, today)),
            today,
        )
        assertEquals(1, out.size)
        assertEquals("LDL", out.first().markerKey)
    }

    @Test
    fun `lower-is-better good case ldl 80 below threshold 100`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(80.0, today)),
            today,
        ).first()
        assertEquals(MarkerTone.Good, out.tone)
        // displayMin=0 max=200 ⇒ tick = 80/200 = 0.4
        assertCloseTo(0.4f, out.tickPct, 0.001f)
        // LOWER_IS_BETTER ⇒ goodLeft = 0, goodFill = 100/200 = 0.5
        assertCloseTo(0f, out.goodLeftPct, 0.001f)
        assertCloseTo(0.5f, out.goodFillPct, 0.001f)
    }

    @Test
    fun `lower-is-better warn within fifteen percent`() {
        // 110/100 ⇒ +10%, < 15% ⇒ Warn (not Alert)
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(110.0, today)),
            today,
        ).first()
        assertEquals(MarkerTone.Warn, out.tone)
    }

    @Test
    fun `lower-is-better alert above fifteen percent`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(160.0, today)),
            today,
        ).first()
        assertEquals(MarkerTone.Alert, out.tone)
    }

    @Test
    fun `higher-is-better good case testosterone above threshold`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(testosterone(700.0, today)),
            today,
        ).first()
        assertEquals(MarkerTone.Good, out.tone)
        // displayMin=200 threshold=300 max=1200 ⇒ span=1000
        // tick = (700-200)/1000 = 0.5
        assertCloseTo(0.5f, out.tickPct, 0.001f)
        // HIGHER_IS_BETTER ⇒ goodLeft = (300-200)/1000 = 0.1; goodFill = 0.9
        assertCloseTo(0.1f, out.goodLeftPct, 0.001f)
        assertCloseTo(0.9f, out.goodFillPct, 0.001f)
    }

    @Test
    fun `higher-is-better alert below threshold`() {
        // 200 vs threshold 300 ⇒ -33%, > 15% ⇒ Alert
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(testosterone(200.0, today)),
            today,
        ).first()
        assertEquals(MarkerTone.Alert, out.tone)
    }

    @Test
    fun `value above displayMax clamps tick to one`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(500.0, today)),
            today,
        ).first()
        assertCloseTo(1.0f, out.tickPct, 0.001f)
    }

    @Test
    fun `value below displayMin clamps tick to zero`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(ldl(-5.0, today)),
            today,
        ).first()
        assertCloseTo(0.0f, out.tickPct, 0.001f)
    }

    @Test
    fun `history filtered to 365 days and deduped by date`() {
        val tooOld = today.minusDays(400)
        val withinA = today.minusDays(200)
        val withinB = today.minusDays(100)
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(
                ldl(50.0, tooOld),
                ldl(80.0, withinA),
                ldl(85.0, withinA),    // same date — keep last
                ldl(110.0, withinB),
            ),
            today,
        ).first()
        assertEquals(2, out.history.size)
        // First (chronological): withinA with the LATER value (85)
        assertEquals(withinA, out.history.first().date)
        assertCloseTo(85.0, out.history.first().value, 0.001)
        assertEquals(withinB, out.history.last().date)
    }

    @Test
    fun `latest reading wins when multiple sample dates exist for same marker`() {
        val out = BloodMarkerSummaryMapper.toDashboardMarkers(
            listOf(
                ldl(80.0, today.minusDays(30)),
                ldl(120.0, today),
            ),
            today,
        ).first()
        assertCloseTo(120.0, out.value, 0.001)
    }

    // ---- helpers ----

    private fun ldl(value: Double, date: LocalDate) = reading(
        "LDL", value, date, lowerIsBetter = true, goodAt = 100.0, min = 0.0, max = 200.0,
    )

    private fun apoB(value: Double, date: LocalDate) = reading(
        "APO_B", value, date, lowerIsBetter = true, goodAt = 90.0, min = 0.0, max = 180.0,
    )

    private fun hba1c(value: Double, date: LocalDate) = reading(
        "HBA1C", value, date, lowerIsBetter = true, goodAt = 5.7, min = 4.0, max = 7.0, unit = "%",
    )

    private fun testosterone(value: Double, date: LocalDate) = reading(
        "TESTOSTERONE",
        value,
        date,
        lowerIsBetter = false,
        goodAt = 300.0,
        min = 200.0,
        max = 1200.0,
        unit = "ng/dL",
    )

    private fun reading(
        marker: String,
        value: Double,
        date: LocalDate,
        lowerIsBetter: Boolean,
        goodAt: Double,
        min: Double,
        max: Double,
        unit: String = "mg/dL",
    ): BloodReadingDto = BloodReadingDto(
        readingId = "$marker-${date}",
        marker = marker,
        value = value,
        unit = unit,
        sampleDate = date,
        labSource = null,
        notes = null,
        reference = ReferenceDto(
            unit = unit,
            orientation = if (lowerIsBetter) "LOWER_IS_BETTER" else "HIGHER_IS_BETTER",
            goodThreshold = goodAt,
            displayMin = min,
            displayMax = max,
        ),
    )

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) <= epsilon)
    }

    private fun assertCloseTo(expected: Float, actual: Float, epsilon: Float) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) <= epsilon)
    }
}
