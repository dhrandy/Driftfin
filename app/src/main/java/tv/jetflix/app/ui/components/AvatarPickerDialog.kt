package tv.jetflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.jetflix.app.data.Avatars
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/**
 * "Choose your avatar" panel: the 20 bundled avatars in a 5x4 grid. OK picks one, Back cancels.
 * The current pick starts focused (or the first avatar when none is picked yet). Stored locally on this TV.
 */
@Composable
fun AvatarPickerContent(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
) {
    Column(
        modifier
            .background(Color(0xFF222222), RoundedCornerShape(16.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Choose your avatar", style = JetflixType.sectionTitle, color = Color.White)
        Avatars.all.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { entry ->
                    val startHere = if (selectedId != 0) entry.id == selectedId else entry.id == 1
                    Surface(
                        onClick = { onSelect(entry.id) },
                        modifier = if (startHere && initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JetflixColors.Surface,
                            focusedContainerColor = JetflixColors.SurfaceLight,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, JetflixColors.Red), shape = CircleShape),
                        ),
                    ) {
                        AvatarImage(entry, 72.dp, Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarPickerDialog(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    // usePlatformDefaultWidth = false: the platform dialog theme narrows the window on TV,
    // which clipped the 5th avatar column on build 48 (only 16 of 20 visible). Full-width
    // window; the grid sizes itself and stays centred.
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AvatarPickerContent(selectedId, onSelect, initialFocus = initialFocus)
    }
    // requestFocus returns false before the window has focus, so retry until it takes (same pattern as ProfilePickerDialog).
    LaunchedEffect(Unit) {
        repeat(20) {
            val done = try { initialFocus.requestFocus() } catch (_: Exception) { false }
            if (done) return@LaunchedEffect
            kotlinx.coroutines.delay(60)
        }
    }
}
