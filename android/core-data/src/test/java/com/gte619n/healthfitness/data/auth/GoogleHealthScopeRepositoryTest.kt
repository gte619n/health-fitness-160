package com.gte619n.healthfitness.data.auth

import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives [GoogleHealthScopeRepository] through its gateway seam. The
 * real `AuthorizationResult` (a final GMS type) is never constructed
 * in tests — the gateway's contract is `HealthAuthFlow` shaped so we
 * can substitute a plain stub without GMS or Robolectric.
 */
class GoogleHealthScopeRepositoryTest {

    @Test
    fun `resolved auth code surfaces HealthAuthFlow_Resolved`() = runBlocking {
        val repo = GoogleHealthScopeRepository(
            webOauthClientId = "client-1",
            client = StubGateway(
                authorize = HealthAuthFlow.Resolved("code-immediate"),
                fromIntent = HealthAuthFlow.Resolved("code-immediate"),
            ),
        )
        val outcome = repo.requestHealthAuthorization()
        assertTrue(outcome is HealthAuthFlow.Resolved)
        assertEquals("code-immediate", (outcome as HealthAuthFlow.Resolved).serverAuthCode)
    }

    @Test
    fun `ApiException during authorize surfaces Failed`() = runBlocking {
        val repo = GoogleHealthScopeRepository(
            webOauthClientId = "client-1",
            client = ThrowingGateway(ApiException(Status.RESULT_CANCELED)),
        )
        val outcome = repo.requestHealthAuthorization()
        assertTrue(outcome is HealthAuthFlow.Failed)
    }

    @Test
    fun `parseConsentResult propagates gateway outcome`() {
        val repo = GoogleHealthScopeRepository(
            webOauthClientId = "client-1",
            client = StubGateway(
                authorize = HealthAuthFlow.Failed("unused"),
                fromIntent = HealthAuthFlow.Resolved("code-consent"),
            ),
        )
        val outcome = repo.parseConsentResult(null)
        assertEquals("code-consent", (outcome as HealthAuthFlow.Resolved).serverAuthCode)
    }

    @Test
    fun `parseConsentResult catches ApiException`() {
        val repo = GoogleHealthScopeRepository(
            webOauthClientId = "client-1",
            client = ThrowingGateway(ApiException(Status.RESULT_CANCELED)),
        )
        val outcome = repo.parseConsentResult(null)
        assertTrue(outcome is HealthAuthFlow.Failed)
    }

    private class StubGateway(
        private val authorize: HealthAuthFlow,
        private val fromIntent: HealthAuthFlow,
    ) : AuthorizationClientGateway {
        override suspend fun authorize(
            webOauthClientId: String,
            scope: String,
        ): HealthAuthFlow = authorize
        override fun resultFromIntent(intent: Intent?): HealthAuthFlow = fromIntent
    }

    private class ThrowingGateway(
        private val throwable: Throwable,
    ) : AuthorizationClientGateway {
        override suspend fun authorize(
            webOauthClientId: String,
            scope: String,
        ): HealthAuthFlow = throw throwable
        override fun resultFromIntent(intent: Intent?): HealthAuthFlow =
            throw throwable
    }

    // Note: the NeedsUserConsent path is covered end-to-end via
    // [GoogleHealthViewModelTest], where the wrapper is constructed by
    // a stub repository that never goes through GMS. Constructing an
    // android.content.IntentSender directly in a JVM test is brittle
    // — IntentSender is a final class whose only useful constructor
    // is package-private — so we leave that branch to the VM test.
}
