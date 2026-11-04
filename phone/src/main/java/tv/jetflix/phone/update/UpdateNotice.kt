package tv.jetflix.phone.update

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import tv.jetflix.phone.ui.DriftfinColors

/** The newest phone update the quiet checker found. The Settings tab dot and Settings observe it. */
object UpdateNoticeState {
    val available = MutableStateFlow<ReleaseInfo?>(null)

    /** UI tests set this false so rendering the shell never fires a real update check (live network, shared state). */
    var checksEnabled = true
}

/**
 * A quiet update check at app start, then every 6 hours while the app is open and in the foreground
 * (the TV app's cadence). It only fills [UpdateNoticeState], which shows the red dot on the Settings
 * tab. It never downloads or installs anything - installing stays a manual choice in Settings.
 */
@Composable
fun UpdateDotCheck(
    currentBuild: Int,
    updater: () -> Updater = { Updater() },
    /** Recheck cadence while the app runs. Default 6 hours: light on GitHub and the network. */
    recheckIntervalMs: Long = 6 * 60 * 60 * 1000L,
) {
    if (!UpdateNoticeState.checksEnabled) return
    // The recheck is a foreground activity: Android keeps composition alive while the app is
    // backgrounded, so LaunchedEffect disposal alone does not bound the loop - gate on STARTED.
    // Returning to the foreground relaunches the effect and re-checks immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inForeground) {
        if (!inForeground) return@LaunchedEffect
        suspend fun checkOnce() {
            val r = withContext(Dispatchers.IO) { updater().check(currentBuild) }
            when (r) {
                is CheckResult.Available -> UpdateNoticeState.available.value = r.info
                is CheckResult.UpToDate -> UpdateNoticeState.available.value = null
                is CheckResult.Failed -> Unit // keep the current state; the next cycle retries
            }
        }
        checkOnce()
        while (true) {
            delay(recheckIntervalMs)
            checkOnce()
        }
    }
}

/** The Settings tab icon, badged with the red update dot while a newer build is known. */
@Composable
fun SettingsTabIcon(icon: ImageVector, label: String) {
    val available by UpdateNoticeState.available.collectAsState()
    if (available != null) {
        BadgedBox(badge = { Badge(containerColor = DriftfinColors.Red, modifier = Modifier.testTag("update-dot")) }) {
            Icon(icon, contentDescription = label)
        }
    } else {
        Icon(icon, contentDescription = label)
    }
}
