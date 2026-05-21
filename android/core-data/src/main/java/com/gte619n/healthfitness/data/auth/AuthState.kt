package com.gte619n.healthfitness.data.auth

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val idToken: String,
    ) : AuthState
    data class Failed(val cause: String) : AuthState
}
