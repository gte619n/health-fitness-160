package com.gte619n.healthfitness.data.medications

import app.cash.turbine.test
import com.gte619n.healthfitness.domain.medications.DrugLookupEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.InstantJsonAdapter
import com.gte619n.healthfitness.network.LocalDateJsonAdapter
import com.gte619n.healthfitness.network.sse.SseConsumer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.sse.EventSources
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * MockWebServer-driven test for the SSE drug-lookup client. Verifies the
 * stream emits Progress → Found / NotFound and completes after each
 * terminal phase.
 */
class DrugLookupStreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DrugLookupStreamClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val moshi = Moshi.Builder()
            .add(InstantJsonAdapter)
            .add(LocalDateJsonAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
        val ok = OkHttpClient.Builder().build()
        val sse = SseConsumer(EventSources.createFactory(ok))
        val baseUrl = object : BackendBaseUrlProvider {
            override val baseUrl: String = server.url("/").toString()
        }
        client = DrugLookupStreamClient(sse, baseUrl, moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `streams searching → generating_image → complete`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    buildString {
                        append("data: {\"phase\":\"searching\",\"message\":\"Looking up drug information...\"}\n\n")
                        append("data: {\"phase\":\"generating_image\",\"message\":\"Generating image...\"}\n\n")
                        append("""data: {"phase":"complete","drug":{"drugId":"d1","name":"Testosterone Cypionate","aliases":[],"category":"PRESCRIPTION","form":"INJECTABLE_VIAL","defaultUnit":"mg","commonDoses":[],"imageUrl":null,"imageFallback":"/fallbacks/injectable-vial.png","suggestedMarkers":[],"description":null}}""")
                        append("\n\n")
                    },
                ),
        )

        client.stream("testosterone").test(timeout = 5.seconds) {
            val a = awaitItem() as DrugLookupEvent.Progress
            assertEquals("searching", a.phase)
            val b = awaitItem() as DrugLookupEvent.Progress
            assertEquals("generating_image", b.phase)
            val c = awaitItem() as DrugLookupEvent.Found
            assertEquals("Testosterone Cypionate", c.drug.name)
            cancelAndConsumeRemainingEvents()
        }

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/drugs/lookup/stream", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"query\":\"testosterone\""))
    }

    @Test
    fun `streams searching → not_found`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    buildString {
                        append("data: {\"phase\":\"searching\",\"message\":null}\n\n")
                        append("data: {\"phase\":\"not_found\",\"message\":\"No drug found matching: zzz\"}\n\n")
                    },
                ),
        )

        client.stream("zzz").test(timeout = 5.seconds) {
            val a = awaitItem() as DrugLookupEvent.Progress
            assertEquals("searching", a.phase)
            val b = awaitItem() as DrugLookupEvent.NotFound
            assertEquals("No drug found matching: zzz", b.message)
            cancelAndConsumeRemainingEvents()
        }
    }
}
