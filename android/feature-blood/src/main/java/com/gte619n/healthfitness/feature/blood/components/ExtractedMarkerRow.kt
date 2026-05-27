package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.domain.blood.ExtractedMarker
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.util.Locale

/**
 * One row on the report-detail extracted-markers list. Renders name,
 * value+unit, an optional flag pill, and the ref-range as a small
 * "0–100" annotation when the lab printed one.
 */
@Composable
fun ExtractedMarkerRow(marker: ExtractedMarker, modifier: Modifier = Modifier) {
    Column(modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp, horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = marker.name,
                style = Hf.type.bodyMd.copy(fontSize = 13.sp),
                color = Hf.colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatValue(marker.value),
                    style = Hf.type.monoMd.copy(fontSize = 13.sp),
                    color = if (marker.flag == ExtractedMarker.Flag.H) Hf.colors.alert
                    else if (marker.flag == ExtractedMarker.Flag.L) Hf.colors.warn
                    else Hf.colors.textPrimary,
                )
                val unit = marker.unit
                if (!unit.isNullOrBlank()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = Hf.type.bodySm.copy(fontSize = 10.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
                if (marker.flag != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = if (marker.flag == ExtractedMarker.Flag.H) "HIGH" else "LOW",
                        tone = if (marker.flag == ExtractedMarker.Flag.H) HfTone.Alert else HfTone.Warn,
                    )
                }
            }
        }
        if (marker.refRangeLow != null || marker.refRangeHigh != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Ref: ${formatBound(marker.refRangeLow)}–${formatBound(marker.refRangeHigh)}",
                style = Hf.type.monoSm.copy(fontSize = 10.sp),
                color = Hf.colors.textTertiary,
            )
        }
    }
}

private fun formatValue(value: Double?): String = when {
    value == null -> "—"
    value < 10.0 -> String.format(Locale.US, "%.1f", value)
    else -> value.toInt().toString()
}

private fun formatBound(v: Double?): String = when {
    v == null -> "—"
    v < 10.0 -> String.format(Locale.US, "%.1f", v)
    else -> v.toInt().toString()
}
