package com.gte619n.healthfitness.mobile.auth

import com.gte619n.healthfitness.data.auth.AuthState
import com.gte619n.healthfitness.data.auth.GoogleAuthRepository
import com.gte619n.healthfitness.data.auth.IdTokenCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Owns the AuthState for the activity. Bootstraps silently on launch when
// the cache says the user has signed in before; otherwise stays SignedOut
// until interactiveSignIn() is invoked from the SignInScreen.
//
// This is the moral equivalent of an AuthViewModel, but lifted out of the
// ViewModelStore so we don't need to wire Hilt yet (per android/CLAUDE.md).
// When Hilt lands in a later IMPL the constructor signature stays the same.
class AuthCoordinator(
    private val repo: GoogleAuthRepository,
    private val cache: IdTokenCache,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    suspend fun bootstrap() {
        val snapshot = cache.read()
        if (!snapshot.hasSignedIn) {
            _state.value = AuthState.SignedOut
            return
        }
        // Cached token still fresh — go straight to SignedIn, refresh on next 401.
        val cachedToken = snapshot.idToken
        if (snapshot.isFresh() && cachedToken != null) {
            _state.value = AuthState.SignedIn(
                userId = "(cached)",
                email = null,
                displayName = null,
                idToken = cachedToken,
            )
            // Best-effort silent refresh so subsequent calls have a fresh token.
            _state.value = repo.silentRefresh().fallbackTo(_state.value)
        } else {
            _state.value = repo.silentRefresh()
        }
    }

    suspend fun interactiveSignIn() {
        _state.value = AuthState.Loading
        _state.value = repo.interactiveSignIn()
    }

    suspend fun signOut() {
        repo.signOut()
        _state.value = AuthState.SignedOut
    }

    private fun AuthState.fallbackTo(other: AuthState): AuthState =
        if (this is AuthState.Failed || this is AuthState.SignedOut) other else this
}
