package com.gte619n.healthfitness.domain.blood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LatestMarkersTest {

    private val today = LocalDate.of(2026, 5, 26)

    private val ldlRef = ReferenceRange(
        unit = "mg/dL",
        orientation = ReferenceRange.Orientation.LOWER_IS_BETTER,
        goodThreshold = 100.0,
        displayMin = 0.0,
        displayMax = 200.0,
    )

    private fun reading(
        marker: BloodMarker,
        value: Double,
        date: LocalDate,
        unit: String = ldlRef.unit,
        reference: ReferenceRange = ldlRef,
    ) = BloodReading(
        readingId = "$marker-$value-$date",
        marker = marker,
        value = value,
        unit = unit,
        sampleDate = date,
        labSource = null,
        notes = null,
        reference = reference,
    )

    @Test
    fun `empty inputs yield one LatestMarker per known marker with source NONE`() {
        val result = LatestMarkers.derive(emptyList(), emptyList(), today)
        assertEquals(MarkerCatalog.DISPLAY_ORDER.size, result.size)
        result.forEach {
            assertEquals(LatestMarker.Source.NONE, it.source)
            assertNull(it.value)
            assertTrue(it.history.isEmpty())
        }
    }

    @Test
    fun `latest manual reading wins when only manual entries exist`() {
        val readings = listOf(
            reading(BloodMarker.LDL, 110.0, today.minusDays(60)),
            reading(BloodMarker.LDL, 85.0, today.minusDays(1)),
        )
        val result = LatestMarkers.derive(readings, emptyList(), today)
        val ldl = result.first { it.marker == BloodMarker.LDL }
        assertEquals(85.0, ldl.value!!, 0.0)
        assertEquals(LatestMarker.Source.MANUAL, ldl.source)
        assertEquals(2, ldl.history.size)
        assertEquals(today.minusDays(60), ldl.history.first().date)
        assertEquals(today.minusDays(1), ldl.history.last().date)
    }

    @Test
    fun `lab report markers feed history when no manual reading exists`() {
        val report = BloodTestReport(
            reportId = "rep-1",
            sampleDate = today.minusDays(10),
            labSource = "Quest",
            markers = listOf(
                ExtractedMarker("LDL", 92.0, "mg/dL", 0.0, 100.0, null),
                ExtractedMarker("APO_B", 75.0, "mg/dL", 0.0, 90.0, null),
            ),
            pdfDownloadPath = "/x",
            createdAt = Instant.parse("2026-05-16T00:00:00Z"),
        )
        val result = LatestMarkers.derive(emptyList(), listOf(report), today)
        val ldl = result.first { it.marker == BloodMarker.LDL }
        assertEquals(92.0, ldl.value!!, 0.0)
        assertEquals(LatestMarker.Source.LAB, ldl.source)
        assertEquals(1, ldl.history.size)
        assertEquals(
            MarkerHistoryPoint.Source.Lab("rep-1", "Quest"),
            ldl.history.first().source,
        )

        val apoB = result.first { it.marker == BloodMarker.APO_B }
        assertEquals(75.0, apoB.value!!, 0.0)
    }

    @Test
    fun `manual wins over lab on same date`() {
        val report = BloodTestReport(
            reportId = "rep-1",
            sampleDate = today.minusDays(5),
            labSource = "Quest",
            markers = listOf(ExtractedMarker("LDL", 120.0, "mg/dL", 0.0, 100.0, ExtractedMarker.Flag.H)),
            pdfDownloadPath = "/x",
            createdAt = null,
        )
        val readings = listOf(reading(BloodMarker.LDL, 88.0, today.minusDays(5)))
        val result = LatestMarkers.derive(readings, listOf(report), today)
        val ldl = result.first { it.marker == BloodMarker.LDL }
        assertEquals(88.0, ldl.value!!, 0.0)
        assertEquals(LatestMarker.Source.MANUAL, ldl.source)
    }

    @Test
    fun `history dedupes same-day entries keeping last`() {
        val day = today.minusDays(2)
        val readings = listOf(
            reading(BloodMarker.LDL, 100.0, day),
            reading(BloodMarker.LDL, 105.0, day),
            reading(BloodMarker.LDL, 110.0, day),
        )
        val result = LatestMarkers.derive(readings, emptyList(), today)
        val ldl = result.first { it.marker == BloodMarker.LDL }
        assertEquals(1, ldl.history.size)
        assertEquals(110.0, ldl.history.first().value, 0.0)
    }

    @Test
    fun `readings older than 12 months are excluded from history`() {
        val readings = listOf(
            reading(BloodMarker.LDL, 90.0, today.minusDays(400)),
            reading(BloodMarker.LDL, 95.0, today.minusDays(30)),
        )
        val result = LatestMarkers.derive(readings, emptyList(), today)
        val ldl = result.first { it.marker == BloodMarker.LDL }
        assertEquals(1, ldl.history.size)
        assertEquals(95.0, ldl.history.first().value, 0.0)
    }

    @Test
    fun `extracted marker with unknown name is dropped`() {
        val report = BloodTestReport(
            reportId = "rep-1",
            sampleDate = today,
            labSource = "Quest",
            markers = listOf(ExtractedMarker("FERRITIN", 50.0, "ng/mL", null, null, null)),
            pdfDownloadPath = "/x",
            createdAt = null,
        )
        val result = LatestMarkers.derive(emptyList(), listOf(report), today)
        assertTrue(result.all { it.source == LatestMarker.Source.NONE })
    }

    @Test
    fun `MarkerCatalog fromExtractedName tolerates case and whitespace`() {
        assertEquals(BloodMarker.LDL, MarkerCatalog.fromExtractedName("ldl"))
        assertEquals(BloodMarker.HBA1C, MarkerCatalog.fromExtractedName(" HBA1C "))
        assertNull(MarkerCatalog.fromExtractedName("UNKNOWN_MARKER"))
        assertNotNull(MarkerCatalog.fromExtractedName("APO_B"))
        assertFalse(MarkerCatalog.DISPLAY_ORDER.isEmpty())
    }
}
