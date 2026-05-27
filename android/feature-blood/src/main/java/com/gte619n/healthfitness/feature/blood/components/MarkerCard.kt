package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.domain.blood.LatestMarker
import com.gte619n.healthfitness.domain.blood.MarkerCatalog
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One marker cell on the overview grid. Tap navigates to the marker
 * detail screen. When `latest.value` is null (no readings for this
 * marker yet) the card renders an "Add reading" placeholder.
 */
@Composable
fun MarkerCard(
    latest: LatestMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HfCard(modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MarkerCatalog.displayName(latest.marker),
                    style = Hf.type.bodyMd.copy(fontSize = 12.sp),
                    color = Hf.colors.textPrimary,
                )
                Text(
                    text = formatSampleDate(latest.sampleDate),
                    style = Hf.type.monoSm.copy(fontSize = 9.sp),
                    color = Hf.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatValue(latest.value),
                    style = Hf.type.monoMd.copy(fontSize = 18.sp),
                    color = valueColor(latest),
                )
                if (latest.value != null && latest.unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = latest.unit,
                        style = Hf.type.bodySm.copy(fontSize = 10.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
                val flag = latest.flag
                if (flag != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = flag.name,
                        tone = if (flag.name == "H") HfTone.Alert else HfTone.Warn,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkerReferenceBar(value = latest.value, reference = latest.reference)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Hf.colors.canvas, RoundedCornerShape(4.dp)),
            ) {
                MiniSparkline(
                    values = latest.history.map { it.value.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
            }
        }
    }
}

@Composable
private fun valueColor(latest: LatestMarker) = when {
    latest.value == null -> Hf.colors.textTertiary
    latest.reference == null -> Hf.colors.textPrimary
    else -> {
        val geom = RangeGeometry.compute(latest.value, latest.reference)
        if (geom.onGoodSide) Hf.colors.good else Hf.colors.alert
    }
}

private fun formatValue(value: Double?): String = when {
    value == null -> "—"
    value < 10.0 -> String.format(Locale.US, "%.1f", value)
    else -> value.toInt().toString()
}

private fun formatSampleDate(date: LocalDate?): String {
    if (date == null) return ""
    return date.format(SHORT).uppercase(Locale.US)
}

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.US)
