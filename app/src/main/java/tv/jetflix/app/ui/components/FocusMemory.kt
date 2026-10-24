package tv.jetflix.app.ui.components

import androidx.compose.runtime.Composable
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
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    val memory = remember { FocusMemory(saved.value) }
    // Mirror the live key into saved state.
    if (saved.value != memory.key) saved.value = memory.key
    return memory
}

/** Records focus for [id] and, on return to the screen, puts focus back on it. */
@Composable
fun Modifier.restoreFocus(memory: FocusMemory, id: String): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (memory.pending && memory.key == id) {
            try {
                requester.requestFocus()
                memory.pending = false
            } catch (_: Exception) {
                // Not attached yet; leave pending so a later composition can try.
            }
        }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) memory.key = id }
}
