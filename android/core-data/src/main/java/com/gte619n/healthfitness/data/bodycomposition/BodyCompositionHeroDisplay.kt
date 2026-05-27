package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionMetric
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionSnapshot
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * One x-axis label on the weight sparkline. Position is a 0..1 fraction
 * across the chart's x-axis; `label` is a short date string ("MAY 20").
 *
 * Promoted from the retired `domain.dashboard.ChartXLabel` so the
 * dashboard hero (and any future caller) can still render evenly-spaced
 * date ticks above/below the chart from the same downsampled series.
 */
data class ChartXLabel(val xFraction: Float, val label: String)

/**
 * Chart-ready, lb-converted view over a [BodyCompositionSnapshot]. The
 * snapshot is the canonical kg-based domain model; this display struct
 * is what the dashboard hero card actually renders. Splitting them keeps
 * the snapshot pure (no unit conversion, no downsampling, no padding)
 * while the hero gets a single value to read from.
 *
 * Matches the math previously in the retired
 * `core-data/dashboard/BodyCompositionMapper` so the post-consolidation
 * hero numbers + chart geometry are byte-for-byte identical to the
 * pre-consolidation render. Verified by [BodyCompositionHeroDisplayTest].
 *
 * `null` factory result means "no weight readings at all" — the hero
 * renders its "Connect Google Health" empty state in that case.
 */
data class WeightHeroDisplay(
    val latestLb: Double,
    val sevenDayDeltaLb: Double?,
    val ninetyDayDeltaLb: Double?,
    /** Downsampled to at most [TARGET_POINTS] evenly-bucketed points, in lb. */
    val series: List<Double>,
    val yMin: Double,
    val yMax: Double,
    val xLabels: List<ChartXLabel>,
    val latestBodyFatPct: Double?,
    val latestLeanMassLb: Double?,
) {
    companion object {
        internal const val KG_TO_LB = 2.20462
        internal const val TARGET_POINTS = 30
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * Build a [WeightHeroDisplay] from a [BodyCompositionSnapshot].
         *
         * Returns `null` when the snapshot has no weight reading at all
         * (caller renders empty state). The conversion / downsample /
         * padding math mirrors the retired
         * `BodyCompositionMapper.toWeightSummary` exactly so the hero
         * numbers don't shift visually across the consolidation.
         *
         * Note on deltas: the snapshot already carries `sevenDayDeltaKg`
         * and `ninetyDayDeltaKg`. We re-derive the 7-day delta here from
         * the raw window to match the legacy "latest minus reading
         * at-or-before now-7d" semantics, then convert kg → lb. The
         * snapshot's 90d delta convention (latest minus mean(window)
         * when no 90d-prior anchor exists) is already aligned with the
         * legacy mapper for short histories — we convert it through as-is.
         */
        fun from(
            snapshot: BodyCompositionSnapshot,
            now: Instant = Instant.now(),
        ): WeightHeroDisplay? {
            val latestKg = snapshot.latestWeightKg ?: return null
            val weights = snapshot.series90d
                .filter { it.metric == BodyCompositionMetric.WEIGHT_KG }
                .sortedBy { it.sampleTime }
            if (weights.isEmpty()) return null

            val pointsLb = weights.map {
                it.sampleTime.toEpochMilli() to (it.value * KG_TO_LB)
            }
            val seriesAllLb = pointsLb.map { it.second }
            val latestLb = latestKg * KG_TO_LB

            // 7d delta: latest − reading at-or-before (now − 7d), null when
            // no such anchor exists. Mirrors the retired mapper exactly.
            val nowMs = now.toEpochMilli()
            val sevenDaysAgoMs = nowMs - SEVEN_DAYS_MS
            val sevenDayReference = pointsLb.lastOrNull { it.first <= sevenDaysAgoMs }
            val sevenDayDeltaLb = sevenDayReference?.let { latestLb - it.second }

            // 90d delta — convert the snapshot's kg delta to lb so the
            // single source of truth for the kg figure remains the snapshot.
            val ninetyDayDeltaLb = snapshot.ninetyDayDeltaKg?.let { it * KG_TO_LB }

            val downsampled = downsample(seriesAllLb, TARGET_POINTS)

            val rawMin = downsampled.min()
            val rawMax = downsampled.max()
            val padding = max(1.0, (rawMax - rawMin) * 0.15)
            val yMin = floor(rawMin - padding)
            val yMax = ceil(rawMax + padding)

            val xLabels = buildXLabels(pointsLb)

            return WeightHeroDisplay(
                latestLb = latestLb,
                sevenDayDeltaLb = sevenDayDeltaLb,
                ninetyDayDeltaLb = ninetyDayDeltaLb,
                series = downsampled,
                yMin = yMin,
                yMax = yMax,
                xLabels = xLabels,
                latestBodyFatPct = snapshot.latestBodyFatPercent,
                latestLeanMassLb = snapshot.latestLeanMassKg?.let { it * KG_TO_LB },
            )
        }

        /**
         * Bucket-average downsample. Returns at most [targetPoints] entries;
         * inputs shorter than [targetPoints] pass through unchanged. Public
         * for parity tests against the retired mapper.
         */
        internal fun downsample(series: List<Double>, targetPoints: Int): List<Double> {
            if (series.size <= targetPoints) return series
            val bucketSize = series.size.toDouble() / targetPoints
            val buckets = mutableListOf<Double>()
            var i = 0
            while (i < targetPoints) {
                val start = floor(i * bucketSize).toInt()
                val end = min(
                    series.size,
                    floor((i + 1) * bucketSize).toInt().coerceAtLeast(start + 1),
                )
                val slice = series.subList(start, end)
                buckets.add(slice.sum() / slice.size)
                i++
            }
            return buckets
        }

        /**
         * Four roughly-evenly spaced x-axis ticks at the same fractions
         * the web uses (32, 180, 335, 500 in a 600-px viewBox). Indices
         * land at 0, n/3, 2n/3, n-1 in the input point list. Returns
         * empty when the input has fewer than two points (no chart to
         * label).
         */
        internal fun buildXLabels(pointsLb: List<Pair<Long, Double>>): List<ChartXLabel> {
            if (pointsLb.size < 2) return emptyList()
            val xFractions = floatArrayOf(
                32f / 600f,
                180f / 600f,
                335f / 600f,
                500f / 600f,
            )
            val n = pointsLb.size
            val idxs = intArrayOf(0, n / 3, (2 * n) / 3, n - 1)
            return idxs.zip(xFractions.toList()).map { (idx, frac) ->
                val (timeMs, _) = pointsLb[min(idx, n - 1)]
                val label = LABEL_FORMATTER.format(Instant.ofEpochMilli(timeMs))
                    .uppercase(Locale.US)
                ChartXLabel(xFraction = frac, label = label)
            }
        }

        private val LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd", Locale.US).withZone(ZoneOffset.UTC)
    }
}

