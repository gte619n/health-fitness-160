package com.gte619n.healthfitness.data.blood

import com.gte619n.healthfitness.data.blood.BloodMappers.toDomain
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.domain.blood.BloodTestReportRepository
import com.gte619n.healthfitness.domain.blood.UploadEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.sse.MultipartSseClient
import com.gte619n.healthfitness.network.sse.MultipartSseEvent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the report-list + per-report endpoints and the multipart-SSE
 * upload flow. Three external collaborators:
 *
 *  - [BloodApi] — CRUD via Retrofit (list, get, delete).
 *  - [MultipartSseClient] — chunked upload + SSE phase stream.
 *  - [OkHttpClient] — raw GET for the binary PDF download (Retrofit's
 *    `@Streaming ResponseBody` works but pulls in an extra binding;
 *    the auth interceptor is already on the shared client).
 */
@Singleton
internal class BloodTestReportRepositoryImpl @Inject constructor(
    private val api: BloodApi,
    private val multipartSseClient: MultipartSseClient,
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrl: BackendBaseUrlProvider,
) : BloodTestReportRepository {

    private val state = MutableStateFlow<List<BloodTestReport>>(emptyList())
    private val phasePayloadAdapter = moshi.adapter(PhasePayload::class.java)
    private val reportAdapter = moshi.adapter(BloodTestReportDto::class.java)

    override fun observeReports(): Flow<List<BloodTestReport>> = state.asStateFlow()

    override suspend fun refresh() {
        state.value = api.listReports().map { it.toDomain() }
            .sortedByDescending { it.sampleDate ?: java.time.LocalDate.MIN }
    }

    override suspend fun get(reportId: String): BloodTestReport =
        api.getReport(reportId).toDomain()

    override suspend fun delete(reportId: String) {
        api.deleteReport(reportId)
        state.update { current -> current.filterNot { it.reportId == reportId } }
    }

    override suspend fun downloadPdf(reportId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = baseUrl.baseUrl.trimEnd('/') + "/api/me/blood/reports/$reportId/pdf"
        val request = Request.Builder().url(url).get().build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("PDF download failed: HTTP ${resp.code}")
            }
            resp.body?.bytes() ?: throw IOException("Empty PDF body")
        }
    }

    override fun upload(fileName: String, pdfBytes: ByteArray): Flow<UploadEvent> = flow {
        val url = (baseUrl.baseUrl.trimEnd('/') + "/api/me/blood/reports").toHttpUrl()
        val parts = listOf(
            MultipartSseClient.Part(
                name = "file",
                fileName = fileName,
                contentType = PDF_MEDIA,
                body = pdfBytes,
            ),
        )
        try {
            multipartSseClient.stream(url, parts).collect { event ->
                emit(toUploadEvent(event))
            }
            // Refresh on completion so the new report shows up in
            // observeReports() (the SSE stream doesn't update state by
            // itself — Complete carries the report, but reading order
            // matters and an explicit refresh keeps that consistent).
            refresh()
        } catch (t: Throwable) {
            emit(UploadEvent.Failed(t.localizedMessage ?: "Upload failed"))
        }
    }

    private fun toUploadEvent(event: MultipartSseEvent): UploadEvent {
        val payload: PhasePayload? = runCatching { phasePayloadAdapter.fromJson(event.data) }
            .getOrNull()
        return when (payload?.phase) {
            "uploading" -> UploadEvent.Uploading
            "extracting" -> UploadEvent.Extracting
            "saving" -> UploadEvent.Saving
            "complete" -> {
                val reportObj = payload.report
                if (reportObj == null) {
                    UploadEvent.Failed("Server reported complete without a report")
                } else {
                    val dto = runCatching { reportAdapter.fromJsonValue(reportObj) }.getOrNull()
                    if (dto == null) UploadEvent.Failed("Could not parse report payload")
                    else UploadEvent.Complete(dto.toDomain())
                }
            }
            "failed" -> UploadEvent.Failed(payload.error ?: payload.message ?: "Upload failed")
            else -> UploadEvent.Failed("Unknown phase: ${payload?.phase ?: "<missing>"}")
        }
    }

    /**
     * SSE payload shape from `BloodTestController`. The `report` field is
     * `Any?` because Moshi gives back a nested `Map<String,Any?>` from
     * the reflective adapter — we round-trip it through the typed
     * [BloodTestReportDto] adapter via `fromJsonValue`.
     */
    private data class PhasePayload(
        val phase: String?,
        val message: String?,
        val error: String?,
        val report: Any?,
    )

    private companion object {
        val PDF_MEDIA = "application/pdf".toMediaType()
    }
}
