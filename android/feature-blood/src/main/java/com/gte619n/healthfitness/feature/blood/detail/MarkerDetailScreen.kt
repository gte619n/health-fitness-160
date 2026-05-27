package com.gte619n.healthfitness.feature.blood.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.domain.blood.LatestMarker
import com.gte619n.healthfitness.domain.blood.MarkerCatalog
import com.gte619n.healthfitness.domain.blood.MarkerHistoryPoint
import com.gte619n.healthfitness.feature.blood.components.HfCard
import com.gte619n.healthfitness.feature.blood.components.MarkerHistoryChart
import com.gte619n.healthfitness.feature.blood.components.MarkerReferenceBar
import com.gte619n.healthfitness.ui.state.ErrorState
import com.gte619n.healthfitness.ui.state.LoadingState
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerDetailScreen(
    onBack: () -> Unit,
    vm: MarkerDetailViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = MarkerCatalog.displayName(vm.marker),
                        style = Hf.type.headingMd,
                    )
                },
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
            is MarkerDetailViewModel.UiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding),
                label = "Loading marker",
            )
            is MarkerDetailViewModel.UiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = s.message,
            )
            is MarkerDetailViewModel.UiState.Ready -> Content(
                latest = s.latest,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun Content(latest: LatestMarker, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Hf.colors.canvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(latest) }
        item { ChartCard(latest) }
        item { TargetCard(latest) }
        if (latest.history.isNotEmpty()) {
            item {
                Text(
                    text = "Readings",
                    style = Hf.type.headingSm.copy(fontSize = 13.sp),
                    color = Hf.colors.textPrimary,
                )
            }
            items(latest.history.reversed()) { point -> ReadingRow(point, latest.unit) }
        }
    }
}

@Composable
private fun SummaryCard(latest: LatestMarker) {
    HfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatValue(latest.value),
                    style = Hf.type.displayMd.copy(fontSize = 32.sp),
                    color = Hf.colors.textPrimary,
                )
                if (latest.value != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "  ${latest.unit}",
                        style = Hf.type.bodySm.copy(fontSize = 12.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Last reading: ${formatDate(latest.sampleDate)} • ${
                    when (latest.source) {
                        LatestMarker.Source.MANUAL -> "Manual"
                        LatestMarker.Source.LAB -> "Lab"
                        LatestMarker.Source.NONE -> "No data"
                    }
                }",
                style = Hf.type.bodySm.copy(fontSize = 11.sp),
                color = Hf.colors.textTertiary,
            )
            Spacer(Modifier.height(10.dp))
            MarkerReferenceBar(value = latest.value, reference = latest.reference)
        }
    }
}

@Composable
private fun ChartCard(latest: LatestMarker) {
    HfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Last 12 months",
                style = Hf.type.headingSm.copy(fontSize = 12.sp),
                color = Hf.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            if (latest.history.isEmpty()) {
                Text(
                    text = "No history yet",
                    style = Hf.type.bodySm.copy(fontSize = 12.sp),
                    color = Hf.colors.textTertiary,
                )
            } else {
                MarkerHistoryChart(
                    history = latest.history,
                    reference = latest.reference,
                )
            }
        }
    }
}

@Composable
private fun TargetCard(latest: LatestMarker) {
    HfCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "About this marker",
                style = Hf.type.headingSm.copy(fontSize = 12.sp),
                color = Hf.colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = MarkerCatalog.description(latest.marker),
                style = Hf.type.bodySm.copy(fontSize = 12.sp),
                color = Hf.colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Target: ${MarkerCatalog.target(latest.marker)}",
                style = Hf.type.bodySm.copy(fontSize = 11.sp),
                color = Hf.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ReadingRow(point: MarkerHistoryPoint, unit: String) {
    HfCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = formatDate(point.date),
                    style = Hf.type.bodyMd.copy(fontSize = 12.sp),
                    color = Hf.colors.textPrimary,
                )
                Text(
                    text = when (val s = point.source) {
                        is MarkerHistoryPoint.Source.Manual -> "Manual"
                        is MarkerHistoryPoint.Source.Lab -> "Lab • ${s.labSource}"
                    },
                    style = Hf.type.bodySm.copy(fontSize = 10.sp),
                    color = Hf.colors.textTertiary,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatValue(point.value),
                    style = Hf.type.monoMd.copy(fontSize = 14.sp),
                    color = Hf.colors.textPrimary,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = " ${point.unit.ifBlank { unit }}",
                        style = Hf.type.bodySm.copy(fontSize = 10.sp),
                        color = Hf.colors.textTertiary,
                    )
                }
            }
        }
    }
}

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
private fun formatDate(d: LocalDate?): String = if (d == null) "—" else d.format(SHORT).uppercase(Locale.US)
private fun formatValue(v: Double?): String = when {
    v == null -> "—"
    v < 10.0 -> String.format(Locale.US, "%.1f", v)
    else -> v.toInt().toString()
}
