package com.gte619n.healthfitness.feature.blood.report

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.feature.blood.components.ExtractedMarkerRow
import com.gte619n.healthfitness.feature.blood.components.HfCard
import com.gte619n.healthfitness.ui.snackbar.LocalSnackbarController
import com.gte619n.healthfitness.ui.state.ErrorState
import com.gte619n.healthfitness.ui.state.LoadingState
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    vm: ReportDetailViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val pdf by vm.pdfState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Lab report", style = Hf.type.headingMd) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Hf.colors.canvas),
            )
        },
        containerColor = Hf.colors.canvas,
    ) { padding ->
        when (val s = ui) {
            is ReportDetailViewModel.UiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding),
                label = "Loading report",
            )
            is ReportDetailViewModel.UiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = s.message,
            )
            is ReportDetailViewModel.UiState.Ready -> Content(
                report = s.report,
                pdfStatus = pdf,
                onOpenPdf = {
                    vm.openPdf { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            snackbar.show("No PDF viewer installed on this device")
                        }
                    }
                },
                onDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete report?") },
            text = { Text("This removes the report and its extracted markers. The PDF copy on the server is also deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        vm.delete(onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Hf.colors.alert),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Content(
    report: BloodTestReport,
    pdfStatus: ReportDetailViewModel.PdfStatus,
    onOpenPdf: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Hf.colors.canvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HfCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = report.labSource.ifBlank { "Lab report" },
                        style = Hf.type.headingMd,
                        color = Hf.colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Sample date: ${formatDate(report.sampleDate)}",
                        style = Hf.type.bodySm.copy(fontSize = 11.sp),
                        color = Hf.colors.textTertiary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onOpenPdf,
                            enabled = pdfStatus !is ReportDetailViewModel.PdfStatus.Downloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Hf.colors.accent),
                        ) {
                            Text(
                                if (pdfStatus is ReportDetailViewModel.PdfStatus.Downloading) "Loading…"
                                else "View PDF",
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onDelete) {
                            Text("Delete report")
                        }
                    }
                    if (pdfStatus is ReportDetailViewModel.PdfStatus.Error) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = pdfStatus.message,
                            style = Hf.type.bodySm.copy(fontSize = 11.sp),
                            color = Hf.colors.alert,
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Extracted markers (${report.markers.size})",
                style = Hf.type.headingSm.copy(fontSize = 13.sp),
                color = Hf.colors.textPrimary,
            )
        }
        if (report.markers.isEmpty()) {
            item {
                HfCard {
                    Text(
                        text = "No markers extracted from this report.",
                        modifier = Modifier.padding(14.dp),
                        style = Hf.type.bodySm.copy(fontSize = 12.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
            }
        } else {
            items(report.markers) { marker ->
                HfCard(modifier = Modifier.fillMaxWidth()) {
                    ExtractedMarkerRow(marker = marker)
                }
            }
        }
    }
}

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
private fun formatDate(d: LocalDate?): String = if (d == null) "—" else d.format(SHORT).uppercase(Locale.US)
