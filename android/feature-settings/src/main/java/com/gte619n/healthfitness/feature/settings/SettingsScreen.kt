package com.gte619n.healthfitness.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gte619n.healthfitness.feature.settings.about.AboutSection
import com.gte619n.healthfitness.feature.settings.googlehealth.GoogleHealthSection
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * Settings root screen — the entry point from the phone's "More" tab
 * and the foldable sidebar's settings icon. Three cards (Profile entry,
 * Google Health, About) plus a Sign out button at the bottom.
 *
 * `onNavigateBack` is wired by the NavGraph to `popBackStack()`;
 * `onSignedOut` is wired to the auth coordinator's mark-signed-out +
 * pop-to-root behavior so the user lands on the sign-in screen the
 * moment the sign-out coroutine resolves.
 */
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
            .background(Hf.colors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Header(onNavigateBack = onNavigateBack)
        Spacer(Modifier.height(14.dp))

        ProfileEntryCard(onClick = onNavigateToProfile)
        Spacer(Modifier.height(12.dp))

        GoogleHealthSection(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        AboutSection(versionInfo = viewModel.versionInfo, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        SignOutButton(onClick = { viewModel.signOut(onSignedOut) })
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Header(onNavigateBack: () -> Unit) {
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
            text = "Settings",
            style = Hf.type.headingLg,
            color = Hf.colors.textPrimary,
        )
    }
}

@Composable
private fun ProfileEntryCard(onClick: () -> Unit) {
    SettingsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Hf.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Profile", style = Hf.type.bodyMd, color = Hf.colors.textPrimary)
                Text(
                    text = "Name, email, height",
                    style = Hf.type.bodySm,
                    color = Hf.colors.textTertiary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Hf.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            SolidColor(Hf.colors.borderStrong),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Hf.colors.alert),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = null,
            tint = Hf.colors.alert,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(text = "Sign out", style = Hf.type.bodyMd)
    }
}
