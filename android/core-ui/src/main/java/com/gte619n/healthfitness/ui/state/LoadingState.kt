package com.gte619n.healthfitness.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Themed loading placeholder. The spinner is tinted with our accent and
 * uses a thinner stroke than Material's default — the rest of the app's
 * type system is rendered in 9-13 sp ranges and a 4 dp stroke looks
 * cartoonish next to it.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = Hf.colors.accent,
            strokeWidth = 2.dp,
        )
        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = Hf.type.bodyMd,
                color = Hf.colors.textTertiary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    HealthFitnessTheme {
        LoadingState(label = "Loading...")
    }
}
