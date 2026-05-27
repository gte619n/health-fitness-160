package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.domain.blood.MarkerHistoryPoint
import com.gte619n.healthfitness.domain.blood.ReferenceRange
import com.gte619n.healthfitness.ui.theme.Hf

/**
 * Full 12-month history chart for a single marker. Mirrors the
 * dashboard `WeightChart` pattern (Canvas polyline + dashed grid) but
 * overlays the reference band rather than a moving-average line.
 *
 * The Y scale uses the reference range when available so the band is
 * always visible; otherwise the chart auto-fits the data.
 */
@Composable
fun MarkerHistoryChart(
    history: List<MarkerHistoryPoint>,
    reference: ReferenceRange?,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return

    val borderSubtle = Hf.colors.borderSubtle
    val accent = Hf.colors.accent
    val accentArea = accent.copy(alpha = 0.06f)
    val bandColor = Hf.colors.accentBg
    val valueColor = Hf.colors.textPrimary

    val (yMin, yMax) = computeYBounds(history, reference)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val w = size.width
            val h = size.height
            val padX = w * (26f / 600f)
            val padBottom = h * (18f / 160f)
            val padTop = h * (12f / 160f)
            val chartW = w - padX - (12f / 600f) * w
            val chartH = h - padTop - padBottom

            fun x(i: Int) = padX + (i.toFloat() / (history.size - 1).coerceAtLeast(1)) * chartW
            fun y(v: Double): Float {
                val span = (yMax - yMin).takeIf { it > 0.0 } ?: 1.0
                return padTop + ((yMax - v) / span).toFloat() * chartH
            }

            // Reference band — paint as a horizontal stripe across the
            // chart area at the good-zone bounds.
            if (reference != null) {
                val lowerIsBetter = reference.orientation == ReferenceRange.Orientation.LOWER_IS_BETTER
                val topV = if (lowerIsBetter) reference.goodThreshold else yMax
                val botV = if (lowerIsBetter) yMin else reference.goodThreshold
                val yTop = y(topV)
                val yBot = y(botV)
                val top = minOf(yTop, yBot)
                val height = kotlin.math.abs(yBot - yTop)
                drawRect(
                    color = bandColor,
                    topLeft = Offset(padX, top),
                    size = androidx.compose.ui.geometry.Size(chartW, height),
                )
            }

            // Dashed grid lines.
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

            if (history.size >= 2) {
                val area = Path().apply {
                    moveTo(x(0), y(history.first().value))
                    for (i in 1 until history.size) lineTo(x(i), y(history[i].value))
                    lineTo(x(history.size - 1), padTop + chartH)
                    lineTo(x(0), padTop + chartH)
                    close()
                }
                drawPath(area, color = accentArea)

                val line = Path().apply {
                    moveTo(x(0), y(history.first().value))
                    for (i in 1 until history.size) lineTo(x(i), y(history[i].value))
                }
                drawPath(
                    path = line,
                    color = accent,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            val cx = x(history.size - 1)
            val cy = y(history.last().value)
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = valueColor,
                radius = 3.5.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}

private fun computeYBounds(
    history: List<MarkerHistoryPoint>,
    reference: ReferenceRange?,
): Pair<Double, Double> {
    val values = history.map { it.value }
    val dataMin = values.min()
    val dataMax = values.max()
    val refMin = reference?.displayMin ?: dataMin
    val refMax = reference?.displayMax ?: dataMax
    val lo = minOf(dataMin, refMin)
    val hi = maxOf(dataMax, refMax)
    val pad = (hi - lo).coerceAtLeast(1.0) * 0.06
    return (lo - pad) to (hi + pad)
}
