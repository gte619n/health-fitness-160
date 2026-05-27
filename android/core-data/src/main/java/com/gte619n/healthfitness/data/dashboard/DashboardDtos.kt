package com.gte619n.healthfitness.data.dashboard

import java.time.Instant
import java.time.LocalDate

/**
 * Wire-shaped DTOs that mirror the backend exactly. Internal — never
 * leak past the mappers. Field names match the backend records 1:1 so
 * the reflective Moshi adapter works without `@Json(name=...)` overrides
 * (see `MoshiContractTest`).
 *
 * The backend response shapes verified:
 *   - GET /api/me/body-composition →
 *     [{ recordId, metric, value, sampleTime, sourcePlatform, recordingMethod }]
 *     where `metric` is the BodyCompositionMetric enum name
 *     (WEIGHT_KG | BODY_FAT_PERCENT | LEAN_MASS_KG | BMI).
 *   - GET /api/me/blood →
 *     [{ readingId, marker, value, unit, sampleDate, labSource, notes,
 *        reference: { unit, orientation, goodThreshold, displayMin, displayMax } }]
 *     where `marker` is the BloodMarker enum name and `orientation` is
 *     "LOWER_IS_BETTER" | "HIGHER_IS_BETTER".
 *   - GET /api/me/medications/today →
 *     [{ medicationId, drugName, imageUrl, window, dose, unit, taken, takenAt }]
 *     where `window` is the TimeWindow enum name
 *     (MORNING | AFTERNOON | EVENING | BEDTIME).
 */

// Reflective Moshi adapter (KotlinJsonAdapterFactory) handles the
// (de)serialisation — no `generateAdapter = true` because IMPL-AND-00
// didn't wire moshi-kotlin-codegen into KSP. Field names match the
// backend exactly, so no @Json(name=...) overrides are needed either.
internal data class BodyCompositionDto(
    val recordId: String,
    val metric: String,
    val value: Double,
    val sampleTime: Instant,
    val sourcePlatform: String?,
    val recordingMethod: String?,
)

// Reflective Moshi adapter (KotlinJsonAdapterFactory) handles the
// (de)serialisation — no `generateAdapter = true` because IMPL-AND-00
// didn't wire moshi-kotlin-codegen into KSP. Field names match the
// backend exactly, so no @Json(name=...) overrides are needed either.
internal data class BloodReadingDto(
    val readingId: String,
    val marker: String,
    val value: Double,
    val unit: String,
    val sampleDate: LocalDate,
    val labSource: String?,
    val notes: String?,
    val reference: ReferenceDto,
)

// Reflective Moshi adapter (KotlinJsonAdapterFactory) handles the
// (de)serialisation — no `generateAdapter = true` because IMPL-AND-00
// didn't wire moshi-kotlin-codegen into KSP. Field names match the
// backend exactly, so no @Json(name=...) overrides are needed either.
internal data class ReferenceDto(
    val unit: String,
    val orientation: String,
    val goodThreshold: Double,
    val displayMin: Double,
    val displayMax: Double,
)

// Reflective Moshi adapter (KotlinJsonAdapterFactory) handles the
// (de)serialisation — no `generateAdapter = true` because IMPL-AND-00
// didn't wire moshi-kotlin-codegen into KSP. Field names match the
// backend exactly, so no @Json(name=...) overrides are needed either.
internal data class TodaysDoseDto(
    val medicationId: String,
    val drugName: String,
    val imageUrl: String?,
    val window: String,
    val dose: Double,
    val unit: String?,
    val taken: Boolean,
    val takenAt: Instant?,
)
