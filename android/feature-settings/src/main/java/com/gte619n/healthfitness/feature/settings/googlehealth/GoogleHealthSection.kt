package com.gte619n.healthfitness.feature.settings.googlehealth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gte619n.healthfitness.feature.settings.SectionTitle
import com.gte619n.healthfitness.feature.settings.SettingsCard
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Connected / Not-connected card for the Google Health integration.
 *
 * The Activity-scoped `ActivityResultLauncher<IntentSenderRequest>` is
 * owned here rather than further up the tree because the consent
 * intent is short-lived and entirely scoped to this card's flow — no
 * other surface needs to launch it.
 *
 * `LaunchedEffect(viewModel)` is keyed on the viewModel so the
 * `consentRequests` collector re-attaches across configuration changes
 * without leaking the previous Activity's launcher.
 */
@Composable
fun GoogleHealthSection(
    modifier: Modifier = Modifier,
    viewModel: GoogleHealthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(result.data)
    }

    LaunchedEffect(viewModel) {
        viewModel.consentRequests.collect { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    SettingsCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.MonitorHeart,
                contentDescription = null,
                tint = Hf.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            SectionTitle("Google Health")
        }
        Spacer(Modifier.height(10.dp))
        when (val s = state) {
            GoogleHealthViewModel.UiState.Loading -> ConnectionStatusText("Checking status…")
            is GoogleHealthViewModel.UiState.Disconnected -> DisconnectedRow(
                connecting = s.connecting,
                onConnect = viewModel::connect,
            )
            is GoogleHealthViewModel.UiState.Connected -> ConnectedRow(
                connectedAtEpochSeconds = s.connectedAtEpochSeconds,
                disconnecting = s.disconnecting,
                onDisconnect = viewModel::disconnect,
            )
            is GoogleHealthViewModel.UiState.Error -> ErrorRow(
                message = s.message,
                onRetry = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun ConnectionStatusText(text: String) {
    Text(text = text, style = Hf.type.bodyMd, color = Hf.colors.textTertiary)
}

@Composable
private fun DisconnectedRow(
    connecting: Boolean,
    onConnect: () -> Unit,
) {
    Column {
        ConnectionStatusText("Not connected")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Sync body composition, blood pressure, and other metrics from Google Health.",
            style = Hf.type.bodySm,
            color = Hf.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConnect,
            enabled = !connecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Hf.colors.accent,
                contentColor = Hf.colors.textInverse,
            ),
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    color = Hf.colors.textInverse,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(text = "Connecting…", style = Hf.type.bodyMd)
            } else {
                Text(text = "Connect Google Health", style = Hf.type.bodyMd)
            }
        }
    }
}

@Composable
private fun ConnectedRow(
    connectedAtEpochSeconds: Long?,
    disconnecting: Boolean,
    onDisconnect: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Connected", style = Hf.type.bodyMd, color = Hf.colors.good)
            if (connectedAtEpochSeconds != null) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "· ${formatConnectedAt(connectedAtEpochSeconds)}",
                    style = Hf.type.monoSm,
                    color = Hf.colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tesseta is syncing your Google Health data in the background.",
            style = Hf.type.bodySm,
            color = Hf.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDisconnect,
            enabled = !disconnecting,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                SolidColor(Hf.colors.borderStrong),
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Hf.colors.textPrimary),
        ) {
            if (disconnecting) {
                CircularProgressIndicator(
                    color = Hf.colors.textPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(text = "Disconnecting…", style = Hf.type.bodyMd)
            } else {
                Text(text = "Disconnect", style = Hf.type.bodyMd)
            }
        }
    }
}

@Composable
private fun ErrorRow(
    message: String,
    onRetry: () -> Unit,
) {
    Column {
        Text(text = "Couldn't reach Google Health", style = Hf.type.bodyMd, color = Hf.colors.alert)
        Spacer(Modifier.height(4.dp))
        Text(text = message, style = Hf.type.bodySm, color = Hf.colors.textTertiary)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Retry", style = Hf.type.bodyMd) }
        }
    }
}

private val connectedFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

private fun formatConnectedAt(epochSeconds: Long): String =
    connectedFormatter.format(Instant.ofEpochSecond(epochSeconds))
