package tv.jetflix.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * One more Left when focus cannot move any further left calls [onEdge]. A Left that can still move focus just moves it.
 * Controls that handle Left themselves (a text field, the player's seek) consume the key first and never reach this.
 */
@Composable
fun Modifier.onLeftEdge(enabled: Boolean, onEdge: () -> Unit): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onKeyEvent { e ->
        if (enabled && e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) {
            if (!focusManager.moveFocus(FocusDirection.Left)) onEdge()
            true
        } else false
    }
}
