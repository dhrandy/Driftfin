package tv.jetflix.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How often Home re-reads Continue Watching while it is on screen and the app is in the foreground. */
const val HOME_REFRESH_INTERVAL_MS = 60_000L

/**
 * Calls [refresh] when the app returns to the foreground and then every [intervalMs] while Home is composed, the
 * activity is RESUMED and [enabled] (the Settings toggle). RESUMED, not STARTED: an activity that is covered by a
 * system dialog or another window is paused but still started, and must not poll. Composition stays alive in the
 * background, so the lifecycle state gates the loop, as in the update notice. Leaving Home removes this from
 * composition. The first start does not refresh: Home has just done its full load.
 */
@Composable
fun HomeAutoRefresh(enabled: Boolean = true, intervalMs: Long = HOME_REFRESH_INTERVAL_MS, refresh: () -> Unit) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val firstStart = remember { booleanArrayOf(true) }
    val current by androidx.compose.runtime.rememberUpdatedState(refresh)
    LaunchedEffect(resumed, enabled) {
        if (!resumed || !enabled) return@LaunchedEffect
        if (!firstStart[0]) current()
        firstStart[0] = false
        while (true) {
            delay(intervalMs)
            current()
        }
    }
}
