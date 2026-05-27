package com.gte619n.healthfitness.feature.blood.upload

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.feature.blood.components.HfCard
import com.gte619n.healthfitness.feature.blood.components.UploadPhase
import com.gte619n.healthfitness.feature.blood.components.UploadPhaseStepper
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

/**
 * File-picker + SSE-phase sheet. On open we immediately launch the PDF
 * picker; on dismiss/back we close. The picker callback reads the
 * stream into a byte array on the IO dispatcher (small PDFs only — the
 * backend caps at 25 MB and the OS buffers the read).
 */
@Composable
fun UploadLabReportScreen(
    onComplete: (reportId: String) -> Unit,
    onDismiss: () -> Unit,
    vm: UploadLabReportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by vm.state.collectAsStateWithLifecycle()
    val pickedAtLeastOnce = remember { androidx.compose.runtime.mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        pickedAtLeastOnce.value = true
        if (uri == null) {
            // User cancelled the picker — close the whole sheet so we
            // don't sit on an empty screen.
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        val name = uri.queryDisplayName(context) ?: "report.pdf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        vm.upload(name, bytes)
    }

    LaunchedEffect(Unit) {
        if (!pickedAtLeastOnce.value) picker.launch("application/pdf")
    }

    LaunchedEffect(ui) {
        if (ui is UploadLabReportViewModel.UiState.Complete) {
            val report = (ui as UploadLabReportViewModel.UiState.Complete).report
            onComplete(report.reportId)
        }
    }

    HfCard(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Upload lab PDF",
                style = Hf.type.headingMd,
                color = Hf.colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))

            val phase = when (ui) {
                is UploadLabReportViewModel.UiState.Idle,
                is UploadLabReportViewModel.UiState.Uploading -> UploadPhase.Uploading
                is UploadLabReportViewModel.UiState.Extracting -> UploadPhase.Extracting
                is UploadLabReportViewModel.UiState.Saving -> UploadPhase.Saving
                is UploadLabReportViewModel.UiState.Complete -> UploadPhase.Done
                is UploadLabReportViewModel.UiState.Failed -> UploadPhase.Uploading
            }
            UploadPhaseStepper(active = phase, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(14.dp))
            val message = when (val s = ui) {
                is UploadLabReportViewModel.UiState.Idle -> "Choose a PDF to upload"
                is UploadLabReportViewModel.UiState.Uploading -> "Uploading…"
                is UploadLabReportViewModel.UiState.Extracting -> "Extracting markers with Gemini…"
                is UploadLabReportViewModel.UiState.Saving -> "Saving the report…"
                is UploadLabReportViewModel.UiState.Complete -> "Done — opening report"
                is UploadLabReportViewModel.UiState.Failed -> "Upload failed: ${s.error}"
            }
            Text(
                text = message,
                style = Hf.type.bodySm.copy(fontSize = 12.sp),
                color = if (ui is UploadLabReportViewModel.UiState.Failed) Hf.colors.alert
                else Hf.colors.textTertiary,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    vm.cancel()
                    onDismiss()
                }) {
                    Text("Cancel")
                }
                if (ui is UploadLabReportViewModel.UiState.Failed) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            pickedAtLeastOnce.value = false
                            picker.launch("application/pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Hf.colors.accent),
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

/** Reads the display name column from `ContentResolver`. */
private fun Uri.queryDisplayName(context: android.content.Context): String? {
    val cursor = context.contentResolver.query(this, null, null, null, null) ?: return null
    cursor.use { c ->
        if (!c.moveToFirst()) return null
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx < 0) return null
        return c.getString(idx)
    }
}
