package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Small set of reusable atoms specific to the blood feature. Mirrors
 * the dashboard's `Pill` + `HfCard` look without depending on `:app`'s
 * private dashboard package.
 */

enum class HfTone { Good, Warn, Alert, Neutral }

@Composable
fun Pill(
    text: String,
    tone: HfTone,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (tone) {
        HfTone.Good -> Hf.colors.goodBg to Hf.colors.accentDim
        HfTone.Warn -> Hf.colors.warnBg to Hf.colors.warn
        HfTone.Alert -> Hf.colors.alertBg to Hf.colors.alert
        HfTone.Neutral -> Hf.colors.canvas to Hf.colors.textSecondary
    }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = Hf.type.capsSm,
            color = fg,
        )
    }
}

@Composable
fun HfCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(0.5.dp, Hf.colors.borderDefault, RoundedCornerShape(10.dp))
            .background(Hf.colors.surface, RoundedCornerShape(10.dp)),
    ) {
        content()
    }
}

@Composable
fun CapsLabel(
    text: String,
    color: Color = Hf.colors.textTertiary,
    sizeSp: Int = 10,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = Hf.type.capsMd.copy(fontSize = sizeSp.sp),
        color = color,
        modifier = modifier,
    )
}
