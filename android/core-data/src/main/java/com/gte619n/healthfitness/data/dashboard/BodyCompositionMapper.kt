package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.domain.dashboard.BodyMetric
import com.gte619n.healthfitness.domain.dashboard.ChartXLabel
import com.gte619n.healthfitness.domain.dashboard.WeightSummary
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure conversion DTO → [WeightSummary]. No Android, no Hilt, no clock
 * injection — tests pass `now` explicitly so they're not flaky against
 * Instant.now().
 *
 * Matches the math in web/app/page.tsx:loadBodyComposition:
 *  - kg → lb at the boundary (KG_TO_LB = 2.20462)
 *  - 90d window; fall back to full series if <2 readings in the window
 *  - 7d delta = latest − reading at-or-before (now − 7d), or null if
 *    no such anchor exists
 *  - 90d delta = latest − mean(window)
 *  - lean mass = paired (weight, body-fat) within 6h
 *  - y-bounds: floor/ceil of min/max with 15% padding
 *
 * Note: the spec calls for *downsampling* to ~30 points (bucket-average)
 * for the chart series — see [downsample]. The deltas use the full window
 * (not the downsampled series) so they don't drift on dense data.
 */
internal object BodyCompositionMapper {

    internal const val KG_TO_LB = 2.20462
    private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    private const val SIX_HOURS_MS = 6L * 60 * 60 * 1000
    private const val TARGET_POINTS = 30
    private val LABEL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM dd", Locale.US).withZone(ZoneOffset.UTC)

    fun toWeightSummary(
        readings: List<BodyCompositionDto>,
        now: Instant = Instant.now(),
    ): WeightSummary? {
        if (readings.isEmpty()) return null

        val weightsAll = readings
            .filter { it.metric == BodyMetric.WEIGHT_KG.name }
            .sortedBy { it.sampleTime }
        if (weightsAll.isEmpty()) return null

        val nowMs = now.toEpochMilli()
        val ninetyDaysAgoMs = nowMs - NINETY_DAYS_MS

        val weights90 = weightsAll.filter { it.sampleTime.toEpochMilli() >= ninetyDaysAgoMs }
        val window = if (weights90.size >= 2) weights90 else weightsAll

        // Convert to lb up-front; everything below operates on (timeMs, lb).
        val pointsLb = window.map { it.sampleTime.toEpochMilli() to (it.value * KG_TO_LB) }
        val seriesAllLb = pointsLb.map { it.second }
        val latestLb = seriesAllLb.last()

        val mean = seriesAllLb.sum() / seriesAllLb.size
        val ninetyDayDelta = latestLb - mean

        // 7-day delta uses the reading at-or-before (now - 7d). Null when
        // the series is too short to have one.
        val sevenDaysAgoMs = nowMs - SEVEN_DAYS_MS
        val sevenDayReference = pointsLb
            .lastOrNull { it.first <= sevenDaysAgoMs }
        val sevenDayDelta = sevenDayReference?.let { latestLb - it.second }

        // Bucket-average down to ~30 points so the Canvas doesn't have
        // to render 8-readings-a-day for users with a smart scale.
        val downsampled = downsample(seriesAllLb, TARGET_POINTS)

        // y bounds with 15% padding (web uses the same).
        val rawMin = downsampled.min()
        val rawMax = downsampled.max()
        val padding = max(1.0, (rawMax - rawMin) * 0.15)
        val yMin = floor(rawMin - padding)
        val yMax = ceil(rawMax + padding)

        val xLabels = buildXLabels(pointsLb)

        val latestBodyFat = readings
            .filter { it.metric == BodyMetric.BODY_FAT_PERCENT.name }
            .maxByOrNull { it.sampleTime }
        val latestBodyFatPct = latestBodyFat?.value
        val latestLeanMassLb = latestBodyFat?.let { bf ->
            // Pair body-fat with closest weight within 6h, using the
            // FULL weight history (not the windowed one) — the latest
            // body-fat may be older than the 90d window.
            computeLeanMassLb(weightsAll, bf)
        }

        return WeightSummary(
            latestLb = latestLb,
            sevenDayDeltaLb = sevenDayDelta,
            ninetyDayDeltaLb = ninetyDayDelta,
            series = downsampled,
            yMin = yMin,
            yMax = yMax,
            xLabels = xLabels,
            latestBodyFatPct = latestBodyFatPct,
            latestLeanMassLb = latestLeanMassLb,
        )
    }

    /**
     * Bucket-average downsample. Returns at most [targetPoints] entries.
     * Inputs shorter than `targetPoints` pass through unchanged.
     */
    internal fun downsample(series: List<Double>, targetPoints: Int): List<Double> {
        if (series.size <= targetPoints) return series
        val bucketSize = series.size.toDouble() / targetPoints
        val buckets = mutableListOf<Double>()
        var i = 0
        while (i < targetPoints) {
            val start = floor(i * bucketSize).toInt()
            val end = min(series.size, floor((i + 1) * bucketSize).toInt().coerceAtLeast(start + 1))
            val slice = series.subList(start, end)
            buckets.add(slice.sum() / slice.size)
            i++
        }
        return buckets
    }

    private fun computeLeanMassLb(
        weights: List<BodyCompositionDto>,
        bodyFat: BodyCompositionDto,
    ): Double? {
        val bfMs = bodyFat.sampleTime.toEpochMilli()
        val pair = weights.minByOrNull { kotlin.math.abs(it.sampleTime.toEpochMilli() - bfMs) }
            ?: return null
        val diff = kotlin.math.abs(pair.sampleTime.toEpochMilli() - bfMs)
        if (diff > SIX_HOURS_MS) return null
        val weightLb = pair.value * KG_TO_LB
        return weightLb * (1.0 - bodyFat.value / 100.0)
    }

    private fun buildXLabels(pointsLb: List<Pair<Long, Double>>): List<ChartXLabel> {
        if (pointsLb.size < 2) return emptyList()
        // Four roughly-evenly spaced ticks, mirroring web's xs = [32, 180, 335, 500]
        // for the 600-px viewBox. Express as 0..1 fractions so the Compose
        // chart can place them at any width.
        val xFractions = floatArrayOf(32f / 600f, 180f / 600f, 335f / 600f, 500f / 600f)
        val n = pointsLb.size
        val idxs = intArrayOf(0, n / 3, (2 * n) / 3, n - 1)
        return idxs.zip(xFractions.toList()).map { (idx, frac) ->
            val (timeMs, _) = pointsLb[min(idx, n - 1)]
            ChartXLabel(xFraction = frac, label = LABEL_FORMATTER.format(Instant.ofEpochMilli(timeMs)))
        }
    }
}
