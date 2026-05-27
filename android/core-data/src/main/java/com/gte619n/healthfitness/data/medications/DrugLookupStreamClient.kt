package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.DrugLookupEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.sse.SseConsumer
import com.gte619n.healthfitness.network.sse.SseEvent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens an SSE stream against `POST /api/drugs/lookup/stream` and turns
 * each Jackson JSON payload into a typed [DrugLookupEvent].
 *
 * Terminal phases (`complete`, `not_found`, `failed`) cause the upstream
 * SSE to close (the backend calls `emitter.complete()`), which propagates
 * through [SseConsumer] as either a terminal [SseEvent.Closed] or a
 * [SseEvent.Failure] depending on the OkHttp side. We rely on the JSON
 * `phase` field as the canonical signal and let the flow complete after
 * emitting any terminal event.
 *
 * Wire shape verified against `DrugLookupController.lookupStream` —
 * payload is `{ phase, message?, error?, drug? }` where `drug` is a
 * `DrugResponse` exactly matching [DrugDto].
 */
@Singleton
internal open class DrugLookupStreamClient @Inject constructor(
    private val sseConsumer: SseConsumer,
    private val baseUrl: BackendBaseUrlProvider,
    private val moshi: Moshi,
) {
    private val phaseAdapter = moshi.adapter(LookupPhaseDto::class.java)
    private val requestAdapter = moshi.adapter(LookupRequestDto::class.java)

    open fun stream(query: String): Flow<DrugLookupEvent> = flow {
        val request = Request.Builder()
            .url(baseUrl.baseUrl.trimEnd('/') + "/api/drugs/lookup/stream")
            .header("Accept", "text/event-stream")
            .post(requestAdapter.toJson(LookupRequestDto(query))
                .toRequestBody(JSON))
            .build()

        var terminal = false
        sseConsumer.stream(request).collect { event ->
            if (terminal) return@collect
            when (event) {
                is SseEvent.Open -> Unit
                is SseEvent.Closed -> {
                    // If backend completed without a terminal phase, treat
                    // as failure so the UI doesn't hang.
                    if (!terminal) {
                        terminal = true
                        emit(DrugLookupEvent.Failed("Stream closed unexpectedly"))
                    }
                }
                is SseEvent.Failure -> {
                    terminal = true
                    val msg = event.cause?.message
                        ?: event.response?.let { "HTTP ${it.code}" }
                        ?: "Stream failed"
                    emit(DrugLookupEvent.Failed(msg))
                }
                is SseEvent.Data -> {
                    val dto = runCatching { phaseAdapter.fromJson(event.payload) }
                        .getOrNull() ?: return@collect

                    when (dto.phase) {
                        "complete" -> {
                            val drug = dto.drug?.let(MedicationMapper::toDomain)
                            terminal = true
                            if (drug != null) emit(DrugLookupEvent.Found(drug))
                            else emit(DrugLookupEvent.Failed("Lookup completed without a drug"))
                        }
                        "not_found" -> {
                            terminal = true
                            emit(DrugLookupEvent.NotFound(dto.message))
                        }
                        "failed" -> {
                            terminal = true
                            emit(DrugLookupEvent.Failed(dto.error ?: dto.message ?: "Lookup failed"))
                        }
                        else -> emit(DrugLookupEvent.Progress(dto.phase, dto.message))
                    }
                }
            }
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
