package tv.jetflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/**
 * "Who's watching?" panel: the profiles saved on this TV for the current server.
 * OK on a name switches to it (no password, the server is asked to confirm the saved login first);
 * the signed-in profile is marked and OK on it just closes. Sign out keeps every saved profile.
 */
@Composable
fun ProfilePickerContent(
    profiles: List<String>,
    currentName: String?,
    connecting: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
) {
    Column(
        modifier
            .background(Color(0xFF222222), RoundedCornerShape(16.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Who's watching?", style = JetflixType.sectionTitle, color = Color.White)
        profiles.forEach { name ->
            val isCurrent = name.equals(currentName, ignoreCase = true)
            Button(
                onClick = { onSelect(name) },
                modifier = if (isCurrent && initialFocus != null) Modifier.width(320.dp).focusRequester(initialFocus) else Modifier.width(320.dp),
                colors = ButtonDefaults.colors(
                    containerColor = JetflixColors.Surface,
                    contentColor = Color.White,
                    focusedContainerColor = JetflixColors.SurfaceLight,
                    focusedContentColor = Color.White,
                ),
            ) { Text(if (isCurrent) "$name (signed in)" else name) }
        }
        if (connecting) Text("Switching...", style = JetflixType.caption, color = JetflixColors.TextSecondary)
        if (error != null) Text(error, style = JetflixType.caption, color = JetflixColors.Red)
        Button(
            onClick = onSignOut,
            modifier = Modifier.width(320.dp),
            colors = ButtonDefaults.colors(
                containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                contentColor = Color.White,
                focusedContainerColor = JetflixColors.Red,
                focusedContentColor = Color.White,
            ),
        ) { Text("Sign out") }
    }
}

@Composable
fun ProfilePickerDialog(
    profiles: List<String>,
    currentName: String?,
    connecting: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        ProfilePickerContent(profiles, currentName, connecting, error, onSelect, onSignOut, initialFocus = initialFocus)
    }
    // requestFocus returns false before the window has focus, so retry until it takes (same pattern as ExitDialog).
    LaunchedEffect(Unit) {
        repeat(20) {
            val done = try { initialFocus.requestFocus() } catch (_: Exception) { false }
            if (done) return@LaunchedEffect
            kotlinx.coroutines.delay(60)
        }
    }
}
