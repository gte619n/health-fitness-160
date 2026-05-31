package com.gte619n.healthfitness.feature.bodycomposition.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Minimal weight-trend line chart drawn as a Canvas polyline. Mirrors the
 * technique from the dashboard's WeightChart but lives in this feature module
 * to avoid coupling to the app module (see IMPL-AND-05 deviation note).
 *
 * @param series y-values, oldest-first.
 * @param minBound optional fixed lower y bound; defaults to the series minimum.
 * @param maxBound optional fixed upper y bound; defaults to the series maximum.
 */
@Composable
fun WeightTrendChart(
    series: List<Double>,
    modifier: Modifier = Modifier,
    minBound: Double? = null,
    maxBound: Double? = null,
) {
    // Capture composition-local color before entering the non-composable DrawScope.
    val accent = Hf.colors.accent
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        if (series.size < 2) return@Canvas
        val minV = minBound ?: series.min()
        val maxV = maxBound ?: series.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (series.size - 1)
        val path = Path()
        series.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (((v - minV) / range) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = accent, style = Stroke(width = 3f))
    }
}
