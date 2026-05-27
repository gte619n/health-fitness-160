package com.gte619n.healthfitness.feature.blood.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.BloodReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Add-reading sheet. The form is a single state class so the UI can
 * re-render after each edit without juggling individual `mutableStateOf`
 * fields. Submit kicks off [BloodReadingRepository.create]; on success
 * we invoke the caller's `onSuccess` callback (the sheet dismisses).
 */
@HiltViewModel
class AddReadingViewModel @Inject constructor(
    private val readings: BloodReadingRepository,
) : ViewModel() {

    data class FormState(
        val marker: BloodMarker? = null,
        val value: String = "",
        val unit: String = "",
        val sampleDate: LocalDate = LocalDate.now(),
        val labSource: String = "",
        val notes: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun onMarker(m: BloodMarker) = _form.update { it.copy(marker = m, error = null) }
    fun onValue(s: String) = _form.update { it.copy(value = s, error = null) }
    fun onUnit(s: String) = _form.update { it.copy(unit = s, error = null) }
    fun onSampleDate(d: LocalDate) = _form.update { it.copy(sampleDate = d, error = null) }
    fun onLabSource(s: String) = _form.update { it.copy(labSource = s, error = null) }
    fun onNotes(s: String) = _form.update { it.copy(notes = s, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val snap = _form.value
        val marker = snap.marker
        val parsed = snap.value.toDoubleOrNull()
        when {
            marker == null -> {
                _form.update { it.copy(error = "Pick a marker") }
                return
            }
            parsed == null -> {
                _form.update { it.copy(error = "Enter a numeric value") }
                return
            }
        }
        _form.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                readings.create(
                    marker = marker!!,
                    value = parsed!!,
                    unit = snap.unit.ifBlank { null },
                    sampleDate = snap.sampleDate,
                    labSource = snap.labSource.ifBlank { null },
                    notes = snap.notes.ifBlank { null },
                )
            }.onSuccess {
                _form.update { it.copy(submitting = false) }
                onSuccess()
            }.onFailure { e ->
                _form.update { it.copy(submitting = false, error = e.localizedMessage ?: "Save failed") }
            }
        }
    }
}
