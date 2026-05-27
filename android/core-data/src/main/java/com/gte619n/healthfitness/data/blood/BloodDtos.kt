package com.gte619n.healthfitness.data.blood

import java.time.Instant
import java.time.LocalDate

/**
 * Wire-shaped DTOs for the blood endpoints. Reflective Moshi adapter
 * handles (de)serialization — field names match the backend records.
 *
 *   GET  /api/me/blood                  → [BloodReadingDto]
 *   POST /api/me/blood                  → [BloodReadingDto]
 *   GET  /api/me/blood/reports          → [BloodTestReportDto] (sans createdAt; see note)
 *   GET  /api/me/blood/reports/{id}     → [BloodTestReportDto]
 *
 * `BloodTestReportResponse` on the backend doesn't ship `createdAt`
 * directly — the field is nullable here for that reason and the Android
 * UI tolerates a missing timestamp by falling back to "Recent" copy.
 */

internal data class BloodReadingDto(
    val readingId: String,
    val marker: String,             // backend BloodMarker enum name
    val value: Double,
    val unit: String,
    val sampleDate: LocalDate,
    val labSource: String?,
    val notes: String?,
    val reference: ReferenceDto,
)

internal data class ReferenceDto(
    val unit: String,
    val orientation: String,         // "LOWER_IS_BETTER" | "HIGHER_IS_BETTER"
    val goodThreshold: Double,
    val displayMin: Double,
    val displayMax: Double,
)

internal data class CreateReadingRequestDto(
    val marker: String,
    val value: Double,
    val unit: String?,
    val sampleDate: LocalDate,
    val labSource: String?,
    val notes: String?,
)

internal data class BloodTestReportDto(
    val reportId: String,
    val sampleDate: LocalDate?,
    val labSource: String?,
    val markers: List<ExtractedMarkerDto>,
    val createdAt: Instant?,
)

internal data class ExtractedMarkerDto(
    val name: String,
    val value: Double?,
    val unit: String?,
    val refRangeLow: Double?,
    val refRangeHigh: Double?,
    val flag: String?,               // "H" | "L" | null
)
