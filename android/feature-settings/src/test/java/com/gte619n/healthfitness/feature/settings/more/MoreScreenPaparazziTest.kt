package com.gte619n.healthfitness.feature.settings.more

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.gte619n.healthfitness.domain.profile.Profile
import com.gte619n.healthfitness.ui.HealthFitnessTheme
import org.junit.Rule
import org.junit.Test

/**
 * Visual coverage for the Round 2 Stage B "More" overflow screen.
 *
 * Two snapshots — both production-reachable states:
 *
 *  - [loaded_withProfile] is what every signed-in user sees once the
 *    `/api/me` call resolves: identity header, three nav rows, sign-out
 *    card.
 *  - [noProfile_loadingOrError] is the degradation when the profile
 *    fetch is in flight long enough to render OR the fetch fails. The
 *    identity header drops out, but the menu rows still render so the
 *    user can navigate or sign out regardless.
 *
 * The screen body is driven against [MoreViewModel.UiState] directly
 * via [MoreScreenContent] so this test needs no Hilt graph.
 */
class MoreScreenPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NOTNIGHT),
    )

    @Test
    fun loaded_withProfile() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                MoreScreenContent(
                    state = MoreViewModel.UiState.Loaded(
                        profile = Profile(
                            userId = "u-1",
                            email = "evan@oxos.com",
                            displayName = "Evan Ruff",
                            heightCm = 188,
                        ),
                    ),
                    onNavigateToBlood = {},
                    onNavigateToWorkouts = {},
                    onNavigateToSettings = {},
                    onSignOut = {},
                )
            }
        }
    }

    @Test
    fun noProfile_loadingOrError() {
        paparazzi.snapshot {
            HealthFitnessTheme {
                MoreScreenContent(
                    state = MoreViewModel.UiState.NoProfile,
                    onNavigateToBlood = {},
                    onNavigateToWorkouts = {},
                    onNavigateToSettings = {},
                    onSignOut = {},
                )
            }
        }
    }
}
