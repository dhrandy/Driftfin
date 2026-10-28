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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.jetflix.app.ui.theme.JetflixType

/**
 * One quiet update check at app start. If a newer build exists, a small note shows for a few seconds and fades.
 * It never downloads or installs anything - installing stays a manual choice in Settings.
 */
/**
 * The newest update the startup check found. Settings reads it so the update row can open straight
 * on "Download" when the notice already named the build - one OK press instead of Check, then OK.
 */
object UpdateNoticeState {
    @Volatile var available: ReleaseInfo? = null
}

@Composable
fun StartupUpdateNotice(currentBuild: Int, modifier: Modifier = Modifier, updater: () -> Updater = { Updater() }) {
    var available by remember { mutableStateOf<Int?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { updater().check(currentBuild) }
        if (r is CheckResult.Available) {
            UpdateNoticeState.available = r.info
            available = r.info.build
            visible = true
            delay(8_000)
            visible = false
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
