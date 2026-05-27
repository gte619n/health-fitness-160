package com.gte619n.healthfitness.data.auth

import com.gte619n.healthfitness.network.AuthTokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the existing [IdTokenCache] + [GoogleAuthRepository] pair to the
 * network module's [AuthTokenProvider] contract. Living in core-data keeps
 * the dependency direction sane — core-network knows nothing about Room,
 * DataStore, or Credential Manager.
 */
@Singleton
class IdTokenCacheAuthTokenProvider @Inject constructor(
    private val cache: IdTokenCache,
    private val repo: GoogleAuthRepository,
) : AuthTokenProvider {

    override suspend fun currentToken(): String? = cache.read().idToken

    override suspend fun refresh(): String? {
        val state = repo.silentRefresh()
        return (state as? AuthState.SignedIn)?.idToken
    }
}
