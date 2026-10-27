package tv.jetflix.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * "Double click on the far left" (Randy): a Left press fires [onEdge] only when the previous fresh Left
 * press already touched the boundary chain - either it moved focus (the press that ARRIVES at the edge
 * counts) or it was itself an edge press that only armed. A single accidental Left at rest at the edge arms
 * instead of opening Search; holding Left repeats the key without a key-up and those repeats stop at the
 * boundary, never firing. Any other key disarms. Controls that handle Left themselves (a text field, the
 * player's seek) consume the key first and never reach this.
 */
@Composable
fun Modifier.onLeftEdge(enabled: Boolean, onEdge: () -> Unit): Modifier {
    val focusManager = LocalFocusManager.current
    // True while the Left key is physically down, so key-repeat from holding the button never fires.
    var leftHeld by remember { mutableStateOf(false) }
    // True after a fresh Left press that moved focus or armed at the edge; the next fresh edge press fires.
    var leftChain by remember { mutableStateOf(false) }
    return this.onKeyEvent { e ->
        if (!enabled) { leftChain = false; return@onKeyEvent false }
        when {
            e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown -> {
                val held = leftHeld
                leftHeld = true
                if (focusManager.moveFocus(FocusDirection.Left)) {
                    leftChain = true
                } else if (!held) {
                    if (leftChain) { leftChain = false; onEdge() } else leftChain = true
                }
                true
            }
            e.key == Key.DirectionLeft && e.type == KeyEventType.KeyUp -> { leftHeld = false; true }
            else -> { leftChain = false; false }
        }
    }
}
