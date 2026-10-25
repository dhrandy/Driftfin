package tv.jetflix.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixType
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
    fun mayRun(onPlayer: Boolean, urls: List<ScreensaverSlide>) = !onPlayer && urls.isNotEmpty()
}

/** One screensaver picture with the title's name and description shown over it. */
data class ScreensaverSlide(val url: String, val title: String? = null, val overview: String? = null)

/** Fullscreen, rotating backdrops of unwatched movies. The caller consumes the first key press to dismiss it. */
@Composable
fun ScreensaverOverlay(urls: List<ScreensaverSlide>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        while (urls.size > 1) { delay(ScreensaverRules.SLIDE_MS); index = (index + 1) % urls.size }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = "Screensaver" }) {
        Crossfade(targetState = urls[index % urls.size], animationSpec = tween(1500), label = "screensaver") { slide ->
            Box(Modifier.fillMaxSize()) {
                ArtImage(url = slide.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (!slide.title.isNullOrBlank() || !slide.overview.isNullOrBlank()) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(220.dp).background(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, end = 56.dp, bottom = 40.dp).fillMaxWidth(0.5f)) {
                        if (!slide.title.isNullOrBlank()) {
                            Text(slide.title, style = JetflixType.heroTitle.copy(fontSize = 28.sp, lineHeight = 32.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (!slide.overview.isNullOrBlank()) {
                            Text(slide.overview, style = JetflixType.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = Color(0xFFE5E5E5), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Set by the player: true while the user has paused playback. Only a paused player may be covered by the screensaver. */
object PlaybackPause {
    val paused = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/**
 * Wraps the app. After [idleMs] without a key press (and not while [blocked], e.g. playback), loads art and shows it
 * fullscreen. The key press that wakes it is swallowed and never reaches the screen underneath.
 */
@Composable
fun IdleScreensaverHost(
    blocked: Boolean,
    loadSlides: suspend () -> List<ScreensaverSlide>,
    idleMs: Long = ScreensaverRules.IDLE_MS,
    content: @Composable () -> Unit,
) {
    var lastInput by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var urls by remember { androidx.compose.runtime.mutableStateOf<List<ScreensaverSlide>?>(null) }
    LaunchedEffect(lastInput, blocked) {
        urls = null
        if (blocked) return@LaunchedEffect
        delay(idleMs)
        val loaded = try { loadSlides() } catch (_: Exception) { emptyList() }
        if (ScreensaverRules.mayRun(blocked, loaded)) urls = loaded
    }
    androidx.compose.runtime.SideEffect { ButtonSounds.screensaverOn = urls != null }
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
