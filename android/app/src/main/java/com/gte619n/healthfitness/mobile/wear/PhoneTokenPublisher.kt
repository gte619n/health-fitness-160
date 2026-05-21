package com.gte619n.healthfitness.mobile.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

// Pushes a newly-issued Google ID token to every paired wear node.
// Called from GoogleAuthRepository's onTokenIssued hook so the wear side
// stays in lock-step with the phone — no independent sign-in on Wear.
class PhoneTokenPublisher(private val context: Context) {

    companion object {
        const val PATH_ID_TOKEN = "/auth/id-token"
    }

    suspend fun publish(idToken: String) {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val messageClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            // Errors per-node are non-fatal; nodes come and go.
            runCatching {
                messageClient.sendMessage(node.id, PATH_ID_TOKEN, idToken.toByteArray(Charsets.UTF_8)).await()
            }
        }
    }
}
