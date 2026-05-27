package com.gte619n.healthfitness.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.domain.profile.HeightMetric
import com.gte619n.healthfitness.domain.profile.Profile
import com.gte619n.healthfitness.domain.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Three-state ViewModel for the Profile screen:
 *
 *  - [UiState.Loading] while the initial fetch is in flight.
 *  - [UiState.Loaded] when the profile is available; `saving = true`
 *    during an in-flight PATCH so the UI can disable the button.
 *  - [UiState.Error] when the GET fails (with a retry path).
 *
 * Save errors stay on the loaded state with `saving = false` rather
 * than flipping to [UiState.Error] — losing the displayed profile
 * because of a transient network failure would be worse than
 * surfacing the error inline.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(
            val profile: Profile,
            val saving: Boolean = false,
            val saveError: String? = null,
        ) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.get().fold(
                onSuccess = { _state.value = UiState.Loaded(it) },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Failed to load profile") },
            )
        }
    }

    fun saveHeight(feet: Int, inches: Int) {
        val current = _state.value as? UiState.Loaded ?: return
        _state.value = current.copy(saving = true, saveError = null)
        val cm = HeightMetric.ftInToCm(feet, inches)
        viewModelScope.launch {
            repository.updateHeightCm(cm).fold(
                onSuccess = { profile -> _state.value = UiState.Loaded(profile, saving = false) },
                onFailure = { e ->
                    _state.update {
                        (it as? UiState.Loaded)?.copy(
                            saving = false,
                            saveError = e.message ?: "Failed to save height",
                        ) ?: it
                    }
                },
            )
        }
    }
}
