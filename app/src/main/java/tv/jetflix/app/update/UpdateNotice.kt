package tv.jetflix.app.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.jetflix.app.ui.theme.JetflixType

/**
 * A quiet update check at every app start (never cached away), then a periodic recheck while the app runs.
 * If a newer build exists, a small note shows for a few seconds and fades - once per newly-seen build, so a
 * recheck finding the same build does not nag. The red dot by the username stays while an update is available.
 * It never downloads or installs anything - installing stays a manual choice in Settings.
 */
/**
 * The newest update the startup check found. Settings reads it so the update row can open straight
 * on "Download" when the notice already named the build - one OK press instead of Check, then OK.
 */
object UpdateNoticeState {
    /** Observed by the top bar (red dot) and Settings (straight-to-download). */
    val available = kotlinx.coroutines.flow.MutableStateFlow<ReleaseInfo?>(null)
}

@Composable
fun StartupUpdateNotice(
    currentBuild: Int,
    modifier: Modifier = Modifier,
    updater: () -> Updater = { Updater() },
    /** Periodic recheck cadence while the app runs. Default 6 hours: light on GitHub and the network. */
    recheckIntervalMs: Long = 6 * 60 * 60 * 1000L,
    /** "Update notifications" in Settings. The loop lives in composition: it runs only while the app is
     *  open (no background promise), and the startup check ALWAYS runs and feeds Settings even when the
     *  popup/dot are turned off - only the notices and the periodic recheck are gated. */
    notices: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
) {
    var available by remember { mutableStateOf<Int?>(null) }
    var visible by remember { mutableStateOf(false) }
    var announcedBuild by remember { mutableStateOf<Int?>(null) }
    val noticesOn by (notices ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsState()
    // The periodic recheck is a foreground activity: Android keeps composition alive while the app is
    // backgrounded, so LaunchedEffect disposal alone does not bound the loop - gate on STARTED instead.
    // Returning to the foreground relaunches the effect and re-checks immediately.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(noticesOn, inForeground) {
        suspend fun checkOnce() = withContext(Dispatchers.IO) { updater().check(currentBuild) }
        fun handle(r: CheckResult) {
            when (r) {
                is CheckResult.Available -> {
                    UpdateNoticeState.available.value = r.info
                    if (noticesOn && r.info.build != announcedBuild) {
                        announcedBuild = r.info.build
                        available = r.info.build
                        visible = true
                    }
                }
                is CheckResult.UpToDate -> UpdateNoticeState.available.value = null
                is CheckResult.Failed -> Unit // keep the current state; the next cycle retries
            }
        }
        if (!inForeground) { visible = false; return@LaunchedEffect } // backgrounded: no requests at all
        handle(checkOnce())                    // the startup (or return-to-foreground) check: always, never cached away
        if (!noticesOn) { visible = false; return@LaunchedEffect }
        while (true) {
            if (visible) { delay(8_000); visible = false }
            delay(recheckIntervalMs)
            handle(checkOnce())
        }
    }
    val build = available ?: return
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(1_200), label = "updateNotice")
    if (alpha > 0f) UpdateNoticeBadge(build, modifier.alpha(alpha))
}

/** The visible pill, separated from the async check so it can be rendered on its own. */
@Composable
fun UpdateNoticeBadge(build: Int, modifier: Modifier = Modifier) {
    Text(
        "Update available: build $build - open Settings to install it.",
        style = JetflixType.body,
        color = Color.White,
        modifier = modifier.background(Color(0xCC222222), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
