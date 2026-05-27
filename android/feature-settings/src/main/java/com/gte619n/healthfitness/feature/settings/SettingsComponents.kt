package com.gte619n.healthfitness.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Surface card used by the settings cards. Visually matches the
 * `HfCard` in `app/dashboard/Components.kt` — a 0.5 dp border, 10 dp
 * radius, surface fill — but lives here so this module doesn't depend
 * on `app/`.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(0.5.dp, Hf.colors.borderDefault, RoundedCornerShape(10.dp))
            .background(Hf.colors.surface, RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = Hf.type.capsMd,
        color = Hf.colors.textTertiary,
    )
}
