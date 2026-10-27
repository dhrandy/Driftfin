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
 * "Double click on the far left" (Randy, strict form after the build-21 device report): a Left press
 * fires [onEdge] only when the previous fresh Left press found focus ALREADY at rest on the boundary.
 * A press that moves focus - including the press that ARRIVES at the edge - never counts, so no single
 * press can open Search regardless of what was pressed or armed before. The first at-rest press arms;
 * the second fresh at-rest press fires. Holding Left repeats the key without a key-up; those repeats
 * stop at the boundary and never arm or fire. Any other key disarms. Controls that handle Left
 * themselves (a text field, the player's seek) consume the key first and never reach this.
 */
@Composable
fun Modifier.onLeftEdge(enabled: Boolean, onEdge: () -> Unit): Modifier {
    val focusManager = LocalFocusManager.current
    // True while the Left key is physically down, so key-repeat from holding the button never fires.
    var leftHeld by remember { mutableStateOf(false) }
    // True only after a fresh Left press that found focus ALREADY at the edge; the next such press fires.
    var armedAtEdge by remember { mutableStateOf(false) }
    return this.onKeyEvent { e ->
        if (!enabled) { armedAtEdge = false; return@onKeyEvent false }
        when {
            e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown -> {
                val held = leftHeld
                leftHeld = true
                if (focusManager.moveFocus(FocusDirection.Left)) {
                    // This press MOVED focus (it may have arrived at the edge): it was not pressed at
                    // rest on the boundary, so it never counts - and it clears any stale arm.
                    armedAtEdge = false
                } else if (!held) {
                    if (armedAtEdge) { armedAtEdge = false; onEdge() } else armedAtEdge = true
                }
                true
            }
            e.key == Key.DirectionLeft && e.type == KeyEventType.KeyUp -> { leftHeld = false; true }
            else -> { armedAtEdge = false; false }
        }
    }
}
