package com.gte619n.healthfitness.feature.settings.googlehealth

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.data.auth.GoogleHealthScopeRepositoryApi
import com.gte619n.healthfitness.data.auth.HealthAuthFlow
import com.gte619n.healthfitness.domain.googlehealth.GoogleHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the Google Health connection state machine on the device side.
 *
 *  - [UiState.Loading] is the bootstrap state before the first
 *    `/status` reply arrives.
 *  - [UiState.Disconnected] is the resting "Not connected" state;
 *    `connecting = true` while the connect flow is in flight (covers
 *    both the immediate-grant path and the post-consent submit).
 *  - [UiState.Connected] is the resting "Connected" state;
 *    `disconnecting = true` while the DELETE is in flight.
 *  - [UiState.Error] surfaces hard failures with a retry path.
 *
 * [consentRequests] is a `MutableSharedFlow` with `replay = 0` rather
 * than a `StateFlow` because each consent intent must be launched
 * exactly once. A StateFlow would re-emit on recomposition and
 * re-launch the consent UI.
 */
@HiltViewModel
class GoogleHealthViewModel @Inject constructor(
    private val backend: GoogleHealthRepository,
    private val scope: GoogleHealthScopeRepositoryApi,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Disconnected(val connecting: Boolean = false) : UiState
        data class Connected(
            val connectedAtEpochSeconds: Long?,
            val disconnecting: Boolean = false,
        ) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _consentRequests = MutableSharedFlow<IntentSender>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val consentRequests: Flow<IntentSender> = _consentRequests

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            backend.status().fold(
                onSuccess = { status ->
                    _state.value = if (status.connected) {
                        UiState.Connected(connectedAtEpochSeconds = status.connectedAtEpochSeconds)
                    } else {
                        UiState.Disconnected()
                    }
                },
                onFailure = { e ->
                    _state.value = UiState.Error(e.message ?: "Failed to load status")
                },
            )
        }
    }

    fun connect() {
        val current = _state.value
        if (current !is UiState.Disconnected || current.connecting) return
        _state.value = UiState.Disconnected(connecting = true)
        viewModelScope.launch {
            when (val flow = scope.requestHealthAuthorization()) {
                is HealthAuthFlow.Resolved -> submitAuthCode(flow.serverAuthCode)
                is HealthAuthFlow.NeedsUserConsent -> _consentRequests.tryEmit(flow.intentSender)
                is HealthAuthFlow.Failed -> {
                    _state.value = UiState.Error(flow.cause)
                }
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (val parsed = scope.parseConsentResult(data)) {
                is HealthAuthFlow.Resolved -> submitAuthCode(parsed.serverAuthCode)
                is HealthAuthFlow.NeedsUserConsent -> {
                    // Edge case: GIS asks for resolution again. Re-emit
                    // so the Activity launches another consent screen
                    // rather than silently dropping the result.
                    _consentRequests.tryEmit(parsed.intentSender)
                }
                is HealthAuthFlow.Failed -> {
                    _state.value = UiState.Error(parsed.cause)
                }
            }
        }
    }

    fun disconnect() {
        val current = _state.value
        if (current !is UiState.Connected || current.disconnecting) return
        _state.value = current.copy(disconnecting = true)
        viewModelScope.launch {
            backend.disconnect().fold(
                onSuccess = { _state.value = UiState.Disconnected() },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Failed to disconnect") },
            )
        }
    }

    private suspend fun submitAuthCode(code: String) {
        backend.connectWithServerAuthCode(code).fold(
            onSuccess = {
                backend.status().fold(
                    onSuccess = { status ->
                        _state.value = if (status.connected) {
                            UiState.Connected(connectedAtEpochSeconds = status.connectedAtEpochSeconds)
                        } else {
                            // Backend says not-connected immediately
                            // after a successful POST — treat as a
                            // transient error so the user can retry.
                            UiState.Error("Backend did not register the connection")
                        }
                    },
                    onFailure = { e ->
                        _state.value = UiState.Error(e.message ?: "Failed to refresh status")
                    },
                )
            },
            onFailure = { e -> _state.value = UiState.Error(e.message ?: "Failed to connect") },
        )
    }
}
