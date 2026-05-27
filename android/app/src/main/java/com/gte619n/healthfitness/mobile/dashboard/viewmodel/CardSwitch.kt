package com.gte619n.healthfitness.mobile.dashboard.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.ui.state.ErrorState
import com.gte619n.healthfitness.ui.state.LoadingState

/**
 * Three-state switch that wraps the loaded body of a dashboard card.
 *
 * Loading and Error render with a fixed [placeholderHeightDp] so the
 * page doesn't reflow as cards transition. The Loaded branch hands the
 * unwrapped data to [content] — most callers just spread it into their
 * existing widget.
 */
@Composable
fun <T> CardSwitch(
    state: CardState<T>,
    placeholderHeightDp: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is CardState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(placeholderHeightDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            LoadingState()
        }
        is CardState.Loaded -> content(state.data)
        is CardState.Error -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(placeholderHeightDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            ErrorState(message = state.message, onRetry = onRetry)
        }
    }
}
