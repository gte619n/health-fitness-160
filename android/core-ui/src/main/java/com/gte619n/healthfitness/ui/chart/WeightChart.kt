package com.gte619n.healthfitness.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Weight trend chart. Promoted from `app/.../dashboard/` into `core-ui`
 * by IMPL-AND-05 so the dashboard hero and the body-composition overview
 * screen render the same widget. The 7-day moving-average overlay is
 * computed here from the input series so callers don't carry redundant
 * data.
 *
 * Signature is presentation-only (`series`, `yMin`, `yMax`) — date/x-label
 * formatting stays in the data layer (`WeightHeroDisplay.buildXLabels`,
 * Round 2 Stage C) and is rendered above/below the chart by the calling
 * screen.
 */
@Composable
fun WeightChart(
    series: List<Float>,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    val safeYMin = if (yMax > yMin) yMin else yMin - 1f
    val safeYMax = if (yMax > yMin) yMax else yMin + 1f
    val ma = movingAverage(series, 7)
    val borderSubtle = Hf.colors.borderSubtle
    val accent = Hf.colors.accent
    val accentArea = accent.copy(alpha = 0.06f)
    val maColor = Hf.colors.textPrimary.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            val padX = w * (26f / 600f)
            val padBottom = h * (22f / 140f)
            val padTop = h * (14f / 140f)
            val chartW = w - padX - (12f / 600f) * w
            val chartH = h - padTop - padBottom

            fun x(i: Int) = padX + (i.toFloat() / (series.size - 1).coerceAtLeast(1)) * chartW
            fun y(v: Float) = padTop + ((safeYMax - v) / (safeYMax - safeYMin)) * chartH

            // Dashed grid — three lines evenly spaced inside the chart area.
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f), 0f)
            val gridDivisions = 3
            (1..gridDivisions).forEach { i ->
                val py = padTop + (i.toFloat() / (gridDivisions + 1)) * chartH
                drawLine(
                    color = borderSubtle,
                    start = Offset(0f, py),
                    end = Offset(w, py),
                    strokeWidth = 0.5.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }

            if (series.size >= 2) {
                val areaPath = Path().apply {
                    moveTo(x(0), y(series.first()))
                    for (i in 1 until series.size) lineTo(x(i), y(series[i]))
                    lineTo(x(series.size - 1), padTop + chartH)
                    lineTo(x(0), padTop + chartH)
                    close()
                }
                drawPath(areaPath, color = accentArea)

                val linePath = Path().apply {
                    moveTo(x(0), y(series.first()))
                    for (i in 1 until series.size) lineTo(x(i), y(series[i]))
                }
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                val maPath = Path().apply {
                    moveTo(x(0), y(ma.first()))
                    for (i in 1 until ma.size) lineTo(x(i), y(ma[i]))
                }
                drawPath(
                    path = maPath,
                    color = maColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f),
                    ),
                )
            }

            val cx = x(series.size - 1)
            val cy = y(series.last())
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = accent,
                radius = 3.5.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}

private fun movingAverage(series: List<Float>, window: Int): List<Float> {
    return series.indices.map { i ->
        val start = maxOf(0, i - (window - 1))
        val slice = series.subList(start, i + 1)
        slice.sum() / slice.size
    }
}
