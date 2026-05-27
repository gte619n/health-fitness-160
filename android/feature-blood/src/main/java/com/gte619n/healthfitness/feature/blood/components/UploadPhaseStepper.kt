package com.gte619n.healthfitness.feature.blood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Three-phase upload indicator: Uploading → Extracting → Saving.
 * The currently-active phase is the accent color; completed phases stay
 * accent-colored; pending phases are muted.
 */
enum class UploadPhase { Uploading, Extracting, Saving, Done }

@Composable
fun UploadPhaseStepper(
    active: UploadPhase,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Step(label = "Upload", filled = active.ordinal >= UploadPhase.Uploading.ordinal)
        Connector(filled = active.ordinal >= UploadPhase.Extracting.ordinal)
        Step(label = "Extract", filled = active.ordinal >= UploadPhase.Extracting.ordinal)
        Connector(filled = active.ordinal >= UploadPhase.Saving.ordinal)
        Step(label = "Save", filled = active.ordinal >= UploadPhase.Saving.ordinal)
    }
}

@Composable
private fun Step(label: String, filled: Boolean) {
    val color = if (filled) Hf.colors.accent else Hf.colors.borderStrong
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = Hf.type.bodySm.copy(fontSize = 11.sp),
            color = if (filled) Hf.colors.textPrimary else Hf.colors.textTertiary,
        )
    }
}

@Composable
private fun Connector(filled: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(2.dp)
            .width(28.dp)
            .background(if (filled) Hf.colors.accent else Hf.colors.borderSubtle),
    )
}
