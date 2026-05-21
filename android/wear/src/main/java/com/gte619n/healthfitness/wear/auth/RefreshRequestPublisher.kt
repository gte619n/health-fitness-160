package com.gte619n.healthfitness.wear.auth

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

// Sent from wear when a 401 / missing-token condition is detected. The phone
// listens on this path, runs a silent Credential Manager refresh, and
// republishes the fresh token back over /auth/id-token.
class RefreshRequestPublisher(private val context: Context) {

    companion object {
        const val PATH_REFRESH_REQUEST = "/auth/refresh-request"
    }

    suspend fun request() {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val messageClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(node.id, PATH_REFRESH_REQUEST, ByteArray(0)).await()
            }
        }
    }
}
