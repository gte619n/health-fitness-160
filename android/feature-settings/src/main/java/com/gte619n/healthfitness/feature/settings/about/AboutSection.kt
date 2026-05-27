package com.gte619n.healthfitness.feature.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.feature.settings.AppVersionInfo
import com.gte619n.healthfitness.feature.settings.SectionTitle
import com.gte619n.healthfitness.feature.settings.SettingsCard
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * About card: app version line + two link rows (Privacy, Terms). URLs
 * are placeholders today; the real ones land alongside the content
 * workstream outside the Android domain (IMPL-AND-02 §"Open questions
 * deferred to implementation").
 */
@Composable
fun AboutSection(
    versionInfo: AppVersionInfo,
    modifier: Modifier = Modifier,
    privacyUrl: String = "https://placeholder.tesseta.app/privacy",
    termsUrl: String = "https://placeholder.tesseta.app/terms",
) {
    val ctx = LocalContext.current
    SettingsCard(modifier = modifier) {
        SectionTitle("About")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "App version",
                style = Hf.type.bodyMd,
                color = Hf.colors.textPrimary,
            )
            Text(
                text = "${versionInfo.versionName} (#${versionInfo.versionCode})",
                style = Hf.type.monoSm,
                color = Hf.colors.textTertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
        AboutLinkRow(
            label = "Privacy policy",
            onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
            },
        )
        AboutLinkRow(
            label = "Terms of service",
            onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl)))
            },
        )
    }
}

@Composable
private fun AboutLinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Hf.type.bodyMd,
            color = Hf.colors.textPrimary,
        )
        Column {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Hf.colors.textTertiary,
            )
        }
    }
}
