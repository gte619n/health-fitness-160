package com.gte619n.healthfitness.feature.bodycomposition.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.gte619n.healthfitness.domain.bodycomposition.DexaRegion
import com.gte619n.healthfitness.domain.bodycomposition.DexaScan
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Round 2 Stage D coverage for the inline-editable DEXA region grid —
 * one full-grid snapshot. Each row routes through `EditableNumberCell`,
 * so the per-cell read/empty/disabled states are covered by
 * [EditableNumberCellPaparazziTest]; this one pins the overall card
 * layout (header row, nine region rows, divider lines, alignment).
 *
 * Fixture: a plausible male body-comp scan with all nine regions
 * populated. No nulls in this snapshot — the empty / sparse-region case
 * is implicitly covered by the individual `readMode_emptyPlaceholder`
 * snapshot when a region's value is null.
 */
class DexaRegionGridPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NOTNIGHT),
    )

    private fun region(total: Double, lean: Double, fat: Double, fatPct: Double) =
        DexaRegion(
            totalMassLb = total,
            leanTissueLb = lean,
            fatTissueLb = fat,
            regionFatPercent = fatPct,
        )

    private val scan = DexaScan(
        scanId = "scan-1",
        measuredOn = LocalDate.of(2026, 4, 12),
        sourceFacility = "BodySpec",
        totalMassLb = 188.4,
        leanTissueLb = 152.1,
        fatTissueLb = 31.7,
        totalBodyFatPercent = 17.4,
        visceralFatLb = 0.6,
        androidGynoidRatio = 0.92,
        trunk = region(86.0, 70.1, 14.9, 17.3),
        android = region(13.4, 10.9, 2.3, 17.1),
        gynoid = region(20.2, 16.5, 3.5, 17.3),
        armsTotal = region(20.6, 17.2, 3.2, 15.5),
        armsRight = region(10.4, 8.7, 1.6, 15.4),
        armsLeft = region(10.2, 8.5, 1.6, 15.7),
        legsTotal = region(64.8, 53.8, 10.5, 16.2),
        legsRight = region(32.6, 27.0, 5.3, 16.3),
        legsLeft = region(32.2, 26.8, 5.2, 16.1),
        bmdTScore = 0.4,
        bmdZScore = 0.2,
        restingMetabolicRateKcal = 1820,
    )

    @Test
    fun readMode_fullScan() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(12.dp)) {
                    DexaRegionGrid(
                        scan = scan,
                        onPatch = { _, _ -> },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
