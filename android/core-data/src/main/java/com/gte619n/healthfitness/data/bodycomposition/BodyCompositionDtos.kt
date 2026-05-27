package com.gte619n.healthfitness.data.bodycomposition

import java.time.Instant
import java.time.LocalDate

/**
 * Wire-shaped DTOs. Reflective Moshi adapter handles (de)serialization;
 * field names match the backend records exactly (no @Json overrides
 * needed). All numeric fields nullable because vendors omit different
 * subsets of the DEXA report.
 *
 * Backend shapes verified:
 *   GET /api/me/body-composition →
 *     [{ recordId, metric, value, sampleTime, sourcePlatform, recordingMethod }]
 *   GET /api/me/dexa/scans →
 *     [{ scanId, measuredOn, sourceFacility, totalMassLb, leanTissueLb,
 *        fatTissueLb, totalBodyFatPercent, visceralFatLb,
 *        androidGynoidRatio, trunk:{...}, android:{...}, gynoid:{...},
 *        armsTotal:{...}, armsRight:{...}, armsLeft:{...},
 *        legsTotal:{...}, legsRight:{...}, legsLeft:{...},
 *        bmdTScore, bmdZScore, restingMetabolicRateKcal }]
 *   PATCH .../{scanId}/field accepts { path: String, value: Double? } and
 *     returns the same DexaScanResponse shape.
 */

internal data class BodyCompositionReadingDto(
    val recordId: String,
    val metric: String,          // BodyCompositionMetric enum name
    val value: Double,
    val sampleTime: Instant,
    val sourcePlatform: String?,
    val recordingMethod: String?,
)

internal data class DexaRegionDto(
    val totalMassLb: Double?,
    val leanTissueLb: Double?,
    val fatTissueLb: Double?,
    val regionFatPercent: Double?,
)

internal data class DexaScanDto(
    val scanId: String,
    val measuredOn: LocalDate?,
    val sourceFacility: String?,
    val totalMassLb: Double?,
    val leanTissueLb: Double?,
    val fatTissueLb: Double?,
    val totalBodyFatPercent: Double?,
    val visceralFatLb: Double?,
    val androidGynoidRatio: Double?,
    val trunk: DexaRegionDto?,
    val android: DexaRegionDto?,
    val gynoid: DexaRegionDto?,
    val armsTotal: DexaRegionDto?,
    val armsRight: DexaRegionDto?,
    val armsLeft: DexaRegionDto?,
    val legsTotal: DexaRegionDto?,
    val legsRight: DexaRegionDto?,
    val legsLeft: DexaRegionDto?,
    val bmdTScore: Double?,
    val bmdZScore: Double?,
    val restingMetabolicRateKcal: Int?,
)

internal data class PatchFieldRequest(
    val path: String,
    val value: Double?,
)
