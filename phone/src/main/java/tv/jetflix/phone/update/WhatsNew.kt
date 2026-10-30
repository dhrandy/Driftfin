package tv.jetflix.phone.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What changed, shown once after an update installs. Release builds can embed the matching release-notes section as
 * assets/whatsnew.txt at build time; [fallbackNotes] covers local and test builds with no asset.
 */
object WhatsNew {
    fun notes(context: android.content.Context): List<String> {
        val fromAsset = runCatching { context.assets.open("whatsnew.txt").bufferedReader().use { it.readLines() } }.getOrNull()?.let { parse(it) }
        return fromAsset?.takeIf { it.isNotEmpty() } ?: fallbackNotes
    }

    /** Keeps the bullet text, drops headings and blank lines. */
    internal fun parse(lines: List<String>): List<String> =
        lines.map { it.trim().removePrefix("- ").removePrefix("* ") }.filter { it.isNotEmpty() && !it.equals("Fixes:", ignoreCase = true) && !it.startsWith("#") }

    /** Shown when this build is newer than the last build whose notes were dismissed. */
    fun shouldShow(currentBuild: Int, lastSeenBuild: Int) = currentBuild > lastSeenBuild

    private val fallbackNotes = listOf(
        "Check for updates and install them from Settings.",
        "Pick another saved profile from Settings without a password.",
        "Autoplay the next episode with a 5 second countdown.",
        "Title logos on Home and Details. Turn them off in Settings.",
    )
}

@Composable
fun WhatsNewDialog(notes: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                notes.forEach { Text("\u2022  $it", fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK", fontSize = 16.sp) } },
    )
}
