package com.gte619n.healthfitness.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Persists the most recent Google ID token + the "user has signed in once"
// flag so we can decide whether the next Credential Manager call should be
// silent (filterByAuthorizedAccounts=true) or interactive.
//
// Token storage in plain DataStore is acceptable here because the token is
// scoped to a single audience and short-lived (~1h). If we ever need to
// store refresh-style credentials on-device we'll move to EncryptedDataStore.

private val Context.authStore by preferencesDataStore("hf-auth")

class IdTokenCache(private val context: Context) {

    private val keyIdToken = stringPreferencesKey("id_token")
    private val keyExpiresAt = longPreferencesKey("expires_at")
    private val keyHasSignedIn = booleanPreferencesKey("has_signed_in")

    suspend fun read(): Snapshot {
        val prefs = context.authStore.data.first()
        return Snapshot(
            idToken = prefs[keyIdToken],
            expiresAtEpochSeconds = prefs[keyExpiresAt] ?: 0L,
            hasSignedIn = prefs[keyHasSignedIn] ?: false,
        )
    }

    suspend fun write(idToken: String, expiresAtEpochSeconds: Long) {
        context.authStore.edit { prefs ->
            prefs[keyIdToken] = idToken
            prefs[keyExpiresAt] = expiresAtEpochSeconds
            prefs[keyHasSignedIn] = true
        }
    }

    suspend fun clear() {
        context.authStore.edit { prefs -> prefs.clear() }
    }

    fun hasSignedInFlow() = context.authStore.data.map { it[keyHasSignedIn] ?: false }

    data class Snapshot(
        val idToken: String?,
        val expiresAtEpochSeconds: Long,
        val hasSignedIn: Boolean,
    ) {
        fun isFresh(skewSeconds: Long = 60): Boolean =
            idToken != null && System.currentTimeMillis() / 1000 < expiresAtEpochSeconds - skewSeconds
    }
}
