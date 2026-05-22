package com.gte619n.healthfitness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tesseta mark — 3x3 grid with center omitted. Geometry mirrors the
// app-icon SVGs in docs/logo/: 120-unit viewBox, 16.5 tiles on a 17.75
// pitch starting at (34, 34), squircle radius 26.
//
// Variants follow the approved combos in LOGO-SPEC.md.

enum class TessetaMarkVariant {
    /** Olive tiles on oatmeal squircle. */
    LIGHT,

    /** Cream tiles on ink squircle. */
    DARK,

    /** Cream tiles on olive squircle. */
    OLIVE,
}

private data class MarkColors(val tile: Color, val ground: Color)

private fun colorsFor(variant: TessetaMarkVariant): MarkColors = when (variant) {
    TessetaMarkVariant.LIGHT -> MarkColors(tile = Color(0xFF5C7A2E), ground = Color(0xFFF0EBE0))
    TessetaMarkVariant.DARK -> MarkColors(tile = Color(0xFFF0EBE0), ground = Color(0xFF1F2419))
    TessetaMarkVariant.OLIVE -> MarkColors(tile = Color(0xFFF0EBE0), ground = Color(0xFF5C7A2E))
}

private const val VIEW_BOX = 120f
private const val TILE_SIZE = 16.5f
private const val TILE_PITCH = 17.75f
private const val ORIGIN = 34f
private const val TILE_RADIUS = 2.5f
private const val GROUND_RADIUS = 26f

private val TILE_POSITIONS = listOf(
    Pair(0, 0), Pair(1, 0), Pair(2, 0),
    Pair(0, 1), /* center omitted */ Pair(2, 1),
    Pair(0, 2), Pair(1, 2), Pair(2, 2),
)

@Composable
fun TessetaMark(
    variant: TessetaMarkVariant = TessetaMarkVariant.LIGHT,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val colors = colorsFor(variant)
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.width / VIEW_BOX
        // Ground squircle
        drawRoundRect(
            color = colors.ground,
            topLeft = Offset.Zero,
            size = Size(this.size.width, this.size.height),
            cornerRadius = CornerRadius(GROUND_RADIUS * scale, GROUND_RADIUS * scale),
        )
        // Tiles
        TILE_POSITIONS.forEach { (col, row) ->
            drawRoundRect(
                color = colors.tile,
                topLeft = Offset(
                    (ORIGIN + col * TILE_PITCH) * scale,
                    (ORIGIN + row * TILE_PITCH) * scale,
                ),
                size = Size(TILE_SIZE * scale, TILE_SIZE * scale),
                cornerRadius = CornerRadius(TILE_RADIUS * scale, TILE_RADIUS * scale),
            )
        }
    }
}
