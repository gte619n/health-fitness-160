package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.CreateMedicationRequest
import com.gte619n.healthfitness.domain.medications.DayOfWeek
import com.gte619n.healthfitness.domain.medications.DiscontinueReason
import com.gte619n.healthfitness.domain.medications.DrugCategory
import com.gte619n.healthfitness.domain.medications.DrugForm
import com.gte619n.healthfitness.domain.medications.FrequencyConfig
import com.gte619n.healthfitness.domain.medications.FrequencyType
import com.gte619n.healthfitness.domain.medications.MedicationStatus
import com.gte619n.healthfitness.domain.medications.TimeSlot
import com.gte619n.healthfitness.domain.medications.TimeWindow
import com.gte619n.healthfitness.network.InstantJsonAdapter
import com.gte619n.healthfitness.network.LocalDateJsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate

/**
 * MockWebServer-driven contract tests for the medications + adherence +
 * drugs Retrofit services. Covers the round-trip from the recorded
 * backend payload shapes into domain models and back out as request
 * bodies on the wire.
 */
class MedicationsApiHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var medsApi: MedicationsApi
    private lateinit var adherenceApi: AdherenceApi
    private lateinit var drugsApi: DrugsApi
    private lateinit var medsRepo: DefaultMedicationRepository
    private lateinit var adherenceRepo: DefaultAdherenceRepository
    private lateinit var drugsRepoCatalogOnly: DefaultDrugRepository

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
        medsApi = retrofit.create(MedicationsApi::class.java)
        adherenceApi = retrofit.create(AdherenceApi::class.java)
        drugsApi = retrofit.create(DrugsApi::class.java)
        medsRepo = DefaultMedicationRepository(medsApi, Dispatchers.Unconfined)
        adherenceRepo = DefaultAdherenceRepository(adherenceApi, Dispatchers.Unconfined)
        // No DrugLookupStreamClient needed here — we don't call lookupStream.
        // Pass a null-safe stub via a no-op cast.
        drugsRepoCatalogOnly = DefaultDrugRepository(
            api = drugsApi,
            lookupClient = NoOpLookupStreamClient,
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list medications maps full payload`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "medicationId": "m1",
                  "drugId": "d1",
                  "drug": { "drugId": "d1", "name": "Testosterone Cypionate",
                            "aliases": ["Test Cyp"], "category": "PRESCRIPTION",
                            "form": "INJECTABLE_VIAL", "defaultUnit": "mg",
                            "commonDoses": ["200mg"], "imageUrl": "https://cdn/img.png",
                            "imageFallback": "/fallbacks/injectable-vial.png",
                            "suggestedMarkers": ["TESTOSTERONE"], "description": null },
                  "customName": null,
                  "status": "ACTIVE",
                  "dose": 200.0, "unit": "mg",
                  "frequency": { "type": "WEEKLY", "timesPerPeriod": 1,
                                 "specificDays": ["MON"], "cycle": null },
                  "timeSlots": [{ "window": "MORNING", "dose": 200.0 }],
                  "protocolId": null, "notes": null, "prescribedBy": null,
                  "startDate": "2026-01-01", "endDate": null,
                  "discontinueReason": null, "discontinueNotes": null,
                  "correlatedMarkers": ["TESTOSTERONE", "ESTRADIOL"],
                  "adherence": { "last30Days": [
                      { "date": "2026-05-01", "taken": true },
                      { "date": "2026-05-02", "taken": false }
                    ], "percentage": 50.0 }
                }]
                """.trimIndent(),
            ),
        )
        val meds = medsRepo.list()
        assertEquals(1, meds.size)
        val m = meds.first()
        assertEquals("m1", m.medicationId)
        assertEquals("Testosterone Cypionate", m.drug?.name)
        assertEquals(MedicationStatus.ACTIVE, m.status)
        assertEquals(200.0, m.dose, 0.001)
        assertEquals(FrequencyType.WEEKLY, m.frequency.type)
        assertEquals(listOf(DayOfWeek.MON), m.frequency.specificDays)
        assertEquals(1, m.timeSlots.size)
        assertEquals(TimeWindow.MORNING, m.timeSlots.first().window)
        assertEquals(2, m.adherence?.last30Days?.size)
        assertEquals(50.0, m.adherence?.percentage ?: 0.0, 0.001)
    }

    @Test
    fun `list medications with status param sends query string`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        medsRepo.list(MedicationStatus.DISCONTINUED)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("status=DISCONTINUED"))
    }

    @Test
    fun `get medication detail maps history entries`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "medicationId": "m1", "drugId": "d1", "drug": null,
                  "customName": "Custom Test", "status": "ACTIVE",
                  "dose": 250.0, "unit": "mg",
                  "frequency": { "type": "DAILY", "timesPerPeriod": 1,
                                 "specificDays": null, "cycle": null },
                  "timeSlots": [], "protocolId": null, "notes": null,
                  "prescribedBy": null,
                  "startDate": "2026-01-01", "endDate": null,
                  "discontinueReason": null, "discontinueNotes": null,
                  "correlatedMarkers": [],
                  "history": [
                    { "historyId": "h1", "changeType": "DOSE_CHANGE",
                      "previousValue": "200 mg", "newValue": "250 mg",
                      "changedAt": "2026-05-20T12:00:00Z", "notes": "labs" }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val detail = medsRepo.get("m1")
        assertEquals("Custom Test", detail.medication.customName)
        assertNull(detail.medication.adherence)
        assertEquals(1, detail.history.size)
        assertEquals("labs", detail.history.first().notes)
    }

    @Test
    fun `create medication POSTs JSON body`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "medicationId": "m-new", "drugId": "d1", "drug": null,
                  "customName": null, "status": "ACTIVE",
                  "dose": 100.0, "unit": "mg",
                  "frequency": { "type": "DAILY", "timesPerPeriod": 1,
                                 "specificDays": null, "cycle": null },
                  "timeSlots": [{ "window": "MORNING", "dose": 100.0 }],
                  "protocolId": null, "notes": null, "prescribedBy": null,
                  "startDate": "2026-05-26", "endDate": null,
                  "discontinueReason": null, "discontinueNotes": null,
                  "correlatedMarkers": [], "adherence": null
                }
                """.trimIndent(),
            ),
        )
        val req = CreateMedicationRequest(
            drugId = "d1",
            dose = 100.0,
            unit = "mg",
            frequency = FrequencyConfig(FrequencyType.DAILY, timesPerPeriod = 1),
            timeSlots = listOf(TimeSlot(TimeWindow.MORNING, 100.0)),
        )
        val med = medsRepo.create(req)
        assertEquals("m-new", med.medicationId)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/me/medications", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body contains drugId", body.contains("\"drugId\":\"d1\""))
        assertTrue("body contains DAILY", body.contains("\"type\":\"DAILY\""))
        assertTrue("body contains MORNING slot", body.contains("\"window\":\"MORNING\""))
    }

    @Test
    fun `discontinue POSTs reason + notes`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "medicationId": "m1", "drugId": "d1", "drug": null,
                  "customName": null, "status": "DISCONTINUED",
                  "dose": 200.0, "unit": "mg",
                  "frequency": { "type": "WEEKLY", "timesPerPeriod": 1,
                                 "specificDays": null, "cycle": null },
                  "timeSlots": [], "protocolId": null, "notes": null,
                  "prescribedBy": null,
                  "startDate": "2026-01-01", "endDate": "2026-05-20",
                  "discontinueReason": "SWITCHED", "discontinueNotes": "moved to enanthate",
                  "correlatedMarkers": [], "adherence": null
                }
                """.trimIndent(),
            ),
        )
        val med = medsRepo.discontinue("m1", DiscontinueReason.SWITCHED, "moved to enanthate")
        assertEquals(MedicationStatus.DISCONTINUED, med.status)
        assertEquals(DiscontinueReason.SWITCHED, med.discontinueReason)
        val recorded = server.takeRequest()
        assertEquals("/api/me/medications/m1/discontinue", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"reason\":\"SWITCHED\""))
        assertTrue(body.contains("moved to enanthate"))
    }

    @Test
    fun `delete sends DELETE no body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        medsRepo.delete("m1")
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/me/medications/m1", recorded.path)
    }

    @Test
    fun `todays doses maps payload`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{ "medicationId": "m1", "drugName": "Vitamin D3",
                   "imageUrl": null, "window": "MORNING", "dose": 5000.0,
                   "unit": "IU", "taken": false, "takenAt": null }]
                """.trimIndent(),
            ),
        )
        val doses = medsRepo.todaysDoses()
        assertEquals(1, doses.size)
        assertEquals(TimeWindow.MORNING, doses.first().window)
        assertEquals("Vitamin D3", doses.first().drugName)
    }

    @Test
    fun `log dose POSTs window in body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        adherenceRepo.logDose("m1", TimeWindow.EVENING)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/me/medications/m1/adherence", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"window\":\"EVENING\""))
    }

    @Test
    fun `undo dose DELETEs with ISO date + window in path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        adherenceRepo.undoDose("m1", LocalDate.of(2026, 5, 20), TimeWindow.BEDTIME)
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/me/medications/m1/adherence/2026-05-20/BEDTIME", recorded.path)
    }

    @Test
    fun `drug catalog with query parameter sends q string`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{ "drugId": "d1", "name": "Atorvastatin", "aliases": [],
                   "category": "PRESCRIPTION", "form": "TABLET",
                   "defaultUnit": "mg", "commonDoses": ["10mg"],
                   "imageUrl": null, "imageFallback": null,
                   "suggestedMarkers": [], "description": null }]
                """.trimIndent(),
            ),
        )
        val results = drugsRepoCatalogOnly.catalog("atorv")
        assertEquals(1, results.size)
        assertEquals(DrugCategory.PRESCRIPTION, results.first().category)
        assertEquals(DrugForm.TABLET, results.first().form)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("q=atorv"))
    }

    @Test
    fun `unknown enum values fall back to safe defaults`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{ "drugId": "d1", "name": "Unknown", "aliases": [],
                   "category": "FUTURE_CATEGORY", "form": "FUTURE_FORM",
                   "defaultUnit": "mg", "commonDoses": [],
                   "imageUrl": null, "imageFallback": null,
                   "suggestedMarkers": [], "description": null }]
                """.trimIndent(),
            ),
        )
        val results = drugsRepoCatalogOnly.catalog()
        assertEquals(DrugCategory.OTC, results.first().category)
        assertEquals(DrugForm.TABLET, results.first().form)
    }
}
