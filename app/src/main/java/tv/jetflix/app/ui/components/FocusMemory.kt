package tv.jetflix.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Remembers which card had focus so Back from a details page lands on the same card.
 * [key] survives leaving the screen (saved state); [pending] is true only on the first
 * composition after returning, so scrolling later never steals focus.
 */
class FocusMemory(initialKey: String?) {
    var key by mutableStateOf(initialKey)
    var pending by mutableStateOf(initialKey != null)
    val hasKey get() = key != null
    /** The remembered card's requester, registered while that card is composed. The screen's
     *  focus-group enter guard redirects here so the restore's own request is not cancelled. */
    var restoreTarget: FocusRequester? = null
}

@Composable
fun rememberFocusMemory(initialKey: String? = null): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(initialKey) }
    val memory = remember { FocusMemory(saved.value) }
    // Mirror the live key into saved state.
    if (saved.value != memory.key) saved.value = memory.key
    return memory
}

/** Records focus for [id] and, on return to the screen, puts focus back on it. */
@Composable
fun Modifier.restoreFocus(memory: FocusMemory, id: String): Modifier {
    val requester = remember { FocusRequester() }
    // Only the remembered card registers itself: the enter guard redirects to exactly this node.
    DisposableEffect(memory.key) {
        if (memory.key == id) memory.restoreTarget = requester
        onDispose { if (memory.restoreTarget === requester) memory.restoreTarget = null }
    }
    LaunchedEffect(Unit) {
        if (memory.pending && memory.key == id && !NavSweep.active) {
            // A single request can lose to the group's default focus entry right after a Back
            // return (the card composes a few frames before focus settles): retry briefly.
            repeat(20) {
                try {
                    requester.requestFocus()
                    memory.pending = false
                    return@LaunchedEffect
                } catch (_: Exception) {
                    // Not attached yet; leave pending so a later composition can try.
                    kotlinx.coroutines.delay(60)
                }
            }
        }
    }
    return this
        .focusRequester(requester)
        // hasFocus, not isFocused: card composables carry the focusable internally, so the node this
    // modifier wraps is the focused card's parent - isFocused here would never record the key.
    .onFocusChanged { if (it.hasFocus) memory.key = id }
}
