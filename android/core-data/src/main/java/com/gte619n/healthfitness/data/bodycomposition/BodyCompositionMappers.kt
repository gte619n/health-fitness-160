package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionMetric
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionPoint
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionSnapshot
import com.gte619n.healthfitness.domain.bodycomposition.DexaRegion
import com.gte619n.healthfitness.domain.bodycomposition.DexaScan
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanSummary
import java.time.Instant
import kotlin.math.abs

/**
 * DTO → domain conversion. Pure functions, no Android imports, fully
 * testable. Snapshot construction lives here (not in the repo impl) so
 * tests can exercise the math without standing up Retrofit.
 */
internal object BodyCompositionMappers {

    private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    private const val SIX_HOURS_MS = 6L * 60 * 60 * 1000

    fun BodyCompositionReadingDto.toDomain(): BodyCompositionPoint =
        BodyCompositionPoint(
            recordId = recordId,
            metric = runCatching { BodyCompositionMetric.valueOf(metric) }
                .getOrDefault(BodyCompositionMetric.WEIGHT_KG),
            value = value,
            sampleTime = sampleTime,
            sourcePlatform = sourcePlatform,
            recordingMethod = recordingMethod,
        )

    /**
     * Aggregate raw points into a snapshot. Latest-of-each-metric +
     * 7d / 90d weight deltas + 90-day weight series (oldest-first).
     *
     * Empty input → all-null snapshot with empty series.
     */
    fun buildSnapshot(
        points: List<BodyCompositionPoint>,
        now: Instant = Instant.now(),
    ): BodyCompositionSnapshot {
        val nowMs = now.toEpochMilli()

        val weights = points.filter { it.metric == BodyCompositionMetric.WEIGHT_KG }
            .sortedBy { it.sampleTime }
        val latestWeight = weights.lastOrNull()
        val series90d = weights.filter {
            it.sampleTime.toEpochMilli() >= nowMs - NINETY_DAYS_MS
        }

        val sevenDayDelta = latestWeight?.let { latest ->
            val anchor = weights
                .filter { it.sampleTime.toEpochMilli() <= nowMs - SEVEN_DAYS_MS }
                .maxByOrNull { it.sampleTime }
            anchor?.let { latest.value - it.value }
        }
        val ninetyDayDelta = latestWeight?.let { latest ->
            val anchor = weights
                .filter { it.sampleTime.toEpochMilli() <= nowMs - NINETY_DAYS_MS }
                .maxByOrNull { it.sampleTime }
            anchor?.let { latest.value - it.value }
                ?: if (weights.size >= 2) {
                    // Fall back to "vs window mean" when there's no 90d-prior
                    // anchor (sparse history) — keeps a delta visible
                    // instead of N/A on a 30-day-old account.
                    val mean = weights.sumOf { it.value } / weights.size
                    latest.value - mean
                } else null
        }

        val latestBf = points.filter { it.metric == BodyCompositionMetric.BODY_FAT_PERCENT }
            .maxByOrNull { it.sampleTime }
        val latestLeanFromApi = points.filter { it.metric == BodyCompositionMetric.LEAN_MASS_KG }
            .maxByOrNull { it.sampleTime }
        val latestBmi = points.filter { it.metric == BodyCompositionMetric.BMI }
            .maxByOrNull { it.sampleTime }

        // Prefer the API-supplied lean mass; fall back to derivation from
        // weight × (1 - bf%) when paired within 6 hours.
        val latestLeanKg = latestLeanFromApi?.value
            ?: deriveLeanMassKg(weights, latestBf)

        return BodyCompositionSnapshot(
            latestWeightKg = latestWeight?.value,
            latestBodyFatPercent = latestBf?.value,
            latestLeanMassKg = latestLeanKg,
            latestBmi = latestBmi?.value,
            latestSampleTime = latestWeight?.sampleTime
                ?: latestBf?.sampleTime
                ?: latestLeanFromApi?.sampleTime
                ?: latestBmi?.sampleTime,
            sevenDayDeltaKg = sevenDayDelta,
            ninetyDayDeltaKg = ninetyDayDelta,
            series90d = series90d,
        )
    }

    private fun deriveLeanMassKg(
        weights: List<BodyCompositionPoint>,
        latestBf: BodyCompositionPoint?,
    ): Double? {
        if (latestBf == null) return null
        val bfMs = latestBf.sampleTime.toEpochMilli()
        val pair = weights.minByOrNull { abs(it.sampleTime.toEpochMilli() - bfMs) }
            ?: return null
        if (abs(pair.sampleTime.toEpochMilli() - bfMs) > SIX_HOURS_MS) return null
        return pair.value * (1.0 - latestBf.value / 100.0)
    }

    fun DexaScanDto.toSummary(): DexaScanSummary = DexaScanSummary(
        scanId = scanId,
        measuredOn = measuredOn,
        sourceFacility = sourceFacility,
        totalMassLb = totalMassLb,
        totalBodyFatPercent = totalBodyFatPercent,
    )

    fun DexaScanDto.toDomain(): DexaScan = DexaScan(
        scanId = scanId,
        measuredOn = measuredOn,
        sourceFacility = sourceFacility,
        totalMassLb = totalMassLb,
        leanTissueLb = leanTissueLb,
        fatTissueLb = fatTissueLb,
        totalBodyFatPercent = totalBodyFatPercent,
        visceralFatLb = visceralFatLb,
        androidGynoidRatio = androidGynoidRatio,
        trunk = trunk?.toDomain(),
        android = android?.toDomain(),
        gynoid = gynoid?.toDomain(),
        armsTotal = armsTotal?.toDomain(),
        armsRight = armsRight?.toDomain(),
        armsLeft = armsLeft?.toDomain(),
        legsTotal = legsTotal?.toDomain(),
        legsRight = legsRight?.toDomain(),
        legsLeft = legsLeft?.toDomain(),
        bmdTScore = bmdTScore,
        bmdZScore = bmdZScore,
        restingMetabolicRateKcal = restingMetabolicRateKcal,
    )

    private fun DexaRegionDto.toDomain(): DexaRegion = DexaRegion(
        totalMassLb = totalMassLb,
        leanTissueLb = leanTissueLb,
        fatTissueLb = fatTissueLb,
        regionFatPercent = regionFatPercent,
    )
}
