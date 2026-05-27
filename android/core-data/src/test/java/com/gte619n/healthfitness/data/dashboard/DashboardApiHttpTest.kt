package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.data.blood.BloodApi
import com.gte619n.healthfitness.data.blood.BloodReadingRepositoryImpl
import com.gte619n.healthfitness.data.blood.BloodTestReportRepositoryImpl
import com.gte619n.healthfitness.network.BackendBaseUrlProvider
import com.gte619n.healthfitness.network.InstantJsonAdapter
import com.gte619n.healthfitness.network.LocalDateJsonAdapter
import com.gte619n.healthfitness.network.sse.MultipartSseClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * MockWebServer-driven HTTP contract tests for the three dashboard
 * endpoints. Replaces both the "Moshi can round-trip a recorded payload"
 * test and the per-repo tests in the spec — same coverage, less surface.
 */
class DashboardApiHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DashboardApi
    private lateinit var bodyCompRepo: BodyCompositionRepositoryImpl
    private lateinit var bloodRepo: BloodMarkerSummaryRepositoryImpl
    private lateinit var dosesRepo: TodaysDosesRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder()
            .add(InstantJsonAdapter)
            .add(LocalDateJsonAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create(DashboardApi::class.java)
        val bloodApi = retrofit.create(BloodApi::class.java)
        // IMPL-AND-04: the blood summary repo now consumes the same
        // BloodReadingRepository the feature-blood module uses. The
        // MockWebServer dispatcher routes both the dashboard's
        // /api/me/blood path and the feature module's reports endpoint
        // so the rewired test path mirrors production.
        val bloodReading = BloodReadingRepositoryImpl(bloodApi)
        val bloodReports = BloodTestReportRepositoryImpl(
            api = bloodApi,
            multipartSseClient = MultipartSseClient(client),
            httpClient = client,
            moshi = moshi,
            baseUrl = object : BackendBaseUrlProvider {
                override val baseUrl: String = server.url("/").toString()
            },
        )
        bodyCompRepo = BodyCompositionRepositoryImpl(api, Dispatchers.Unconfined)
        bloodRepo = BloodMarkerSummaryRepositoryImpl(bloodReading, bloodReports, Dispatchers.Unconfined)
        dosesRepo = TodaysDosesRepositoryImpl(api, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `body composition empty payload returns null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val summary = bodyCompRepo.loadRecent()
        assertNull(summary)
    }

    @Test
    fun `body composition realistic payload converts kg to lb and derives lean mass`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  { "recordId": "1", "metric": "WEIGHT_KG", "value": 90.0,
                    "sampleTime": "2026-04-01T07:00:00Z",
                    "sourcePlatform": "scale", "recordingMethod": null },
                  { "recordId": "2", "metric": "WEIGHT_KG", "value": 88.5,
                    "sampleTime": "2026-05-01T07:00:00Z",
                    "sourcePlatform": "scale", "recordingMethod": null },
                  { "recordId": "3", "metric": "WEIGHT_KG", "value": 88.0,
                    "sampleTime": "2026-05-20T07:00:00Z",
                    "sourcePlatform": "scale", "recordingMethod": null },
                  { "recordId": "4", "metric": "BODY_FAT_PERCENT", "value": 17.0,
                    "sampleTime": "2026-05-20T08:00:00Z",
                    "sourcePlatform": "scale", "recordingMethod": null }
                ]
                """.trimIndent(),
            ),
        )
        val summary = bodyCompRepo.loadRecent()
        assertNotNull(summary)
        summary!!
        // 88 kg ≈ 194.0 lb
        assertEquals(194.0, summary.latestLb, 0.1)
        assertEquals(17.0, summary.latestBodyFatPct!!, 0.001)
        // Lean = 194.0 * (1 - 0.17) ≈ 161.0
        assertEquals(161.0, summary.latestLeanMassLb!!, 0.5)
        // Three weight points are below the 30-point downsample threshold.
        assertEquals(3, summary.series.size)
    }

    @Test
    fun `body composition HTTP 500 surfaces as exception`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(HttpException::class.java) {
            runBlocking { bodyCompRepo.loadRecent() }
        }
    }

    @Test
    fun `blood readings show top 4 dashboard markers in display order`() = runBlocking {
        // The new impl hits both /api/me/blood and /api/me/blood/reports
        // — route the responses with a dispatcher rather than enqueue
        // order so the calls can interleave in any order.
        server.dispatcher = bloodEndpointsDispatcher(
            readingsBody = """
                [
                  { "readingId": "a", "marker": "LDL", "value": 80.0, "unit": "mg/dL",
                    "sampleDate": "2026-05-01", "labSource": null, "notes": null,
                    "reference": { "unit": "mg/dL", "orientation": "LOWER_IS_BETTER",
                                   "goodThreshold": 100.0, "displayMin": 0.0, "displayMax": 200.0 } },
                  { "readingId": "b", "marker": "APO_B", "value": 75.0, "unit": "mg/dL",
                    "sampleDate": "2026-05-01", "labSource": null, "notes": null,
                    "reference": { "unit": "mg/dL", "orientation": "LOWER_IS_BETTER",
                                   "goodThreshold": 90.0, "displayMin": 0.0, "displayMax": 180.0 } },
                  { "readingId": "c", "marker": "HDL", "value": 65.0, "unit": "mg/dL",
                    "sampleDate": "2026-05-01", "labSource": null, "notes": null,
                    "reference": { "unit": "mg/dL", "orientation": "HIGHER_IS_BETTER",
                                   "goodThreshold": 60.0, "displayMin": 0.0, "displayMax": 100.0 } },
                  { "readingId": "d", "marker": "HBA1C", "value": 5.4, "unit": "%",
                    "sampleDate": "2026-05-10", "labSource": null, "notes": null,
                    "reference": { "unit": "%", "orientation": "LOWER_IS_BETTER",
                                   "goodThreshold": 5.7, "displayMin": 4.0, "displayMax": 7.0 } }
                ]
            """.trimIndent(),
            reportsBody = "[]",
        )
        val markers = bloodRepo.loadDashboardMarkers()
        // Top 4 from DISPLAY_ORDER that have readings. The catalog
        // order is [LDL, APO_B, HDL, TOTAL_CHOLESTEROL, …]; with no
        // total-cholesterol reading the panel takes the first 4
        // available: LDL, APO_B, HDL, HBA1C.
        assertEquals(listOf("LDL", "APO_B", "HDL", "HBA1C"), markers.map { it.markerKey })
    }

    @Test
    fun `blood readings empty payload returns empty list`() = runBlocking {
        server.dispatcher = bloodEndpointsDispatcher(
            readingsBody = "[]",
            reportsBody = "[]",
        )
        val markers = bloodRepo.loadDashboardMarkers()
        assertTrue(markers.isEmpty())
    }

    private fun bloodEndpointsDispatcher(readingsBody: String, reportsBody: String): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/api/me/blood/reports") -> MockResponse().setBody(reportsBody)
                    path.endsWith("/api/me/blood") -> MockResponse().setBody(readingsBody)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

    @Test
    fun `todays doses parse window enum and taken flag`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  { "medicationId": "m1", "drugName": "Rosuvastatin", "imageUrl": null,
                    "window": "MORNING", "dose": 10.0, "unit": "mg", "taken": true,
                    "takenAt": "2026-05-20T07:14:00Z" },
                  { "medicationId": "m2", "drugName": "Vitamin D3", "imageUrl": null,
                    "window": "BEDTIME", "dose": 5000.0, "unit": "IU", "taken": false,
                    "takenAt": null }
                ]
                """.trimIndent(),
            ),
        )
        val doses = dosesRepo.loadToday()
        assertEquals(2, doses.size)
        assertEquals("Rosuvastatin", doses.first().drugName)
        assertEquals(com.gte619n.healthfitness.domain.dashboard.DoseWindow.MORNING, doses.first().window)
        assertTrue(doses.first().taken)
        assertNotNull(doses.first().takenAt)
        assertEquals(com.gte619n.healthfitness.domain.dashboard.DoseWindow.BEDTIME, doses[1].window)
        assertNull(doses[1].takenAt)
    }

    @Test
    fun `todays doses empty payload returns empty list`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val doses = dosesRepo.loadToday()
        assertTrue(doses.isEmpty())
    }
}
