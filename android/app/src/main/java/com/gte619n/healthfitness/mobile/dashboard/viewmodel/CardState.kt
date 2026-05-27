package com.gte619n.healthfitness.mobile.dashboard.viewmodel

/**
 * Per-card state envelope. Same intent as IMPL-AND-00's screen-wide
 * `UiState`, but scoped to a single card so a slow blood fetch doesn't
 * gate the weight chart.
 *
 * Empty isn't a third state — `Loaded(emptyList())` or `Loaded(null)`
 * is the empty path; the card body interprets it.
 */
sealed interface CardState<out T> {
    data object Loading : CardState<Nothing>
    data class Loaded<T>(val data: T) : CardState<T>
    data class Error(val message: String, val cause: Throwable? = null) : CardState<Nothing>
}
