package com.gte619n.healthfitness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gte619n.healthfitness.feature.settings.about.AboutSection
import com.gte619n.healthfitness.feature.settings.googlehealth.GoogleHealthSection
import com.gte619n.healthfitness.feature.settings.units.UnitsSection
import com.gte619n.healthfitness.ui.components.HfCard
import com.gte619n.healthfitness.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
            SectionTitle(text = "Settings")
        }

        // Profile entry card.
        HfCard(transparent = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Profile")
                OutlinedButton(
                    onClick = onNavigateToProfile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit profile")
                }
            }
        }

        // Units (IMPL — user-configurable display units).
        UnitsSection()

        // Google Health connection.
        GoogleHealthSection()

        // About.
        AboutSection(
            versionName = viewModel.versionName,
            versionCode = viewModel.versionCode,
        )

        // Sign out footer.
        Button(
            onClick = { viewModel.signOut(onSignedOut) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out")
        }
    }
}
