package com.gte619n.healthfitness.domain.dashboard

import java.time.Instant
import java.time.LocalDate

/**
 * Domain models the dashboard ViewModel and the Compose layer speak.
 *
 * Pure Kotlin — no Android, no Retrofit, no Moshi. The Retrofit DTOs in
 * `core-data` map *into* these on the IO dispatcher; everything north of
 * the repository never sees a wire-shaped type.
 *
 * Round 2 Stage C: body-composition types (WeightSummary, BodyMetric,
 * BodyCompositionPoint, ChartXLabel) were retired from this package.
 * The canonical body-composition domain lives in
 * `com.gte619n.healthfitness.domain.bodycomposition`; the dashboard
 * hero builds a lb-shaped `WeightHeroDisplay` from the snapshot at
 * render time.
 */

enum class MarkerTone { Good, Warn, Alert }

data class HistoryPoint(val date: LocalDate, val value: Double)

/**
 * One marker row on the dashboard blood panel. All the geometry the bar
 * needs to render (tickPct, goodFillPct, goodLeftPct, tone) is computed
 * by the mapper so the Compose layer is purely declarative.
 */
data class BloodMarkerSummary(
    /** Canonical backend marker key — e.g. "LDL", "APO_B". */
    val markerKey: String,
    /** Display name — "LDL", "ApoB", "HbA1c", "Testosterone". */
    val displayName: String,
    val value: Double,
    val unit: String,
    val tone: MarkerTone,
    /** Width of the "good zone" fill rectangle as a 0..1 fraction. */
    val goodFillPct: Float,
    /** Left offset of the "good zone" fill as a 0..1 fraction. */
    val goodLeftPct: Float,
    /** Position of the value tick as a 0..1 fraction, clamped to [0, 1]. */
    val tickPct: Float,
    val displayMin: Double,
    val goodThreshold: Double,
    val displayMax: Double,
    val history: List<HistoryPoint>,
)

enum class DoseWindow { MORNING, AFTERNOON, EVENING, BEDTIME }

data class TodaysDoseSummary(
    val medicationId: String,
    val drugName: String,
    val imageUrl: String?,
    val window: DoseWindow,
    val dose: Double,
    val unit: String?,
    val taken: Boolean,
    val takenAt: Instant?,
)
