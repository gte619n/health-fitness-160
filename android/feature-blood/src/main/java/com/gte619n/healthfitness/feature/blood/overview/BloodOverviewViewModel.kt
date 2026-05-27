package com.gte619n.healthfitness.feature.blood.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.domain.blood.BloodReadingRepository
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.domain.blood.BloodTestReportRepository
import com.gte619n.healthfitness.domain.blood.LatestMarker
import com.gte619n.healthfitness.domain.blood.LatestMarkers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Blood overview screen. Combines the manual-reading flow
 * with the lab-report flow, derives the unified `LatestMarker` list,
 * and exposes the most recent 10 reports.
 *
 * UiState is a single sealed flow per the IMPL-AND-00 convention.
 * Errors surface from the initial refresh; once the cache populates,
 * upstream emissions don't re-trigger Error (the user is already
 * looking at stale data and a transient hiccup shouldn't blank it).
 */
@HiltViewModel
class BloodOverviewViewModel @Inject constructor(
    private val readingsRepo: BloodReadingRepository,
    private val reportsRepo: BloodTestReportRepository,
) : ViewModel() {

    private val refreshError = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> = combine(
        readingsRepo.observeReadings(),
        reportsRepo.observeReports(),
        refreshError,
    ) { readings, reports, err ->
        if (err != null && readings.isEmpty() && reports.isEmpty()) {
            UiState.Error(err)
        } else {
            UiState.Ready(
                recentReports = reports
                    .sortedByDescending { it.sampleDate ?: LocalDate.MIN }
                    .take(10),
                trackedMarkers = LatestMarkers.derive(readings, reports),
            )
        }
    }.catch { e ->
        emit(UiState.Error(e.localizedMessage ?: "Failed to load blood data"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init { retry() }

    fun retry() {
        viewModelScope.launch {
            refreshError.value = null
            runCatching {
                readingsRepo.refresh()
                reportsRepo.refresh()
            }.onFailure { e ->
                refreshError.value = e.localizedMessage ?: "Failed to load blood data"
            }
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(
            val recentReports: List<BloodTestReport>,
            val trackedMarkers: List<LatestMarker>,
        ) : UiState
        data class Error(val message: String) : UiState
    }
}
