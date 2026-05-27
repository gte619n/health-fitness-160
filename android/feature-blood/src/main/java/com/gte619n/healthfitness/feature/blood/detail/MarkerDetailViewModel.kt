package com.gte619n.healthfitness.feature.blood.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.BloodReadingRepository
import com.gte619n.healthfitness.domain.blood.BloodTestReportRepository
import com.gte619n.healthfitness.domain.blood.LatestMarker
import com.gte619n.healthfitness.domain.blood.LatestMarkers
import com.gte619n.healthfitness.feature.blood.nav.MarkerDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the marker-detail screen. Reads the same combined flow as the
 * overview and picks the requested marker — keeps the math in
 * `LatestMarkers.derive` so detail and overview never disagree.
 */
@HiltViewModel
class MarkerDetailViewModel @Inject constructor(
    private val readings: BloodReadingRepository,
    private val reports: BloodTestReportRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    val marker: BloodMarker = run {
        val route = savedState.toRoute<MarkerDetailRoute>()
        BloodMarker.valueOf(route.markerKey)
    }

    private val refreshError = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> = combine(
        readings.observeReadings(),
        reports.observeReports(),
        refreshError,
    ) { r, rep, err ->
        if (err != null && r.isEmpty() && rep.isEmpty()) {
            UiState.Error(err)
        } else {
            val derived: LatestMarker? = LatestMarkers.derive(r, rep)
                .firstOrNull { it.marker == marker }
            if (derived == null) UiState.Error("Marker not found")
            else UiState.Ready(latest = derived)
        }
    }.catch { e ->
        emit(UiState.Error(e.localizedMessage ?: "Failed to load marker"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch {
            runCatching {
                readings.refresh()
                reports.refresh()
            }.onFailure { e -> refreshError.value = e.localizedMessage ?: "Failed to load" }
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val latest: LatestMarker) : UiState
        data class Error(val message: String) : UiState
    }
}
