package tv.jetflix.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

/** Idle timing for the in-app screensaver. Never runs during playback. */
object ScreensaverRules {
    const val IDLE_MS = 5 * 60 * 1000L
    const val SLIDE_MS = 12_000L
    fun mayRun(onPlayer: Boolean, urls: List<String>) = !onPlayer && urls.isNotEmpty()
}

/** Fullscreen, rotating backdrops of unwatched movies. The caller consumes the first key press to dismiss it. */
@Composable
fun ScreensaverOverlay(urls: List<String>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        while (urls.size > 1) { delay(ScreensaverRules.SLIDE_MS); index = (index + 1) % urls.size }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = "Screensaver" }) {
        Crossfade(targetState = urls[index % urls.size], animationSpec = tween(1500), label = "screensaver") { url ->
            ArtImage(url = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * Wraps the app. After [idleMs] without a key press (and not while [blocked], e.g. playback), loads art and shows it
 * fullscreen. The key press that wakes it is swallowed and never reaches the screen underneath.
 */
@Composable
fun IdleScreensaverHost(
    blocked: Boolean,
    loadUrls: suspend () -> List<String>,
    idleMs: Long = ScreensaverRules.IDLE_MS,
    content: @Composable () -> Unit,
) {
    var lastInput by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var urls by remember { androidx.compose.runtime.mutableStateOf<List<String>?>(null) }
    LaunchedEffect(lastInput, blocked) {
        urls = null
        if (blocked) return@LaunchedEffect
        delay(idleMs)
        val loaded = try { loadUrls() } catch (_: Exception) { emptyList() }
        if (ScreensaverRules.mayRun(blocked, loaded)) urls = loaded
    }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            val showing = urls != null
            lastInput = System.nanoTime()
            showing
        },
    ) {
        content()
        urls?.let { ScreensaverOverlay(it) }
    }
}
