package com.gte619n.healthfitness.data.blood

import app.cash.turbine.test
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.ReferenceRange
import com.gte619n.healthfitness.domain.blood.UploadEvent
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.InstantJsonAdapter
import com.gte619n.healthfitness.network.LocalDateJsonAdapter
import com.gte619n.healthfitness.network.sse.MultipartSseClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * MockWebServer-driven tests for the blood data layer:
 *  - `BloodReadingRepositoryImpl` round-trips list / create / delete
 *    and emits state-flow updates on each op.
 *  - `BloodTestReportRepositoryImpl.upload` drives a multipart-SSE
 *    stream and emits the expected UploadEvent ladder.
 */
class BloodApiHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BloodApi
    private lateinit var moshi: Moshi
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        moshi = Moshi.Builder()
            .add(InstantJsonAdapter)
            .add(LocalDateJsonAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
        client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create(BloodApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listReadings parses LDL row with reference range`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "readingId": "r1", "marker": "LDL", "value": 95.0, "unit": "mg/dL",
                    "sampleDate": "2026-04-01", "labSource": "Quest", "notes": null,
                    "reference": { "unit": "mg/dL", "orientation": "LOWER_IS_BETTER",
                                   "goodThreshold": 100, "displayMin": 0, "displayMax": 200 } }
                ]
                """.trimIndent(),
            ),
        )
        val repo = BloodReadingRepositoryImpl(api)
        repo.refresh()
        repo.observeReadings().test(timeout = 2.seconds) {
            val list = awaitItem()
            assertEquals(1, list.size)
            val r = list[0]
            assertEquals(BloodMarker.LDL, r.marker)
            assertEquals(95.0, r.value, 0.0)
            assertEquals("Quest", r.labSource)
            assertEquals(ReferenceRange.Orientation.LOWER_IS_BETTER, r.reference.orientation)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `createReading posts marker enum name and updates state`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                { "readingId": "r2", "marker": "HBA1C", "value": 5.4, "unit": "%",
                  "sampleDate": "2026-05-20", "labSource": null, "notes": null,
                  "reference": { "unit": "%", "orientation": "LOWER_IS_BETTER",
                                 "goodThreshold": 5.7, "displayMin": 4, "displayMax": 7 } }
                """.trimIndent(),
            ),
        )
        val repo = BloodReadingRepositoryImpl(api)
        repo.refresh() // empty
        val created = repo.create(
            marker = BloodMarker.HBA1C,
            value = 5.4,
            unit = null,
            sampleDate = LocalDate.of(2026, 5, 20),
            labSource = null,
            notes = null,
        )
        assertEquals(BloodMarker.HBA1C, created.marker)

        // First request — list. Second — POST.
        server.takeRequest()
        val postReq = server.takeRequest()
        assertEquals("POST", postReq.method)
        val postedBody = postReq.body.readUtf8()
        assertTrue(postedBody.contains("\"marker\":\"HBA1C\""))
        assertTrue(postedBody.contains("\"sampleDate\":\"2026-05-20\""))

        repo.observeReadings().test(timeout = 2.seconds) {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("r2", list[0].readingId)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `delete reading removes row from state`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "readingId": "r1", "marker": "LDL", "value": 95, "unit": "mg/dL",
                    "sampleDate": "2026-04-01", "labSource": null, "notes": null,
                    "reference": { "unit": "mg/dL", "orientation": "LOWER_IS_BETTER",
                                   "goodThreshold": 100, "displayMin": 0, "displayMax": 200 } }
                ]
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))
        val repo = BloodReadingRepositoryImpl(api)
        repo.refresh()
        repo.delete("r1")
        repo.observeReadings().test(timeout = 2.seconds) {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `upload report streams Uploading then Extracting then Complete`() = runTest {
        // The SSE response is served on the upload endpoint, then the
        // refresh() that runs on Complete hits the list endpoint and
        // returns the single new report.
        // SSE data fields are line-based; the JSON below MUST be on one
        // line — newlines split the data field and would cause `phase`
        // to be missing on parse.
        val reportJson = """{"reportId":"rep-1","sampleDate":"2026-05-20","labSource":"Quest","createdAt":"2026-05-21T10:00:00Z","markers":[{"name":"LDL","value":85,"unit":"mg/dL","refRangeLow":0,"refRangeHigh":100,"flag":null}]}"""
        val sseBody = buildString {
            append("data: {\"phase\":\"uploading\",\"message\":\"Saving your PDF\"}\n\n")
            append("data: {\"phase\":\"extracting\",\"message\":\"Reading the report\"}\n\n")
            append("data: {\"phase\":\"complete\",\"report\":$reportJson}\n\n")
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        // refresh() after Complete
        server.enqueue(
            MockResponse().setBody("[$reportJson]"),
        )

        val multipart = MultipartSseClient(client)
        val baseUrl = object : BackendBaseUrlProvider {
            override val baseUrl: String = server.url("/").toString()
        }
        val repo = BloodTestReportRepositoryImpl(api, multipart, client, moshi, baseUrl)
        val events = mutableListOf<UploadEvent>()
        repo.upload("report.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46))
            .collect { events += it }

        assertEquals(3, events.size)
        assertEquals(UploadEvent.Uploading, events[0])
        assertEquals(UploadEvent.Extracting, events[1])
        val complete = events[2] as UploadEvent.Complete
        assertEquals("rep-1", complete.report.reportId)
        assertEquals("Quest", complete.report.labSource)
        assertEquals(1, complete.report.markers.size)
        assertEquals("LDL", complete.report.markers[0].name)
    }

    @Test
    fun `upload report emits Failed on phase failed`() = runTest {
        val sseBody = "data: {\"phase\":\"failed\",\"error\":\"Could not parse PDF\"}\n\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        // refresh() after the (single) terminal event still runs; serve an empty list.
        server.enqueue(MockResponse().setBody("[]"))

        val multipart = MultipartSseClient(client)
        val baseUrl = object : BackendBaseUrlProvider {
            override val baseUrl: String = server.url("/").toString()
        }
        val repo = BloodTestReportRepositoryImpl(api, multipart, client, moshi, baseUrl)
        val events = mutableListOf<UploadEvent>()
        repo.upload("report.pdf", byteArrayOf()).collect { events += it }
        assertEquals(1, events.size)
        val failed = events[0] as UploadEvent.Failed
        assertEquals("Could not parse PDF", failed.error)
    }

    @Test
    fun `downloadPdf returns response bytes`() = runBlocking {
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) // %PDF-1.4
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/pdf")
                .setBody(okio.Buffer().write(pdfBytes)),
        )
        val baseUrl = object : BackendBaseUrlProvider {
            override val baseUrl: String = server.url("/").toString()
        }
        val repo = BloodTestReportRepositoryImpl(
            api,
            MultipartSseClient(client),
            client,
            moshi,
            baseUrl,
        )
        val got = repo.downloadPdf("rep-1")
        assertEquals(pdfBytes.toList(), got.toList())

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertNotNull(recorded.path)
        assertTrue(
            "expected /api/me/blood/reports/rep-1/pdf, got ${recorded.path}",
            recorded.path!!.endsWith("/api/me/blood/reports/rep-1/pdf"),
        )
    }
}
