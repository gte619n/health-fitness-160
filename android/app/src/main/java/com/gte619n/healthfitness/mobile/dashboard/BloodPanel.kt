package com.gte619n.healthfitness.mobile.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummary
import com.gte619n.healthfitness.domain.dashboard.MarkerTone
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.util.Locale

@Composable
fun BloodPanel(
    markers: List<BloodMarkerSummary>,
    sampleDate: String?,
    showRangeLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    HfCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Blood panel")
                if (sampleDate != null) {
                    Text(
                        text = sampleDate,
                        style = Hf.type.monoSm.copy(fontSize = 9.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            if (markers.isEmpty()) {
                Text(
                    text = "No recent blood readings",
                    style = Hf.type.bodySm.copy(fontSize = 11.sp),
                    color = Hf.colors.textTertiary,
                )
            } else {
                markers.forEachIndexed { i, m ->
                    MarkerRow(marker = m, showLabels = showRangeLabels)
                    if (i != markers.size - 1) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MarkerRow(
    marker: BloodMarkerSummary,
    showLabels: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = marker.displayName,
                style = Hf.type.bodyMd.copy(fontSize = 11.sp),
                color = Hf.colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatValue(marker.value),
                    style = Hf.type.monoMd.copy(fontSize = 12.sp),
                    color = when (marker.tone) {
                        MarkerTone.Warn -> Hf.colors.warn
                        MarkerTone.Alert -> Hf.colors.alert
                        MarkerTone.Good -> Hf.colors.good
                    },
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = marker.unit,
                    style = Hf.type.bodySm.copy(fontSize = 9.sp),
                    color = Hf.colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        RangeBar(
            goodLeftPct = marker.goodLeftPct,
            goodFillPct = marker.goodFillPct,
            tickPct = marker.tickPct,
        )
        if (showLabels) {
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatScale(marker.displayMin), style = Hf.type.monoSm.copy(fontSize = 9.sp), color = Hf.colors.textQuaternary)
                Text(formatScale(marker.goodThreshold), style = Hf.type.monoSm.copy(fontSize = 9.sp), color = Hf.colors.good)
                Text(formatScale(marker.displayMax), style = Hf.type.monoSm.copy(fontSize = 9.sp), color = Hf.colors.textQuaternary)
            }
        }
    }
}

@Composable
private fun RangeBar(goodLeftPct: Float, goodFillPct: Float, tickPct: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Hf.colors.canvas),
    ) {
        // Good-zone fill: positioned at goodLeftPct, width goodFillPct.
        GoodZone(leftPct = goodLeftPct, widthPct = goodFillPct)
        // Tick mark for the current value.
        TickMark(tickPct = tickPct)
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
 * Compact value display:
 *   - 0..10  →  one decimal (5.7 HbA1c)
 *   - >10    →  integer (112 LDL, 92 ApoB)
 *   - >1000  →  integer (650 Testosterone)
 */
private fun formatValue(value: Double): String =
    if (value < 10.0) String.format(Locale.US, "%.1f", value)
    else value.toInt().toString()

private fun formatScale(value: Double): String =
    if (value < 10.0) String.format(Locale.US, "%.1f", value)
    else value.toInt().toString()
