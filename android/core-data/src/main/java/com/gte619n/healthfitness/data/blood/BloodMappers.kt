package com.gte619n.healthfitness.data.blood

import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.BloodReading
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.domain.blood.ExtractedMarker
import com.gte619n.healthfitness.domain.blood.ReferenceRange

/**
 * Wire ↔ domain conversions for the blood feature. Pure functions,
 * package-private — never escape the data layer.
 */
internal object BloodMappers {

    fun BloodReadingDto.toDomain(): BloodReading = BloodReading(
        readingId = readingId,
        marker = BloodMarker.valueOf(marker),
        value = value,
        unit = unit,
        sampleDate = sampleDate,
        labSource = labSource,
        notes = notes,
        reference = reference.toDomain(),
    )

    fun ReferenceDto.toDomain(): ReferenceRange = ReferenceRange(
        unit = unit,
        orientation = when (orientation) {
            "HIGHER_IS_BETTER" -> ReferenceRange.Orientation.HIGHER_IS_BETTER
            else -> ReferenceRange.Orientation.LOWER_IS_BETTER
        },
        goodThreshold = goodThreshold,
        displayMin = displayMin,
        displayMax = displayMax,
    )

    fun BloodTestReportDto.toDomain(): BloodTestReport = BloodTestReport(
        reportId = reportId,
        sampleDate = sampleDate,
        labSource = labSource.orEmpty(),
        markers = markers.map { it.toDomain() },
        pdfDownloadPath = "/api/me/blood/reports/$reportId/pdf",
        createdAt = createdAt,
    )

    fun ExtractedMarkerDto.toDomain(): ExtractedMarker = ExtractedMarker(
        name = name,
        value = value,
        unit = unit,
        refRangeLow = refRangeLow,
        refRangeHigh = refRangeHigh,
        flag = when (flag) {
            "H" -> ExtractedMarker.Flag.H
            "L" -> ExtractedMarker.Flag.L
            else -> null
        },
    )
}
