package com.gte619n.healthfitness.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Themed empty-state placeholder. Used by lists/feeds that "loaded
 * successfully" but have nothing to show — empty isn't a third UI state in
 * the XUiState shape (see spec), it's just `Loaded(emptyList())` that the
 * screen renders this way.
 */
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Hf.colors.textQuaternary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = Hf.type.headingMd,
            color = Hf.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = Hf.type.bodySm,
                color = Hf.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    HealthFitnessTheme {
        EmptyState(
            title = "No workouts yet",
            description = "Log one from the Today screen to see it here.",
            icon = Icons.Outlined.Inbox,
        )
    }
}
