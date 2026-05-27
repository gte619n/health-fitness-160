package com.gte619n.healthfitness.feature.workouts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.gte619n.healthfitness.domain.workouts.EquipmentSpec
import com.gte619n.healthfitness.domain.workouts.SpecSchemaTag
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import org.junit.Rule
import org.junit.Test

/**
 * Round 2 Stage D coverage for the per-category equipment spec form.
 * Six snapshots, one per `SpecSchemaTag` value — each rendered with a
 * realistic seed so the form fields are visually distinct and the
 * Bodyweight / Cardio variants render their no-numeric branches.
 *
 * Each snapshot wraps the form in a fixed-width box so the
 * OutlinedTextField row geometry is deterministic.
 */
class EquipmentSpecFormPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NOTNIGHT),
    )

    private fun snapshot(schema: SpecSchemaTag, spec: EquipmentSpec) {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(360.dp)) {
                    EquipmentSpecForm(
                        schema = schema,
                        current = spec,
                        onChange = {},
                    )
                }
            }
        }
    }

    @Test
    fun selectorized() {
        snapshot(
            SpecSchemaTag.SELECTORIZED,
            EquipmentSpec.Selectorized(
                minWeight = 10.0,
                maxWeight = 200.0,
                increment = 5.0,
            ),
        )
    }

    @Test
    fun plateLoaded() {
        snapshot(
            SpecSchemaTag.PLATE_LOADED,
            EquipmentSpec.PlateLoaded(
                barWeight = 45.0,
                availablePlates = listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0),
            ),
        )
    }

    @Test
    fun cable() {
        snapshot(
            SpecSchemaTag.CABLE,
            EquipmentSpec.Cable(
                weightStack = 200.0,
                numStations = 2,
            ),
        )
    }

    @Test
    fun cardio() {
        snapshot(
            SpecSchemaTag.CARDIO,
            EquipmentSpec.Cardio(
                resistanceLevels = 20,
                hasIncline = true,
            ),
        )
    }

    @Test
    fun bodyweight() {
        snapshot(
            SpecSchemaTag.BODYWEIGHT,
            EquipmentSpec.Bodyweight,
        )
    }

    @Test
    fun weightSet() {
        snapshot(
            SpecSchemaTag.WEIGHT_SET,
            EquipmentSpec.WeightSet(
                minWeight = 5.0,
                maxWeight = 50.0,
                increment = 5.0,
                weights = listOf(10.0, 15.0, 20.0, 25.0),
            ),
        )
    }
}
