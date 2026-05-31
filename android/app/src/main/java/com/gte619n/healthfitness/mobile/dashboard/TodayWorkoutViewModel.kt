package com.gte619n.healthfitness.mobile.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.data.workout.WorkoutRepository
import com.gte619n.healthfitness.domain.workout.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayWorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object RestDay : UiState
        data class Ready(val session: WorkoutSession) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val today = repository.today()
                if (today == null) UiState.RestDay else UiState.Ready(today)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Couldn't load today's workout")
            }
        }
    }
}
