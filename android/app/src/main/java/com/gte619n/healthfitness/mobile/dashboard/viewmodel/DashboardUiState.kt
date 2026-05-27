package com.gte619n.healthfitness.mobile.dashboard.viewmodel

import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummary
import com.gte619n.healthfitness.domain.dashboard.TodaysDoseSummary
import com.gte619n.healthfitness.domain.dashboard.WeightSummary

/**
 * Aggregate dashboard state — three independent per-card sub-states.
 * The Compose layer reads this and switches on each card individually.
 *
 *  - [bodyComposition] is `Loaded(null)` when the user has no body-comp
 *    data at all (server returned an empty list). The hero card shows
 *    a "Connect Google Health" CTA in that case — wired in IMPL-AND-02.
 *  - [blood] is `Loaded(emptyList())` when no dashboard markers have
 *    readings. The panel hides itself.
 *  - [todaysDoses] is `Loaded(emptyList())` when nothing is scheduled
 *    today. The today card renders "No scheduled doses today".
 */
data class DashboardUiState(
    val bodyComposition: CardState<WeightSummary?>,
    val blood: CardState<List<BloodMarkerSummary>>,
    val todaysDoses: CardState<List<TodaysDoseSummary>>,
) {
    companion object {
        val initial = DashboardUiState(
            bodyComposition = CardState.Loading,
            blood = CardState.Loading,
            todaysDoses = CardState.Loading,
        )
    }
}
