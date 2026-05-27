package com.gte619n.healthfitness.data.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Public scope-upgrade contract. Repositories that depend on the
 * Google Health scope flow inject the interface; the production
 * implementation is [GoogleHealthScopeRepository], and tests can
 * substitute a simple fake without going through GMS at all.
 */
interface GoogleHealthScopeRepositoryApi {
    suspend fun requestHealthAuthorization(): HealthAuthFlow
    fun parseConsentResult(data: Intent?): HealthAuthFlow
}

/**
 * Sibling to [GoogleAuthRepository] for the Google Health scope upgrade.
 *
 * Why not extend [GoogleAuthRepository]? Two different Google API
 * surfaces — Credential Manager (sign-in / ID token) vs GIS
 * AuthorizationClient (OAuth scope grant) — have different threading
 * models (suspend vs `Task<...>`), different lifecycles (per-refresh vs
 * one-shot per upgrade), and different resolution patterns. Keeping
 * them as siblings means each repository remains testable in isolation.
 *
 * The flow is intentionally two-phase so the caller's Activity can own
 * the `ActivityResultLauncher<IntentSenderRequest>` for the consent UI:
 *
 *  - [requestHealthAuthorization] returns [HealthAuthFlow.Resolved] if
 *    the user has previously granted the scope (no UI needed), or
 *    [HealthAuthFlow.NeedsUserConsent] carrying an `IntentSender` the
 *    screen launches.
 *  - [parseConsentResult] is called from the launcher callback with
 *    the result `Intent` and yields the eventual auth code.
 *
 * `requestOfflineAccess(..., forceCodeForRefreshToken = true)` is the
 * GIS equivalent of the web's `prompt=consent` — it forces the consent
 * screen so Google reliably issues a fresh refresh token even if the
 * user previously dismissed a consent for this scope.
 */
class GoogleHealthScopeRepository(
    private val webOauthClientId: String,
    private val client: AuthorizationClientGateway,
) : GoogleHealthScopeRepositoryApi {

    /**
     * Production constructor — Hilt calls this one. The default
     * gateway forwards to GMS via [Identity.getAuthorizationClient].
     */
    constructor(context: Context, webOauthClientId: String) : this(
        webOauthClientId = webOauthClientId,
        client = DefaultAuthorizationClientGateway(context),
    )

    override suspend fun requestHealthAuthorization(): HealthAuthFlow = try {
        client.authorize(
            webOauthClientId = webOauthClientId,
            scope = GoogleHealthScopes.METRICS_READ_ONLY,
        )
    } catch (e: ApiException) {
        HealthAuthFlow.Failed(e.message ?: e.javaClass.simpleName)
    }

    override fun parseConsentResult(data: Intent?): HealthAuthFlow = try {
        client.resultFromIntent(data)
    } catch (e: ApiException) {
        HealthAuthFlow.Failed(e.message ?: e.javaClass.simpleName)
    }
}

/**
 * Two-phase outcome of [GoogleHealthScopeRepository.requestHealthAuthorization].
 * `Resolved` is the happy path (no UI needed); `NeedsUserConsent`
 * surfaces a `PendingIntent.IntentSender` the Activity must launch via
 * `ActivityResultLauncher<IntentSenderRequest>`; `Failed` carries the
 * `ApiException` message verbatim for surface-level diagnostics.
 */
sealed interface HealthAuthFlow {
    data class Resolved(val serverAuthCode: String) : HealthAuthFlow
    data class NeedsUserConsent(val intentSender: IntentSender) : HealthAuthFlow
    data class Failed(val cause: String) : HealthAuthFlow
}

/**
 * Thin seam over [com.google.android.gms.auth.api.identity.AuthorizationClient]
 * — and over the bit of result-classification work that goes with it —
 * so tests can substitute the GMS surface without spinning up Google
 * Play services. Default impl forwards to
 * `Identity.getAuthorizationClient(context)` and translates the
 * resulting [AuthorizationResult] into a [HealthAuthFlow].
 *
 * The classify logic lives here (not in the repository) so the
 * repository's own logic stays trivially testable without needing a
 * real `AuthorizationResult` — that GMS type is final and can't be
 * subclassed in a JVM test.
 */
interface AuthorizationClientGateway {
    /**
     * Build an `AuthorizationRequest` for the given client id + scope
     * and execute it, translating the GMS `AuthorizationResult` into
     * the [HealthAuthFlow] sealed type. Implementations decide whether
     * to use `forceCodeForRefreshToken` (production does).
     */
    suspend fun authorize(webOauthClientId: String, scope: String): HealthAuthFlow
    fun resultFromIntent(intent: Intent?): HealthAuthFlow
}

internal class DefaultAuthorizationClientGateway(
    context: Context,
) : AuthorizationClientGateway {
    private val client = Identity.getAuthorizationClient(context)

    override suspend fun authorize(webOauthClientId: String, scope: String): HealthAuthFlow {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(scope)))
            .requestOfflineAccess(webOauthClientId, /* forceCodeForRefreshToken = */ true)
            .build()
        return classify(client.authorize(request).await())
    }

    override fun resultFromIntent(intent: Intent?): HealthAuthFlow {
        val result = client.getAuthorizationResultFromIntent(intent)
        return result.serverAuthCode?.let { HealthAuthFlow.Resolved(it) }
            ?: HealthAuthFlow.Failed("Consent completed but no server auth code returned")
    }

    private fun classify(result: AuthorizationResult): HealthAuthFlow {
        val code = result.serverAuthCode
        if (code != null) return HealthAuthFlow.Resolved(code)
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            return HealthAuthFlow.NeedsUserConsent(pending.intentSender)
        }
        return HealthAuthFlow.Failed("No server auth code and no resolution")
    }
}
