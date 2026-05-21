package com.gte619n.healthfitness.wear.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearAuthStore by preferencesDataStore("hf-wear-auth")

// Wear-side mirror of the phone's IdTokenCache. The phone is the source of
// truth; we only store the latest token relayed over the Data Layer.
class WearIdTokenCache(private val context: Context) {
    private val keyIdToken = stringPreferencesKey("id_token")

    val idTokenFlow: Flow<String?> = context.wearAuthStore.data.map { it[keyIdToken] }

    suspend fun write(idToken: String) {
        context.wearAuthStore.edit { it[keyIdToken] = idToken }
    }

    suspend fun clear() {
        context.wearAuthStore.edit { it.clear() }
    }
}
