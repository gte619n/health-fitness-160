package com.gte619n.healthfitness.feature.blood.marker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.gte619n.healthfitness.domain.blood.ReferenceRange
import com.gte619n.healthfitness.feature.blood.components.MarkerReferenceBar
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import org.junit.Rule
import org.junit.Test

/**
 * Round 2 Stage D visual coverage for the reference-range bar used on the
 * marker overview rows and the marker detail screen. Geometry is computed
 * by [com.gte619n.healthfitness.feature.blood.components.RangeGeometry]
 * and the three snapshots pin the production-reachable variants:
 *
 *  - [inRange_lowerIsBetter] — LDL 88 mg/dL with a 100 mg/dL threshold:
 *    tick sits inside the green band, no value warning.
 *  - [outOfRange_lowerIsBetter_high] — LDL 145 mg/dL with the same range:
 *    tick lands outside the green band on the high side, mimicking what
 *    a worse-than-target reading renders as.
 *  - [outOfRange_higherIsBetter_low] — HDL 32 mg/dL with a 40 mg/dL
 *    threshold: tick sits below the green band, mimicking the inverse
 *    "lower side is bad" orientation.
 *
 * The bar fills its parent's width, so the test wraps it in a fixed-width
 * box for a deterministic render even if `DeviceConfig.PIXEL_5` ever
 * resizes; the bar height comes from its internal `3.dp` default.
 */
class MarkerReferenceBarPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NOTNIGHT),
    )

    private val ldlReference = ReferenceRange(
        unit = "mg/dL",
        orientation = ReferenceRange.Orientation.LOWER_IS_BETTER,
        goodThreshold = 100.0,
        displayMin = 40.0,
        displayMax = 200.0,
    )

    private val hdlReference = ReferenceRange(
        unit = "mg/dL",
        orientation = ReferenceRange.Orientation.HIGHER_IS_BETTER,
        goodThreshold = 40.0,
        displayMin = 20.0,
        displayMax = 90.0,
    )

    @Test
    fun inRange_lowerIsBetter() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(240.dp)) {
                    MarkerReferenceBar(
                        value = 88.0,
                        reference = ldlReference,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    @Test
    fun outOfRange_lowerIsBetter_high() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(240.dp)) {
                    MarkerReferenceBar(
                        value = 145.0,
                        reference = ldlReference,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    @Test
    fun outOfRange_higherIsBetter_low() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                Box(Modifier.padding(16.dp).width(240.dp)) {
                    MarkerReferenceBar(
                        value = 32.0,
                        reference = hdlReference,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
