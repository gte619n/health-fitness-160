package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.data.di.IoDispatcher
import com.gte619n.healthfitness.domain.blood.BloodReadingRepository
import com.gte619n.healthfitness.domain.blood.BloodTestReportRepository
import com.gte619n.healthfitness.domain.blood.LatestMarker
import com.gte619n.healthfitness.domain.blood.LatestMarkers
import com.gte619n.healthfitness.domain.blood.MarkerCatalog
import com.gte619n.healthfitness.domain.blood.ReferenceRange
import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummary
import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummaryRepository
import com.gte619n.healthfitness.domain.dashboard.HistoryPoint
import com.gte619n.healthfitness.domain.dashboard.MarkerTone
import com.gte619n.healthfitness.domain.dashboard.TodaysDoseSummary
import com.gte619n.healthfitness.domain.dashboard.TodaysDosesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * IMPL-AND-04 rewires the dashboard blood panel onto the same
 * [BloodReadingRepository] + [BloodTestReportRepository] the new
 * feature-blood module uses. The panel shows the same top-4 markers
 * by [MarkerCatalog.DISPLAY_ORDER], in the same order, with values
 * derived from the union of manual readings + lab-extracted markers.
 *
 * The output type stays [BloodMarkerSummary] so the existing
 * `BloodPanel` composable + `DashboardViewModel` are untouched —
 * only the data source changes.
 *
 * Round 2 Stage C: the body-composition repo previously co-located
 * here moved to `data.bodycomposition.BodyCompositionRepositoryImpl`;
 * the dashboard hero now consumes the canonical snapshot Flow.
 */
@Singleton
internal class BloodMarkerSummaryRepositoryImpl @Inject constructor(
    private val readings: BloodReadingRepository,
    private val reports: BloodTestReportRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BloodMarkerSummaryRepository {

    override suspend fun loadDashboardMarkers(): List<BloodMarkerSummary> = withContext(io) {
        readings.refresh()
        reports.refresh()
        val r = readings.observeReadings().first()
        val rep = reports.observeReports().first()
        LatestMarkers.derive(r, rep)
            .filter { it.value != null && it.reference != null }
            .take(4)
            .map(::toDashboardSummary)
    }

    private fun toDashboardSummary(latest: LatestMarker): BloodMarkerSummary {
        val ref: ReferenceRange = latest.reference!!
        val value = latest.value!!
        val span = (ref.displayMax - ref.displayMin).takeIf { it > 0.0 } ?: 1.0
        val tickPct = ((value - ref.displayMin) / span).coerceIn(0.0, 1.0).toFloat()
        val lowerIsBetter = ref.orientation == ReferenceRange.Orientation.LOWER_IS_BETTER
        val goodLeftPct: Float
        val goodFillPct: Float
        if (lowerIsBetter) {
            goodLeftPct = 0f
            goodFillPct = ((ref.goodThreshold - ref.displayMin) / span)
                .coerceIn(0.0, 1.0).toFloat()
        } else {
            val left = ((ref.goodThreshold - ref.displayMin) / span)
                .coerceIn(0.0, 1.0)
            goodLeftPct = left.toFloat()
            goodFillPct = (1.0 - left).toFloat()
        }
        val onGoodSide = if (lowerIsBetter) value <= ref.goodThreshold
        else value >= ref.goodThreshold
        val tone: MarkerTone = if (onGoodSide) MarkerTone.Good
        else {
            val distance = abs(value - ref.goodThreshold) /
                ref.goodThreshold.takeIf { it != 0.0 }.let { it ?: 1.0 }
            if (distance < 0.15) MarkerTone.Warn else MarkerTone.Alert
        }
        return BloodMarkerSummary(
            markerKey = latest.marker.name,
            displayName = MarkerCatalog.displayName(latest.marker),
            value = value,
            unit = latest.unit.ifBlank { ref.unit },
            tone = tone,
            goodFillPct = goodFillPct,
            goodLeftPct = goodLeftPct,
            tickPct = tickPct,
            displayMin = ref.displayMin,
            goodThreshold = ref.goodThreshold,
            displayMax = ref.displayMax,
            history = latest.history.map { HistoryPoint(it.date, it.value) },
        )
    }
}

@Singleton
internal class TodaysDosesRepositoryImpl @Inject constructor(
    private val api: DashboardApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TodaysDosesRepository {
    override suspend fun loadToday(): List<TodaysDoseSummary> = withContext(io) {
        api.todaysDoses().map(TodaysDosesMapper::toDomain)
    }
}
