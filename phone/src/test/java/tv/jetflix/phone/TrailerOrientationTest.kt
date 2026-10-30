package tv.jetflix.phone

import android.content.pm.ActivityInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The trailer screen rotates with the phone itself; leaving it puts the app back to portrait (reported 10/29: "can it auto rotate?"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailerOrientationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun trailerAutoRotatesAndLeavingRestoresPortrait() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        var showing by mutableStateOf(true)
        rule.setContent { if (showing) OrientationWhileShowing(PlaybackOrientation.TRAILER) }
        rule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, rule.activity.requestedOrientation)
        showing = false
        rule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
    }
}
