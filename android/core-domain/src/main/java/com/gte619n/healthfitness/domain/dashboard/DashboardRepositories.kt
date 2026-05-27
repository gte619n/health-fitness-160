package com.gte619n.healthfitness.domain.dashboard

/**
 * Repository contracts the dashboard ViewModel depends on. Three sources
 * are deliberately split — the ViewModel exposes per-card sub-states so
 * a slow blood-panel fetch never blocks the weight chart.
 *
 * Implementations live in `core-data`; tests use in-memory fakes that
 * satisfy these interfaces.
 */

interface BodyCompositionRepository {
    /**
     * Latest body composition window for the user.
     * @return materialised [WeightSummary] or `null` if the user has no
     *   weight readings at all (empty backend response).
     */
    suspend fun loadRecent(): WeightSummary?
}

interface BloodMarkerSummaryRepository {
    /**
     * Top-4 dashboard markers (Testosterone, LDL, ApoB, HbA1c) in display
     * order. Markers with no reading are omitted, not rendered empty.
     */
    suspend fun loadDashboardMarkers(): List<BloodMarkerSummary>
}

interface TodaysDosesRepository {
    /**
     * All scheduled doses for the current calendar day in window order
     * (morning → afternoon → evening → bedtime). The dashboard's preview
     * row in this IMPL takes the first 3.
     */
    suspend fun loadToday(): List<TodaysDoseSummary>
}
