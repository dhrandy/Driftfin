package tv.jetflix.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
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
    /** A long description scrolls at about one line every 2.5 s (lines are 21dp), slow enough to read. */
    const val READ_DP_PER_SECOND = 8.4f
    const val READ_START_DELAY_MS = 2_500L
    const val READ_END_HOLD_MS = 3_000L
    /** Pause, scroll to the end at reading speed, hold; [onReading] is true for the whole time so the next slide waits. */
    suspend fun readThrough(maxValue: () -> Int, density: Float, animate: suspend (target: Int, durationMs: Int) -> Unit, onReading: (Boolean) -> Unit) {
        onReading(true)
        try {
            delay(READ_START_DELAY_MS)
            val max = maxValue()
            if (max > 0) {
                animate(max, scrollMs(max, density))
                delay(READ_END_HOLD_MS)
            }
        } finally {
            onReading(false)
        }
    }

    fun scrollMs(distancePx: Int, density: Float): Int = (distancePx / (READ_DP_PER_SECOND * density) * 1000f).toInt().coerceAtLeast(1)
    fun mayRun(onPlayer: Boolean, urls: List<ScreensaverSlide>) = !onPlayer && urls.isNotEmpty()
}

/** One screensaver picture with the title's name (as logo art when the server has it) and description shown over it. */
data class ScreensaverSlide(val url: String, val title: String? = null, val overview: String? = null, val logoUrl: String? = null)

/** Fullscreen, rotating backdrops of unwatched movies. The caller consumes the first key press to dismiss it. */
@Composable
fun ScreensaverOverlay(urls: List<ScreensaverSlide>) {
    var index by remember { mutableIntStateOf(0) }
    // The next slide waits until a long description has been scrolled to its end and held there.
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(urls) {
        while (urls.size > 1) {
            delay(ScreensaverRules.SLIDE_MS)
            while (reading) delay(500)
            index = (index + 1) % urls.size
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = "Screensaver" }) {
        Crossfade(targetState = urls[index % urls.size], animationSpec = tween(1500), label = "screensaver") { slide ->
            Box(Modifier.fillMaxSize()) {
                ArtImage(url = slide.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (!slide.title.isNullOrBlank() || !slide.overview.isNullOrBlank()) {
                    ScreensaverText(slide, Modifier.align(Alignment.BottomStart), onReading = { reading = it })
                }
            }
        }
    }
}

/** Title and description over a scrim that grows with the text. A long description scrolls slowly inside a bounded box instead of being cut. */
@Composable
internal fun ScreensaverText(
    slide: ScreensaverSlide,
    modifier: Modifier = Modifier,
    scroll: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    onReading: (Boolean) -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        modifier.fillMaxWidth().background(
            Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black.copy(alpha = 0.9f)),
        ).padding(top = 72.dp),
    ) {
        Column(Modifier.padding(start = 56.dp, end = 56.dp, bottom = 40.dp).fillMaxWidth(0.62f)) {
            if (!slide.title.isNullOrBlank()) {
                // The title's logo art when the server has it (requested), the plain-text title otherwise.
                TitleOrLogo(
                    title = slide.title, logoUrl = slide.logoUrl,
                    maxLogoHeight = 48.dp, maxLogoWidth = 300.dp,
                    textStyle = JetflixType.heroTitle.copy(fontSize = 28.sp, lineHeight = 32.sp), textColor = Color.White,
                )
            }
            if (!slide.overview.isNullOrBlank()) {
                LaunchedEffect(slide) {
                    ScreensaverRules.readThrough(
                        maxValue = { scroll.maxValue },
                        density = density,
                        animate = { target, ms -> scroll.animateScrollTo(target, tween(durationMillis = ms, easing = androidx.compose.animation.core.LinearEasing)) },
                        onReading = onReading,
                    )
                }
                Text(
                    slide.overview,
                    style = JetflixType.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
                    color = Color(0xFFE5E5E5),
                    modifier = Modifier.padding(top = 8.dp).heightIn(max = 126.dp).verticalScroll(scroll),
                )
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
    // Coming back to the app counts as activity: a screensaver that started while the app was away is
    // cleared and the idle clock restarts, so the user never returns to a screensaver.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) lastInput = System.nanoTime()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
