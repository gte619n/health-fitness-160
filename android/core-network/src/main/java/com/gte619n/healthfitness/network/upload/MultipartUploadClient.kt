package com.gte619n.healthfitness.network.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multipart upload payload — file name, MIME type, and a lazy
 * [InputStream] producer. The producer is invoked at upload time so
 * callers don't have to buffer the bytes themselves (especially handy
 * when the source is a `ContentResolver` `Uri`).
 *
 * Lives in `core-network` (not `core-data`) so the wrapper for an
 * Android `Uri` (`UriUploads`) can sit alongside this type without
 * dragging the framework `Uri` API into the domain layer.
 */
data class PendingUpload(
    val filename: String,
    val mimeType: String,
    val source: () -> InputStream,
    /** Used when the producer's underlying stream knows its length up-front (file).
     *  `null` falls back to OkHttp's chunked transfer. */
    val sizeBytes: Long? = null,
)

/**
 * Multipart-only uploader. Returns once the server replies with the full
 * response body — no SSE plumbing, no streaming. For multipart+SSE
 * uploads (blood-test, DEXA) the dedicated `MultipartSseClient` still
 * applies.
 *
 * Why a separate helper instead of extending [MultipartSseClient]: the
 * SSE client emits a `Flow<MultipartSseEvent>` and is geared around a
 * long-lived response body; this one collapses to a `Result<T>` with a
 * caller-supplied parser. Trying to reuse one shape for both forced a
 * coroutine boundary either way.
 *
 * IMPL-AND-06: introduced for the gym cover-photo upload
 * (`POST /api/me/gyms/{id}/photo`). The signature was deliberately kept
 * narrow — a single field, a `path` relative to the backend base URL,
 * and a caller-supplied parser — so IMPL-AND-04 / 05 can adopt it for
 * the non-SSE branches of their upload flows without re-shaping the API.
 */
@Singleton
class MultipartUploadClient @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * Posts a single multipart part to [url]. The response body is
     * handed to [parse] inside `use {}` so callers can stream large
     * payloads without forcing the whole thing into memory.
     */
    suspend fun <T> upload(
        url: HttpUrl,
        upload: PendingUpload,
        fieldName: String = "file",
        parse: (ResponseBody) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val media: MediaType = upload.mimeType.toMediaType()
            val body = upload.source().use { input ->
                // Read into a byte array — the InputStream may not be
                // re-readable and OkHttp needs a deterministic body for
                // the Content-Length header (the backend's
                // `MultipartFile.getSize()` check expects it).
                input.readBytes()
            }
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    fieldName,
                    upload.filename,
                    body.toRequestBody(media),
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(multipart)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
                val responseBody = resp.body
                    ?: throw IOException("Empty response body")
                parse(responseBody)
            }
        }
    }

    /**
     * Convenience overload that posts a [File] from disk. The stream is
     * opened lazily so we don't hold a file descriptor longer than the
     * upload itself.
     */
    suspend fun <T> upload(
        url: HttpUrl,
        file: File,
        mimeType: String,
        fieldName: String = "file",
        parse: (ResponseBody) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val media = mimeType.toMediaType()
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    fieldName,
                    file.name,
                    file.asRequestBody(media),
                )
                .build()
            val request = Request.Builder()
                .url(url)
                .post(multipart)
                .build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
                val responseBody = resp.body
                    ?: throw IOException("Empty response body")
                parse(responseBody)
            }
        }
    }
}
