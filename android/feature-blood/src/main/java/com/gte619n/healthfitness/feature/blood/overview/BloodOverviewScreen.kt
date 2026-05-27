package com.gte619n.healthfitness.feature.blood.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.feature.blood.components.HfCard
import com.gte619n.healthfitness.feature.blood.components.MarkerCard
import com.gte619n.healthfitness.ui.state.ErrorState
import com.gte619n.healthfitness.ui.state.LoadingState
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BloodOverviewScreen(
    onMarkerClick: (markerKey: String) -> Unit,
    onReportClick: (reportId: String) -> Unit,
    onAddReading: () -> Unit,
    onUploadPdf: () -> Unit,
    vm: BloodOverviewViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    when (val s = ui) {
        is BloodOverviewViewModel.UiState.Loading -> LoadingState(label = "Loading blood data")
        is BloodOverviewViewModel.UiState.Error -> ErrorState(
            message = s.message,
            onRetry = vm::retry,
        )
        is BloodOverviewViewModel.UiState.Ready -> BloodOverviewContent(
            state = s,
            onMarkerClick = { onMarkerClick(it.name) },
            onReportClick = onReportClick,
            onAddReading = onAddReading,
            onUploadPdf = onUploadPdf,
        )
    }
}

@Composable
private fun BloodOverviewContent(
    state: BloodOverviewViewModel.UiState.Ready,
    onMarkerClick: (BloodMarker) -> Unit,
    onReportClick: (String) -> Unit,
    onAddReading: () -> Unit,
    onUploadPdf: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .background(Hf.colors.canvas)
            .fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ActionRow(onAddReading = onAddReading, onUploadPdf = onUploadPdf) }

        item {
            Text(
                text = "Tracked markers",
                style = Hf.type.headingSm.copy(fontSize = 13.sp),
                color = Hf.colors.textPrimary,
            )
        }
        items(state.trackedMarkers, key = { it.marker.name }) { latest ->
            MarkerCard(
                latest = latest,
                onClick = { onMarkerClick(latest.marker) },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Recent reports",
                style = Hf.type.headingSm.copy(fontSize = 13.sp),
                color = Hf.colors.textPrimary,
            )
        }
        if (state.recentReports.isEmpty()) {
            item {
                HfCard {
                    Text(
                        text = "No lab reports uploaded yet.",
                        modifier = Modifier.padding(14.dp),
                        style = Hf.type.bodySm.copy(fontSize = 12.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
            }
        } else {
            items(state.recentReports, key = { it.reportId }) { report ->
                ReportRow(report = report, onClick = { onReportClick(report.reportId) })
            }
        }
    }
}

@Composable
private fun ActionRow(onAddReading: () -> Unit, onUploadPdf: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionButton(
            label = "Add reading",
            icon = Icons.Outlined.Add,
            onClick = onAddReading,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = "Upload lab PDF",
            icon = Icons.Outlined.Description,
            onClick = onUploadPdf,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HfCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Hf.colors.accent,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = label,
                style = Hf.type.bodyMd.copy(fontSize = 13.sp),
                color = Hf.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ReportRow(report: BloodTestReport, onClick: () -> Unit) {
    HfCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = report.labSource.ifBlank { "Lab report" },
                    style = Hf.type.bodyMd.copy(fontSize = 13.sp),
                    color = Hf.colors.textPrimary,
                )
                Text(
                    text = formatDate(report.sampleDate),
                    style = Hf.type.monoSm.copy(fontSize = 10.sp),
                    color = Hf.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${report.markers.size} marker${if (report.markers.size == 1) "" else "s"} extracted",
                style = Hf.type.bodySm.copy(fontSize = 11.sp),
                color = Hf.colors.textTertiary,
            )
        }
    }
}

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
private fun formatDate(d: LocalDate?): String = if (d == null) "—" else d.format(SHORT).uppercase(Locale.US)
