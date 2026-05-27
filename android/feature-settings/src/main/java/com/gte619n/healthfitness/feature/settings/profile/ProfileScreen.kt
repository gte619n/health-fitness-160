package com.gte619n.healthfitness.feature.settings.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gte619n.healthfitness.domain.profile.HeightMetric
import com.gte619n.healthfitness.domain.profile.Profile
import com.gte619n.healthfitness.feature.settings.SectionTitle
import com.gte619n.healthfitness.feature.settings.SettingsCard
import com.gte619n.healthfitness.ui.state.ErrorState
import com.gte619n.healthfitness.ui.state.LoadingState
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Profile sub-screen reachable from `SettingsScreen → Profile`. Shows
 * read-only name + email rows sourced from the signed-in Google account
 * and an editable ft/in height pair backed by the backend's `cm`
 * storage.
 */
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ProfileScaffold(
        onNavigateBack = onNavigateBack,
    ) {
        when (val s = state) {
            ProfileViewModel.UiState.Loading -> LoadingState()
            is ProfileViewModel.UiState.Error -> ErrorState(
                message = s.message,
                onRetry = viewModel::refresh,
            )
            is ProfileViewModel.UiState.Loaded -> ProfileLoaded(
                profile = s.profile,
                saving = s.saving,
                saveError = s.saveError,
                onSave = viewModel::saveHeight,
            )
        }
    }
}

@Composable
private fun ProfileScaffold(
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hf.colors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onNavigateBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Hf.colors.textPrimary,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Profile",
                style = Hf.type.headingLg,
                color = Hf.colors.textPrimary,
            )
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ProfileLoaded(
    profile: Profile,
    saving: Boolean,
    saveError: String?,
    onSave: (feet: Int, inches: Int) -> Unit,
) {
    SettingsCard {
        SectionTitle("Identity")
        Spacer(Modifier.height(10.dp))
        ProfileRow(label = "Name", value = profile.displayName ?: "—")
        ProfileRow(label = "Email", value = profile.email ?: "—")
    }
    Spacer(Modifier.height(12.dp))
    SettingsCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        SectionTitle("Height")
        Spacer(Modifier.height(10.dp))
        HeightEditor(
            initial = HeightMetric.cmToFtIn(profile.heightCm),
            saving = saving,
            onSave = onSave,
        )
        if (saveError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = saveError,
                style = Hf.type.bodySm,
                color = Hf.colors.alert,
            )
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = Hf.type.bodyMd, color = Hf.colors.textPrimary)
        Text(text = value, style = Hf.type.bodyMd, color = Hf.colors.textTertiary)
    }
}

@Composable
private fun HeightEditor(
    initial: HeightMetric.FtIn?,
    saving: Boolean,
    onSave: (feet: Int, inches: Int) -> Unit,
) {
    var feet by remember(initial) { mutableStateOf(initial?.feet?.toString() ?: "") }
    var inches by remember(initial) { mutableStateOf(initial?.inches?.toString() ?: "") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberField(
            value = feet,
            onValueChange = { new -> if (new.matches(DIGITS)) feet = new },
            suffix = "ft",
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = inches,
            onValueChange = { new -> if (new.matches(DIGITS)) inches = new },
            suffix = "in",
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val f = feet.toIntOrNull() ?: 0
                val i = inches.toIntOrNull() ?: 0
                onSave(f, i)
            },
            enabled = !saving && (feet.isNotBlank() || inches.isNotBlank()),
            colors = ButtonDefaults.buttonColors(
                containerColor = Hf.colors.accent,
                contentColor = Hf.colors.textInverse,
            ),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    color = Hf.colors.textInverse,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text("Save", style = Hf.type.bodyMd)
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Hf.colors.canvasMuted, RoundedCornerShape(6.dp))
            .border(0.5.dp, Hf.colors.borderDefault, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Hf.type.bodyMd.copy(
                color = Hf.colors.textPrimary,
                textAlign = TextAlign.End,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = " $suffix",
            style = Hf.type.bodySm,
            color = Hf.colors.textTertiary,
        )
    }
}

private val DIGITS = Regex("^\\d{0,3}$")
