package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.domain.blood.ReferenceRange
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Horizontal reference-range bar with a tick at the current value.
 * Same visual as the dashboard `BloodPanel.RangeBar` — kept in the
 * feature module so the dashboard can stay untouched, but the geometry
 * math is shared via [RangeGeometry] so both screens compute it the
 * same way.
 */
@Composable
fun MarkerReferenceBar(
    value: Double?,
    reference: ReferenceRange?,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val geometry = RangeGeometry.compute(value, reference)
    Box(
        modifier = modifier
            .height(3.dp)
            .background(Hf.colors.canvas),
    ) {
        GoodZone(leftPct = geometry.goodLeftPct, widthPct = geometry.goodFillPct)
        if (value != null) {
            TickMark(tickPct = geometry.tickPct)
        }
    }
}

@Composable
private fun GoodZone(leftPct: Float, widthPct: Float) {
    Layout(
        content = {
            Box(modifier = Modifier.fillMaxHeight().background(Hf.colors.accentBg))
        },
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val w = (maxW * widthPct).toInt().coerceAtLeast(0)
        val left = (maxW * leftPct).toInt().coerceIn(0, maxW)
        val placeable = measurables[0].measure(Constraints.fixed(w, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(left, 0)
        }
    }
}

@Composable
private fun TickMark(tickPct: Float) {
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Hf.colors.textPrimary),
            )
        },
    ) { measurables, constraints ->
        val placeable = measurables[0].measure(Constraints.fixed(2.dp.roundToPx(), constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val xCenter = (constraints.maxWidth * tickPct).toInt()
            placeable.placeRelative(xCenter - placeable.width / 2, 0)
        }
    }
}

/**
 * Pure geometry for the range bar. Computed in one place so the
 * dashboard and the feature-blood UI agree pixel-for-pixel.
 */
data class RangeGeometry(
    val goodLeftPct: Float,
    val goodFillPct: Float,
    val tickPct: Float,
    val onGoodSide: Boolean,
) {
    companion object {
        fun compute(value: Double?, reference: ReferenceRange?): RangeGeometry {
            if (reference == null) {
                return RangeGeometry(0f, 0f, 0f, true)
            }
            val span = (reference.displayMax - reference.displayMin).takeIf { it > 0.0 } ?: 1.0
            val tick = if (value == null) 0.0 else
                ((value - reference.displayMin) / span).coerceIn(0.0, 1.0)
            val lowerIsBetter = reference.orientation == ReferenceRange.Orientation.LOWER_IS_BETTER
            val goodLeft: Double
            val goodFill: Double
            if (lowerIsBetter) {
                goodLeft = 0.0
                goodFill = ((reference.goodThreshold - reference.displayMin) / span)
                    .coerceIn(0.0, 1.0)
            } else {
                val left = ((reference.goodThreshold - reference.displayMin) / span)
                    .coerceIn(0.0, 1.0)
                goodLeft = left
                goodFill = 1.0 - left
            }
            val onGoodSide = if (value == null) true else {
                if (lowerIsBetter) value <= reference.goodThreshold
                else value >= reference.goodThreshold
            }
            return RangeGeometry(
                goodLeftPct = goodLeft.toFloat(),
                goodFillPct = goodFill.toFloat(),
                tickPct = tick.toFloat(),
                onGoodSide = onGoodSide,
            )
        }
    }
}
