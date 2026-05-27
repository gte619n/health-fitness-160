package com.gte619n.healthfitness.feature.bodycomposition.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import org.junit.Rule
import org.junit.Test

/**
 * Round 2 Stage D coverage for the inline-editable PATCH cell used by
 * the DEXA region grid. Three snapshots covering the user-visible states:
 *
 *  - [readMode_withValue] — formatted value + tappable affordance.
 *  - [readMode_emptyPlaceholder] — em-dash for null values.
 *  - [readMode_disabledWhileSaving] — `enabled = false` is the visual
 *    used by the parent grid while a PATCH is in flight (optimistic UI
 *    drops the tap target so a second tap can't fire another save).
 *
 * The actual "edit" (BasicTextField visible) and "error revert" states
 * are gated on focus/tap events Paparazzi can't fire, so they're left
 * for the underlying `EditableNumber` interaction tests to cover.
 */
class EditableNumberCellPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NOTNIGHT),
    )

    @Test
    fun readMode_withValue() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(140.dp)) {
                    EditableNumberCell(
                        value = 38.4,
                        onSave = {},
                        decimals = 1,
                        suffix = "lb",
                    )
                }
            }
        }
    }

    @Test
    fun readMode_emptyPlaceholder() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(140.dp)) {
                    EditableNumberCell(
                        value = null,
                        onSave = {},
                        decimals = 1,
                        suffix = "lb",
                    )
                }
            }
        }
    }

    @Test
    fun readMode_disabledWhileSaving() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(140.dp)) {
                    EditableNumberCell(
                        value = 38.4,
                        onSave = {},
                        decimals = 1,
                        suffix = "lb",
                        enabled = false,
                    )
                }
            }
        }
    }
}
