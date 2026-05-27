package com.gte619n.healthfitness.feature.bodycomposition.upload

import app.cash.turbine.test
import com.gte619n.healthfitness.domain.bodycomposition.DexaScan
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanRepository
import com.gte619n.healthfitness.domain.bodycomposition.DexaScanSummary
import com.gte619n.healthfitness.domain.bodycomposition.DexaUploadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the upload state machine through the three SSE phases plus
 * the size-limit short-circuit. Uses a [FakeDexaScanRepository] that
 * emits the events the real backend produces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadDexaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `oversized PDF short-circuits to Failed`() = runTest(dispatcher) {
        val repo = FakeDexaScanRepository()
        val vm = UploadDexaViewModel(repo)
        // 26 MB > 25 MB cap.
        val bigBytes = ByteArray((25L * 1024 * 1024 + 1).toInt())
        vm.upload("oversized.pdf", bigBytes)
        advanceUntilIdle()
        val state = vm.state.value as UploadDexaViewModel.UiState.Failed
        assertTrue(state.error.contains("25 MB"))
        assertTrue("repo should not be called when oversized", repo.uploadCalls == 0)
    }

    @Test fun `phase events propagate to InProgress states`() = runTest(dispatcher) {
        val repo = FakeDexaScanRepository()
        val vm = UploadDexaViewModel(repo)

        vm.upload("ok.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46))
        vm.state.test {
            assertEquals(UploadDexaViewModel.UiState.Idle, awaitItem())
            advanceUntilIdle()
            repo.events.emit(DexaUploadEvent.Phase("uploading", "Saving"))
            advanceUntilIdle()
            // onStart fires InProgress("uploading", "Saving your PDF") first
            val s1 = awaitItem()
            assertTrue(s1 is UploadDexaViewModel.UiState.InProgress)
            // Then our explicit emit replaces it
            val s2 = awaitItem() as UploadDexaViewModel.UiState.InProgress
            assertEquals("uploading", s2.phase)
            repo.events.emit(DexaUploadEvent.Phase("extracting", null))
            advanceUntilIdle()
            assertEquals("extracting", (awaitItem() as UploadDexaViewModel.UiState.InProgress).phase)
            repo.events.emit(DexaUploadEvent.Phase("saving", null))
            advanceUntilIdle()
            assertEquals("saving", (awaitItem() as UploadDexaViewModel.UiState.InProgress).phase)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test fun `Complete event yields UiState Complete with the new scan`() = runTest(dispatcher) {
        val repo = FakeDexaScanRepository()
        val vm = UploadDexaViewModel(repo)
        vm.upload("ok.pdf", byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        repo.events.emit(DexaUploadEvent.Complete(scan = scanFixture("scan-42")))
        advanceUntilIdle()
        val s = vm.state.value as UploadDexaViewModel.UiState.Complete
        assertEquals("scan-42", s.scan.scanId)
    }

    @Test fun `Failed event yields UiState Failed with the error`() = runTest(dispatcher) {
        val repo = FakeDexaScanRepository()
        val vm = UploadDexaViewModel(repo)
        vm.upload("ok.pdf", byteArrayOf(1))
        advanceUntilIdle()
        repo.events.emit(DexaUploadEvent.Failed("parse error"))
        advanceUntilIdle()
        val s = vm.state.value as UploadDexaViewModel.UiState.Failed
        assertEquals("parse error", s.error)
    }
}

private fun scanFixture(scanId: String): DexaScan = DexaScan(
    scanId = scanId,
    measuredOn = null,
    sourceFacility = null,
    totalMassLb = null,
    leanTissueLb = null,
    fatTissueLb = null,
    totalBodyFatPercent = null,
    visceralFatLb = null,
    androidGynoidRatio = null,
    trunk = null, android = null, gynoid = null,
    armsTotal = null, armsRight = null, armsLeft = null,
    legsTotal = null, legsRight = null, legsLeft = null,
    bmdTScore = null, bmdZScore = null,
    restingMetabolicRateKcal = null,
)

internal class FakeDexaScanRepository : DexaScanRepository {
    val events = MutableSharedFlow<DexaUploadEvent>(extraBufferCapacity = 8)
    var uploadCalls = 0

    override fun observeScans(): Flow<List<DexaScanSummary>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun refreshScans() = Unit
    override suspend fun getScan(scanId: String): DexaScan = scanFixture(scanId)
    override suspend fun deleteScan(scanId: String) = Unit
    override suspend fun downloadPdf(scanId: String): ByteArray = byteArrayOf()
    override suspend fun patchField(scanId: String, path: String, value: Double?): DexaScan =
        scanFixture(scanId)

    override fun uploadPdf(fileName: String, bytes: ByteArray): Flow<DexaUploadEvent> {
        uploadCalls += 1
        return flow { events.asSharedFlow().collect { emit(it) } }
    }
}
