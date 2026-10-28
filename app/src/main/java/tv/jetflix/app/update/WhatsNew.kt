package tv.jetflix.app.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixType

/**
 * What changed, shown once after an update installs. Release builds embed the matching
 * RELEASE_NOTES.md section as assets/whatsnew.txt at build time (see android.yml), so the card
 * always shows the build's real changes; [fallbackNotes] covers local/dev builds with no asset.
 */
object WhatsNew {
    fun notes(context: android.content.Context): List<String> {
        val fromAsset = runCatching {
            context.assets.open("whatsnew.txt").bufferedReader().use { it.readLines() }
        }.getOrNull()?.let { parse(it) }
        return fromAsset?.takeIf { it.isNotEmpty() } ?: fallbackNotes
    }

    /** Keeps the bullet text, drops headings and blank lines. */
    internal fun parse(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Fixes:", ignoreCase = true) && !it.startsWith("#") }

    private val fallbackNotes: List<String> = listOf(
        "Check for updates moved to the top of Settings, and the app now tells you on start when a new build is out.",
        "4K filter on Movies and Shows: Filter, then Resolution.",
        "New trailer button icon.",
        "Fixes: Back from an actor page no longer jumps the details page, the actor page no longer closes itself over a paused movie, and returning to the app no longer lands on the screensaver.",
        "Trailers try harder to start with YouTube captions off.",
        "Library grids: top posters no longer clip under the menu bar.",
        "Details backdrop art is centred.",
        "Pause cast strip is easier to read on bright scenes.",
    )
}

/**
 * The once-per-update card. [show] when the installed build is newer than the last one it was shown for;
 * OK (or Back) dismisses and records the build so it never shows again for it.
 */
@Composable
fun WhatsNewDialog(notes: List<String>, onDismiss: () -> Unit) {
    val okFocus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF222222), RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("What's new", style = JetflixType.sectionTitle, color = Color.White)
            notes.forEach { Text("\u2022  " + it, style = JetflixType.body, color = Color(0xFFE5E5E5)) }
            Button(onClick = onDismiss, modifier = Modifier.focusRequester(okFocus)) { Text("OK") }
        }
    }
    LaunchedEffect(Unit) { repeat(10) { try { okFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
}
