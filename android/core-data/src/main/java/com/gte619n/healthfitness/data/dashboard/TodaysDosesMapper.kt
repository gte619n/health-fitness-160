package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.domain.dashboard.DoseWindow
import com.gte619n.healthfitness.domain.dashboard.TodaysDoseSummary

internal object TodaysDosesMapper {
    fun toDomain(dto: TodaysDoseDto): TodaysDoseSummary = TodaysDoseSummary(
        medicationId = dto.medicationId,
        drugName = dto.drugName,
        imageUrl = dto.imageUrl,
        window = parseWindow(dto.window),
        dose = dto.dose,
        unit = dto.unit,
        taken = dto.taken,
        takenAt = dto.takenAt,
    )

    /**
     * Unknown windows fall back to MORNING. The backend enum is sealed
     * but a stale Android client against a newer backend shouldn't crash —
     * MORNING is the safest default for sort order (renders at the top).
     */
    private fun parseWindow(raw: String): DoseWindow = try {
        DoseWindow.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        DoseWindow.MORNING
    }
}
