package com.gte619n.healthfitness.network.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE event yielded from [MultipartSseClient.stream] — narrower than the
 * [SseEvent] sealed type that wraps OkHttp's `EventSource`. Multipart
 * uploads can't ride `EventSource.Factory` (it builds GET-only requests),
 * so this helper hand-rolls the chunked-text/event-stream parser. The
 * caller doesn't need `Open` / `Closed` markers — the cold [Flow] starts
 * with the upload and ends when the body is exhausted.
 */
data class MultipartSseEvent(val event: String?, val data: String)

/**
 * Posts a `multipart/form-data` request and decodes the response body as
 * `text/event-stream`. The flow is cold — the upload begins on the first
 * collector, the IO is on [Dispatchers.IO], and cancelling the collector
 * closes the OkHttp call (the underlying socket).
 *
 * Reusable for any backend endpoint shaped as "PDF upload → progress
 * stream → terminal event": blood-test PDF extraction (IMPL-AND-04) and
 * DEXA scan upload (IMPL-AND-05) both use this exact pattern.
 *
 * Notes on the parser:
 *  - Lines starting with `:` are SSE comments (heartbeat); skipped.
 *  - `event:` sets the next emission's event name.
 *  - `data:` is appended; multiple `data:` lines join with `\n`.
 *  - A blank line flushes the accumulated event.
 *  - End-of-body flushes any trailing event without a blank line.
 */
@Singleton
class MultipartSseClient @Inject constructor(
    private val client: OkHttpClient,
) {
    data class Part(
        val name: String,
        val fileName: String,
        val contentType: MediaType,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Part) return false
            return name == other.name &&
                fileName == other.fileName &&
                contentType == other.contentType &&
                body.contentEquals(other.body)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + body.contentHashCode()
            return result
        }
    }

    fun stream(url: HttpUrl, parts: List<Part>): Flow<MultipartSseEvent> = flow {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { p ->
                    addFormDataPart(
                        p.name,
                        p.fileName,
                        p.body.toRequestBody(p.contentType),
                    )
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(multipart)
            .header("Accept", "text/event-stream")
            .build()

        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val source: BufferedSource = response.body?.source()
                ?: throw IOException("Empty response body")

            var eventName: String? = null
            val dataBuf = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (dataBuf.isNotEmpty()) {
                            emit(MultipartSseEvent(eventName, dataBuf.toString()))
                            eventName = null
                            dataBuf.clear()
                        }
                    }
                    line.startsWith(":") -> Unit // comment / heartbeat
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                        dataBuf.append(line.substring(5).trimStart())
                    }
                    // Unknown SSE field; ignore (id:, retry: …).
                }
            }
            // Final event without a trailing blank line (servers do this).
            if (dataBuf.isNotEmpty()) {
                emit(MultipartSseEvent(eventName, dataBuf.toString()))
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)
}
