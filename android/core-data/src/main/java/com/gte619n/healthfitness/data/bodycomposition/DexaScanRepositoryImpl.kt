package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.toDomain
import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.toSummary
import com.gte619n.healthfitness.domain.bodycomposition.DexaScan
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanRepository
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanSummary
import com.gte619n.healthfitness.domain.bodycomposition.DexaUploadEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.sse.MultipartSseClient
import com.gte619n.healthfitness.network.sse.MultipartSseEvent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the DEXA scan list / detail / mutation surface.
 *
 *  - Retrofit ([DexaScanApi]) for list / get / patch / delete.
 *  - [MultipartSseClient] for the multipart-SSE PDF upload — same client
 *    introduced by IMPL-AND-04 for blood-test reports.
 *  - Raw OkHttp call for the binary PDF download (matches the blood
 *    report repo pattern; Retrofit's @Streaming ResponseBody works but
 *    needs an extra binding for a one-shot stream).
 */
@Singleton
internal class DexaScanRepositoryImpl @Inject constructor(
    private val api: DexaScanApi,
    private val multipartSseClient: MultipartSseClient,
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrl: BackendBaseUrlProvider,
) : DexaScanRepository {

    private val scans = MutableSharedFlow<List<DexaScanSummary>>(replay = 1)
    private val phasePayloadAdapter = moshi.adapter(PhasePayload::class.java)
    private val scanAdapter = moshi.adapter(DexaScanDto::class.java)

    override fun observeScans(): Flow<List<DexaScanSummary>> = scans.asSharedFlow()

    override suspend fun refreshScans() {
        val list = api.list().map { it.toSummary() }
            .sortedByDescending { it.measuredOn ?: java.time.LocalDate.MIN }
        scans.emit(list)
    }

    override suspend fun getScan(scanId: String): DexaScan = api.get(scanId).toDomain()

    override suspend fun deleteScan(scanId: String) {
        val response = api.delete(scanId)
        if (!response.isSuccessful) {
            throw IOException("Delete failed: HTTP ${response.code()}")
        }
        refreshScans()
    }

    override suspend fun patchField(scanId: String, path: String, value: Double?): DexaScan {
        // Field-level PATCH. Backend returns the updated scan; the
        // caller folds the response into UI state so we settle to the
        // server's authoritative numbers (handles rounding etc.).
        return api.patchField(scanId, PatchFieldRequest(path, value)).toDomain()
    }

    override suspend fun downloadPdf(scanId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = baseUrl.baseUrl.trimEnd('/') + "/api/me/dexa/scans/$scanId/pdf"
        val request = Request.Builder().url(url).get().build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("PDF download failed: HTTP ${resp.code}")
            }
            resp.body?.bytes() ?: throw IOException("Empty PDF body")
        }
    }

    override fun uploadPdf(fileName: String, bytes: ByteArray): Flow<DexaUploadEvent> = flow {
        val url = (baseUrl.baseUrl.trimEnd('/') + "/api/me/dexa/scans").toHttpUrl()
        val parts = listOf(
            MultipartSseClient.Part(
                name = "file",
                fileName = fileName,
                contentType = PDF_MEDIA,
                body = bytes,
            ),
        )
        try {
            multipartSseClient.stream(url, parts).collect { event ->
                emit(toUploadEvent(event))
            }
            // Refresh on completion so observeScans() reflects the new
            // scan even when the caller doesn't navigate through the
            // detail screen (matches BloodTestReportRepositoryImpl).
            refreshScans()
        } catch (t: Throwable) {
            emit(DexaUploadEvent.Failed(t.localizedMessage ?: "Upload failed"))
        }
    }

    private fun toUploadEvent(event: MultipartSseEvent): DexaUploadEvent {
        val payload: PhasePayload? = runCatching { phasePayloadAdapter.fromJson(event.data) }
            .getOrNull()
        return when (val phase = payload?.phase) {
            "uploading", "extracting", "saving" ->
                DexaUploadEvent.Phase(phase, payload.message)
            "complete" -> {
                val scanObj = payload.scan
                if (scanObj == null) {
                    DexaUploadEvent.Failed("Server reported complete without a scan")
                } else {
                    val dto = runCatching { scanAdapter.fromJsonValue(scanObj) }.getOrNull()
                    if (dto == null) DexaUploadEvent.Failed("Could not parse scan payload")
                    else DexaUploadEvent.Complete(dto.toDomain())
                }
            }
            "failed" -> DexaUploadEvent.Failed(
                payload.error ?: payload.message ?: "Upload failed"
            )
            else -> DexaUploadEvent.Failed("Unknown phase: ${phase ?: "<missing>"}")
        }
    }

    /**
     * SSE payload shape from `DexaScanController`. `scan` is `Any?`
     * because the reflective Moshi adapter gives back a nested
     * `Map<String,Any?>`; we round-trip it through the typed
     * [DexaScanDto] adapter via `fromJsonValue`.
     */
    private data class PhasePayload(
        val phase: String?,
        val message: String?,
        val error: String?,
        val scan: Any?,
    )

    private companion object {
        val PDF_MEDIA = "application/pdf".toMediaType()
    }
}
