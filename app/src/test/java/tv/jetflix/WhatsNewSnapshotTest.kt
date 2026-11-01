package tv.jetflix

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.WhatsNewCard

/** Pixel renders of the What's new card with the real build-47 notes (7 bullets, ~1030 chars):
 *  default text and 1.3x text, scroll top and clamped to the last bullet. The card must cap its
 *  height and keep OK pinned on screen at both scales. */
class WhatsNewSnapshotTest {
    private val tv13 = DeviceConfig.NEXUS_5.copy(
        screenWidth = 1920, screenHeight = 1080,
        orientation = com.android.resources.ScreenOrientation.LANDSCAPE,
        xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH,
        fontScale = 1.3f,
    )
    private val tv10 = tv13.copy(fontScale = 1.0f)

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = tv13,
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    // The real build-47 notes (RELEASE_NOTES.md).
    private val build47Notes = listOf(
        "Settings scrolls when the categories don't fit, so Account and everything above it is reachable on every TV.",
        "A show's status (Continuing or Ended) now actually appears on its page: the app reads the field the server really sends.",
        "The subtitle and audio check mark moves the moment you press OK, instead of after closing and reopening the panel.",
        "New \"What's new in this build\" row in Settings > Updates replays the change list shown after an update installed.",
        "Update checks run on every startup and every 6 hours while the app is on screen, with a red dot left of your name while a newer build exists. A new \"Update notifications\" setting hides the popup and dot; checks still run and installing stays manual.",
        "A show's Cast row now follows the season you have selected. If that season has no actor information on the server, the row stays hidden rather than showing another season's cast.",
        "Focus fixes: pressing Down on the last row of a settings pane stays put, and sweeping right from Search to Home no longer drops the cursor on Play.",
    )

    private fun shot(name: String, config: DeviceConfig, scroll: Int) {
        paparazzi.unsafeUpdateConfig(config)
        paparazzi.snapshot(name = name) {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
                    // A huge initial offset clamps to the max, rendering the last-bullet position.
                    WhatsNewCard(build47Notes, {}, ScrollState(scroll), Modifier.align(Alignment.Center))
                }
            }
        }
    }

    @Test fun cardAtLargeTextScrollTop() = shot("whatsnew_13_top", tv13, 0)

    @Test fun cardAtLargeTextScrolledToLastBullet() = shot("whatsnew_13_bottom", tv13, 100000)

    @Test fun cardAtDefaultText() = shot("whatsnew_10_top", tv10, 0)
}
