package com.gte619n.healthfitness.feature.blood.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.MarkerCatalog
import com.gte619n.healthfitness.feature.blood.components.HfCard
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Modal sheet for posting a new manual reading. Rendered as a dialog
 * route. Picks a marker via a flow-row chip set (the catalog is only
 * 8 entries so no scroll picker is needed).
 */
@Composable
fun AddReadingScreen(
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    vm: AddReadingViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()

    HfCard(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Add blood reading",
                style = Hf.type.headingMd,
                color = Hf.colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Marker",
                style = Hf.type.bodySm.copy(fontSize = 11.sp),
                color = Hf.colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            MarkerChips(selected = form.marker, onSelect = vm::onMarker)

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.value,
                onValueChange = vm::onValue,
                label = { Text("Value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.unit,
                onValueChange = vm::onUnit,
                label = { Text("Unit (optional)") },
                placeholder = { Text("mg/dL, %, mg/L…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.sampleDate.format(ISO),
                onValueChange = { s ->
                    runCatching { LocalDate.parse(s, ISO) }.getOrNull()?.let { vm.onSampleDate(it) }
                },
                label = { Text("Sample date (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.labSource,
                onValueChange = vm::onLabSource,
                label = { Text("Lab source (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.notes,
                onValueChange = vm::onNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = form.error!!,
                    style = Hf.type.bodySm.copy(fontSize = 11.sp),
                    color = Hf.colors.alert,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !form.submitting) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.submit(onDone) },
                    enabled = !form.submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = Hf.colors.accent),
                ) {
                    Text(if (form.submitting) "Saving…" else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkerChips(selected: BloodMarker?, onSelect: (BloodMarker) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkerCatalog.DISPLAY_ORDER.forEach { m ->
            val active = selected == m
            Box(
                modifier = Modifier
                    .background(
                        color = if (active) Hf.colors.accent else Hf.colors.canvasMuted,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(m) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = MarkerCatalog.displayName(m),
                    style = Hf.type.bodySm.copy(fontSize = 11.sp),
                    color = if (active) Hf.colors.textInverse else Hf.colors.textPrimary,
                )
            }
        }
    }
}

private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
