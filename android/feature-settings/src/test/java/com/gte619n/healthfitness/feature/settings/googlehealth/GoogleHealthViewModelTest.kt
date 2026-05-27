package com.gte619n.healthfitness.feature.settings.googlehealth

import android.content.Intent
import app.cash.turbine.test
import com.gte619n.healthfitness.data.auth.GoogleHealthScopeRepositoryApi
import com.gte619n.healthfitness.data.auth.HealthAuthFlow
import com.gte619n.healthfitness.domain.googlehealth.GoogleHealthRepository
import com.gte619n.healthfitness.domain.googlehealth.GoogleHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleHealthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial Loading transitions to Disconnected when status connected_is_false`() =
        runTest(dispatcher) {
            val backend = FakeGoogleHealthRepository(status = GoogleHealthStatus(false, null))
            val vm = GoogleHealthViewModel(backend, FakeScopeRepo())
            vm.state.test {
                assertEquals(GoogleHealthViewModel.UiState.Loading, awaitItem())
                val state = awaitItem()
                assertTrue(state is GoogleHealthViewModel.UiState.Disconnected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `connect with immediate Resolved code POSTs and transitions to Connected`() =
        runTest(dispatcher) {
            val backend = FakeGoogleHealthRepository(
                status = GoogleHealthStatus(false, null),
                statusAfterConnect = GoogleHealthStatus(true, 1716700000L),
            )
            val scope = FakeScopeRepo(initial = HealthAuthFlow.Resolved("code-A"))
            val vm = GoogleHealthViewModel(backend, scope)
            vm.state.test {
                awaitItem(); awaitItem() // Loading → Disconnected()
                vm.connect()
                val connecting = awaitItem() as GoogleHealthViewModel.UiState.Disconnected
                assertEquals(true, connecting.connecting)
                val connected = awaitItem() as GoogleHealthViewModel.UiState.Connected
                assertEquals(1716700000L, connected.connectedAtEpochSeconds)
                assertEquals(1, backend.connectCalls.size)
                assertEquals("code-A", backend.connectCalls.first())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `disconnect transitions through disconnecting then Disconnected`() = runTest(dispatcher) {
        val backend = FakeGoogleHealthRepository(
            status = GoogleHealthStatus(true, 1716700000L),
        )
        val vm = GoogleHealthViewModel(backend, FakeScopeRepo())
        vm.state.test {
            awaitItem(); awaitItem() // Loading → Connected
            vm.disconnect()
            val disconnecting = awaitItem() as GoogleHealthViewModel.UiState.Connected
            assertEquals(true, disconnecting.disconnecting)
            val disconnected = awaitItem()
            assertTrue(disconnected is GoogleHealthViewModel.UiState.Disconnected)
            assertEquals(1, backend.disconnectCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status failure surfaces UiState_Error`() = runTest(dispatcher) {
        val backend = FakeGoogleHealthRepository(statusFailure = RuntimeException("boom"))
        val vm = GoogleHealthViewModel(backend, FakeScopeRepo())
        vm.state.test {
            awaitItem()
            val err = awaitItem()
            assertTrue(err is GoogleHealthViewModel.UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scope failure on connect surfaces UiState_Error`() = runTest(dispatcher) {
        val backend = FakeGoogleHealthRepository(status = GoogleHealthStatus(false, null))
        val scope = FakeScopeRepo(initial = HealthAuthFlow.Failed("user cancelled"))
        val vm = GoogleHealthViewModel(backend, scope)
        vm.state.test {
            awaitItem(); awaitItem()
            vm.connect()
            awaitItem() // Disconnected(connecting = true)
            val err = awaitItem()
            assertTrue(err is GoogleHealthViewModel.UiState.Error)
            assertEquals("user cancelled", (err as GoogleHealthViewModel.UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConsentResult resolved leads to Connected via submitAuthCode`() = runTest(dispatcher) {
        val backend = FakeGoogleHealthRepository(
            status = GoogleHealthStatus(false, null),
            statusAfterConnect = GoogleHealthStatus(true, 1717000000L),
        )
        val scope = FakeScopeRepo(parsed = HealthAuthFlow.Resolved("code-after-consent"))
        val vm = GoogleHealthViewModel(backend, scope)
        advanceUntilIdle() // drain init
        vm.onConsentResult(null)
        advanceUntilIdle()
        val finalState = vm.state.value
        assertTrue(finalState is GoogleHealthViewModel.UiState.Connected)
        assertEquals(
            1717000000L,
            (finalState as GoogleHealthViewModel.UiState.Connected).connectedAtEpochSeconds,
        )
        assertEquals(listOf("code-after-consent"), backend.connectCalls)
    }

    private class FakeGoogleHealthRepository(
        private val status: GoogleHealthStatus = GoogleHealthStatus(false, null),
        private val statusAfterConnect: GoogleHealthStatus? = null,
        private val statusFailure: Throwable? = null,
        private val connectFailure: Throwable? = null,
    ) : GoogleHealthRepository {
        val connectCalls: MutableList<String> = mutableListOf()
        var disconnectCalls = 0
        private var connected = false

        override suspend fun status(): Result<GoogleHealthStatus> {
            statusFailure?.let { return Result.failure(it) }
            return if (connected && statusAfterConnect != null) {
                Result.success(statusAfterConnect)
            } else {
                Result.success(status)
            }
        }

        override suspend fun connectWithServerAuthCode(serverAuthCode: String): Result<Unit> {
            connectCalls += serverAuthCode
            connectFailure?.let { return Result.failure(it) }
            connected = true
            return Result.success(Unit)
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls++
            connected = false
            return Result.success(Unit)
        }
    }

    private class FakeScopeRepo(
        private val initial: HealthAuthFlow = HealthAuthFlow.Resolved("default-code"),
        private val parsed: HealthAuthFlow = HealthAuthFlow.Resolved("default-parsed"),
    ) : GoogleHealthScopeRepositoryApi {
        override suspend fun requestHealthAuthorization(): HealthAuthFlow = initial
        override fun parseConsentResult(data: Intent?): HealthAuthFlow = parsed
    }
}
