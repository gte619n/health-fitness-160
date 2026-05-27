package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.DrugLookupEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.sse.SseConsumer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient
import okhttp3.sse.EventSources

/**
 * Test stub for [DrugLookupStreamClient] used by the catalog-only API
 * tests — they never invoke `lookupStream`. We can't pass `null` because
 * [DefaultDrugRepository] takes the concrete type, so we subclass and
 * override the only entry point used externally.
 *
 * Includes [KotlinJsonAdapterFactory] in the bundled Moshi instance so
 * the parent constructor's `moshi.adapter<LookupPhaseDto>()` call doesn't
 * fail at init time.
 */
internal object NoOpLookupStreamClient : DrugLookupStreamClient(
    sseConsumer = SseConsumer(
        EventSources.createFactory(OkHttpClient.Builder().build()),
    ),
    baseUrl = object : BackendBaseUrlProvider {
        override val baseUrl: String = "http://localhost/"
    },
    moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
) {
    override fun stream(query: String): Flow<DrugLookupEvent> = emptyFlow()
}
