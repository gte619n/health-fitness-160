package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Compact in-card sparkline. Auto-fits its y-range to the data and
 * draws a single accent-colored polyline. Mirrors the dashboard's
 * `Sparkline` but doesn't assume a fixed viewBox.
 */
@Composable
fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = Hf.colors.accent
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val padY = 4f
        val xStep = w / (values.size - 1)
        val yMin = values.min()
        val yMax = values.max()
        val span = (yMax - yMin).coerceAtLeast(0.0001f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = i * xStep
            val py = padY + (1f - (v - yMin) / span) * (h - 2 * padY)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
