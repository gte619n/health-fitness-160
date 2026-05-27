package com.gte619n.healthfitness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.data.auth.GoogleAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Backing logic for the Settings root screen — owns the sign-out action
 * and surfaces app-version metadata for the About card.
 *
 * Sign-out is intentionally scoped to local state: clear the cached ID
 * token + Credential Manager grant, then notify the caller so the
 * navigation layer can return to the sign-in screen. No backend call —
 * the session is JWT-only and has no server-side record to invalidate.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: GoogleAuthRepository,
    val versionInfo: AppVersionInfo,
) : ViewModel() {

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onDone()
        }
    }
}
