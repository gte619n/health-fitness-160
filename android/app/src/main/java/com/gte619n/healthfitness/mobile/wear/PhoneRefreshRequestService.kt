package com.gte619n.healthfitness.mobile.wear

import com.gte619n.healthfitness.data.auth.AuthState
import com.gte619n.healthfitness.data.auth.GoogleAuthRepository
import com.gte619n.healthfitness.data.auth.IdTokenCache
import com.gte619n.healthfitness.mobile.BuildConfig
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// The wear app fires a /auth/refresh-request when its cached token returns
// 401 from the backend. We respond by running a silent Credential Manager
// refresh — GoogleAuthRepository's onTokenIssued hook republishes the new
// token to all paired nodes, completing the round-trip.
class PhoneRefreshRequestService : WearableListenerService() {

    companion object {
        const val PATH_REFRESH_REQUEST = "/auth/refresh-request"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_REFRESH_REQUEST) return
        val cache = IdTokenCache(applicationContext)
        val publisher = PhoneTokenPublisher(applicationContext)
        val repo = GoogleAuthRepository(
            context = applicationContext,
            cache = cache,
            webOauthClientId = BuildConfig.WEB_OAUTH_CLIENT_ID,
            onTokenIssued = { token, _ -> publisher.publish(token) },
        )
        scope.launch {
            val result = repo.silentRefresh()
            if (result is AuthState.Failed || result is AuthState.SignedOut) {
                // Wear stays unsigned. Phone UI will prompt on next foreground.
            }
        }
    }
}
