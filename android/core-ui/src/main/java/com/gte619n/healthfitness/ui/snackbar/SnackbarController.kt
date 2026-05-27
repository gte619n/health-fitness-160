package com.gte619n.healthfitness.ui.snackbar

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Lightweight payload for transient bottom-of-screen messages. `isError`
 * lets screens tag fatal failures so a future themed `SnackbarHost` can
 * render them with the alert color.
 */
data class SnackbarMessage(
    val text: String,
    val isError: Boolean = false,
)

/**
 * Channel-backed controller surfaced to the whole composition via a
 * `CompositionLocal`. Producers (`onClick` handlers, ViewModels via
 * `LaunchedEffect`) call `show()`; the `Scaffold` at the top wires the
 * `hostState` into its `snackbarHost` slot.
 */
class SnackbarController internal constructor(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val channel: Channel<SnackbarMessage>,
) {
    fun show(message: SnackbarMessage) {
        scope.launch { channel.send(message) }
    }

    fun show(text: String, isError: Boolean = false) {
        show(SnackbarMessage(text, isError))
    }
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("SnackbarController not provided — wrap with ProvideSnackbarController")
}

@Composable
fun ProvideSnackbarController(content: @Composable () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val channel = remember { Channel<SnackbarMessage>(capacity = Channel.BUFFERED) }
    val controller = remember(hostState, scope) {
        SnackbarController(hostState, scope, channel)
    }
    LaunchedEffect(channel) {
        for (msg in channel) {
            hostState.showSnackbar(msg.text)
        }
    }
    CompositionLocalProvider(
        LocalSnackbarController provides controller,
        content = content,
    )
}
