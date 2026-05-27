package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummary
import com.gte619n.healthfitness.domain.dashboard.HistoryPoint
import com.gte619n.healthfitness.domain.dashboard.MarkerTone
import java.time.LocalDate
import kotlin.math.abs

/**
 * IMPL-AND-04 note: this mapper is no longer used in production —
 * [BloodMarkerSummaryRepositoryImpl] now delegates to the blood domain
 * repositories + `LatestMarkers.derive`. The mapper file is kept (and
 * its tests pass) as a historical reference for the dashboard-only
 * derivation logic from Stage 01, in case a separate single-endpoint
 * path ever needs to be revived. Delete if neither use case
 * resurfaces by IMPL-AND-08.
 *
 * Pure conversion DTO → [BloodMarkerSummary] list.
 *
 * Mirrors web/app/page.tsx:loadBloodPanel:
 *  - filter to the four dashboard markers
 *  - latest reading per marker = max-by sampleDate
 *  - 365-day history per marker, deduped by date (keep last value)
 *  - tone = onGoodSide ? Good
 *           : abs(value - good) / good < 0.15 ? Warn
 *           : Alert
 *  - tickPct / goodFillPct / goodLeftPct in 0..1 fractions
 *
 * NOTE: TESTOSTERONE is in DISPLAY_ORDER but is NOT in the backend's
 * BloodMarker enum (see android-impl-questions.md). Users will only see
 * it once the backend adds the enum value or IMPL-AND-04 brings the
 * extracted-marker reports endpoint online. This mapper transparently
 * omits it when no readings carry the key.
 */
internal object BloodMarkerSummaryMapper {

    private const val WARN_DISTANCE = 0.15

    private val DISPLAY_ORDER = listOf("TESTOSTERONE", "LDL", "APO_B", "HBA1C")

    private val LABELS = mapOf(
        "TESTOSTERONE" to "Testosterone",
        "LDL" to "LDL",
        "APO_B" to "ApoB",
        "HBA1C" to "HbA1c",
    )

    fun toDashboardMarkers(
        readings: List<BloodReadingDto>,
        today: LocalDate = LocalDate.now(),
    ): List<BloodMarkerSummary> {
        if (readings.isEmpty()) return emptyList()

        val cutoff = today.minusDays(365)
        val byKey: Map<String, List<BloodReadingDto>> = readings
            .filter { it.marker in DISPLAY_ORDER }
            .groupBy { it.marker }

        return DISPLAY_ORDER.mapNotNull { key ->
            val rs = byKey[key] ?: return@mapNotNull null
            if (rs.isEmpty()) return@mapNotNull null

            val latest = rs.maxByOrNull { it.sampleDate } ?: return@mapNotNull null

            val history = rs
                .filter { !it.sampleDate.isBefore(cutoff) }
                .sortedBy { it.sampleDate }
                .let { dedupeByDate(it) }
                .map { HistoryPoint(it.sampleDate, it.value) }

            val ref = latest.reference
            val span = (ref.displayMax - ref.displayMin).takeIf { it > 0.0 } ?: 1.0
            val tickPctF = (((latest.value - ref.displayMin) / span)
                .coerceIn(0.0, 1.0)).toFloat()
            val orientationLower = ref.orientation == "LOWER_IS_BETTER"

            val goodLeftPct: Float
            val goodFillPct: Float
            if (orientationLower) {
                goodLeftPct = 0f
                goodFillPct = (((ref.goodThreshold - ref.displayMin) / span)
                    .coerceIn(0.0, 1.0)).toFloat()
            } else {
                val left = ((ref.goodThreshold - ref.displayMin) / span)
                    .coerceIn(0.0, 1.0)
                goodLeftPct = left.toFloat()
                goodFillPct = (1.0 - left).toFloat()
            }

            val onGoodSide = if (orientationLower) {
                latest.value <= ref.goodThreshold
            } else {
                latest.value >= ref.goodThreshold
            }
            val tone: MarkerTone = if (onGoodSide) {
                MarkerTone.Good
            } else {
                val distance = abs(latest.value - ref.goodThreshold) /
                    ref.goodThreshold.takeIf { it != 0.0 }.let { it ?: 1.0 }
                if (distance < WARN_DISTANCE) MarkerTone.Warn else MarkerTone.Alert
            }

            BloodMarkerSummary(
                markerKey = key,
                displayName = LABELS[key] ?: key,
                value = latest.value,
                unit = latest.unit,
                tone = tone,
                goodFillPct = goodFillPct,
                goodLeftPct = goodLeftPct,
                tickPct = tickPctF,
                displayMin = ref.displayMin,
                goodThreshold = ref.goodThreshold,
                displayMax = ref.displayMax,
                history = history,
            )
        }
    }

    /** Keeps the *last* reading per sampleDate. Input must be ascending. */
    private fun dedupeByDate(sorted: List<BloodReadingDto>): List<BloodReadingDto> {
        if (sorted.isEmpty()) return sorted
        val out = ArrayList<BloodReadingDto>(sorted.size)
        for (r in sorted) {
            if (out.isNotEmpty() && out.last().sampleDate == r.sampleDate) {
                out[out.size - 1] = r
            } else {
                out.add(r)
            }
        }
        return out
    }
}
