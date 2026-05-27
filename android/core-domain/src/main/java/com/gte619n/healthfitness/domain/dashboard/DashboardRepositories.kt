package com.gte619n.healthfitness.domain.dashboard

/**
 * Repository contracts the dashboard ViewModel depends on. The body
 * composition source moved to the canonical
 * `com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionRepository`
 * in Round 2 Stage C — the ViewModel now consumes that snapshot Flow
 * directly. Blood + today's doses still split per-card so a slow
 * blood-panel fetch never blocks the rest of the dashboard.
 *
 * Implementations live in `core-data`; tests use in-memory fakes that
 * satisfy these interfaces.
 */

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
