package com.gte619n.healthfitness.data.medications

import com.gte619n.healthfitness.domain.medications.ChangeDoseRequest
import com.gte619n.healthfitness.domain.medications.DiscontinueReason
import com.gte619n.healthfitness.domain.medications.MedicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate

class MedicationRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultMedicationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(MedsTestMoshi.instance))
            .build()
            .create(MedicationsApi::class.java)
        repository = DefaultMedicationRepository(api, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val activeMedJson = """
        {"medicationId":"m1","drugId":null,"drug":null,"customName":"X","status":"ACTIVE",
         "dose":250.0,"unit":"mg",
         "frequency":{"type":"DAILY","timesPerPeriod":1},
         "timeSlots":[],"startDate":"2026-01-01","endDate":null,"correlatedMarkers":[]}
    """.trimIndent()

    @Test
    fun `list maps status query`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[$activeMedJson]"))
        val meds = repository.list(MedicationStatus.ACTIVE)
        assertEquals(1, meds.size)
        assertEquals("m1", meds.first().medicationId)
        assertTrue(server.takeRequest().path!!.contains("status=ACTIVE"))
    }

    @Test
    fun `changeDose posts to dosage endpoint with body`() = runBlocking {
        // [PR#8]
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(activeMedJson))
        repository.changeDose(
            "m1",
            ChangeDoseRequest(dose = 250.0, unit = "mg", startDate = LocalDate.of(2026, 5, 30), changeNotes = "labs"),
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/api/me/medications/m1/dosage"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"dose\":250.0"))
        assertTrue(body.contains("2026-05-30"))
    }

    @Test
    fun `reactivate posts to reactivate endpoint with resume date`() = runBlocking {
        // [PR#8]
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(activeMedJson))
        repository.reactivate("m1", LocalDate.of(2026, 6, 1))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/api/me/medications/m1/reactivate"))
        assertTrue(request.body.readUtf8().contains("2026-06-01"))
    }

    @Test
    fun `discontinue posts reason notes and endDate`() = runBlocking {
        // [PR#8]
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(activeMedJson))
        repository.discontinue("m1", DiscontinueReason.SWITCHED, "moved", LocalDate.of(2026, 4, 1))
        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/api/me/medications/m1/discontinue"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"reason\":\"SWITCHED\""))
        assertTrue(body.contains("2026-04-01"))
    }

    @Test
    fun `delete issues DELETE`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        repository.delete("m1")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertTrue(request.path!!.endsWith("/api/me/medications/m1"))
    }
}
