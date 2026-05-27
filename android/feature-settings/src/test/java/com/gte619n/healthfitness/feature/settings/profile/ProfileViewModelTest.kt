package com.gte619n.healthfitness.feature.settings.profile

import app.cash.turbine.test
import com.gte619n.healthfitness.domain.profile.Profile
import com.gte619n.healthfitness.domain.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
    fun `loads profile on init then exposes Loaded`() = runTest(dispatcher) {
        val repo = FakeProfileRepository(
            initial = Profile("u-1", "a@b", "Alice", heightCm = 180),
        )
        val vm = ProfileViewModel(repo)
        vm.state.test {
            assertEquals(ProfileViewModel.UiState.Loading, awaitItem())
            val loaded = awaitItem()
            assertTrue(loaded is ProfileViewModel.UiState.Loaded)
            val state = loaded as ProfileViewModel.UiState.Loaded
            assertEquals(180, state.profile.heightCm)
            assertEquals(false, state.saving)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveHeight flips saving then updates profile`() = runTest(dispatcher) {
        val repo = FakeProfileRepository(
            initial = Profile("u-1", "a@b", "Alice", heightCm = 180),
        )
        val vm = ProfileViewModel(repo)
        vm.state.test {
            // Loading -> Loaded(180)
            awaitItem(); awaitItem()
            vm.saveHeight(feet = 6, inches = 2)
            val saving = awaitItem() as ProfileViewModel.UiState.Loaded
            assertEquals(true, saving.saving)
            val saved = awaitItem() as ProfileViewModel.UiState.Loaded
            assertEquals(false, saved.saving)
            // 6 ft 2 in = 74 in × 2.54 = 187.96 → rounds to 188 cm
            assertEquals(188, saved.profile.heightCm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get failure surfaces UiState_Error`() = runTest(dispatcher) {
        val repo = FakeProfileRepository(initial = null)
        val vm = ProfileViewModel(repo)
        vm.state.test {
            assertEquals(ProfileViewModel.UiState.Loading, awaitItem())
            val err = awaitItem()
            assertTrue(err is ProfileViewModel.UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save failure keeps Loaded and surfaces saveError`() = runTest(dispatcher) {
        val repo = FakeProfileRepository(
            initial = Profile("u-1", "a@b", "Alice", heightCm = 180),
            failSave = true,
        )
        val vm = ProfileViewModel(repo)
        vm.state.test {
            awaitItem(); awaitItem()
            vm.saveHeight(feet = 5, inches = 10)
            val saving = awaitItem() as ProfileViewModel.UiState.Loaded
            assertEquals(true, saving.saving)
            val failed = awaitItem() as ProfileViewModel.UiState.Loaded
            assertEquals(false, failed.saving)
            assertTrue(failed.saveError != null)
            // Profile retained at the prior value.
            assertEquals(180, failed.profile.heightCm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeProfileRepository(
        private val initial: Profile?,
        private val failSave: Boolean = false,
    ) : ProfileRepository {
        override suspend fun get(): Result<Profile> =
            if (initial == null) Result.failure(RuntimeException("no profile"))
            else Result.success(initial)

        override suspend fun updateHeightCm(heightCm: Int?): Result<Profile> {
            if (failSave) return Result.failure(RuntimeException("backend down"))
            val base = initial ?: return Result.failure(RuntimeException("no profile"))
            return Result.success(base.copy(heightCm = heightCm))
        }
    }
}
