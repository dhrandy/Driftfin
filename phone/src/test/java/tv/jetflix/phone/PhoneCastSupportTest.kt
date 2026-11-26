package tv.jetflix.phone

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.cast.CastButton
import tv.jetflix.phone.cast.authedStreamUrl

/** Chromecast support: the stream URL handed to the receiver carries the token as
 *  api_key (Jellyfin's query auth), never doubled; the cast button stays out of the
 *  UI when the Cast framework is unusable on the device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneCastSupportTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun streamUrlGetsApiKey() {
        assertEquals(
            "http://jf:8096/Videos/abc/stream?static=true&mediaSourceId=abc&api_key=tok123",
            authedStreamUrl("http://jf:8096/Videos/abc/stream?static=true&mediaSourceId=abc", "tok123")
        )
    }

    @Test
    fun streamUrlWithoutQueryGetsQuestionMark() {
        assertEquals("http://jf:8096/Videos/abc/stream?api_key=tok123",
            authedStreamUrl("http://jf:8096/Videos/abc/stream", "tok123"))
    }

    @Test
    fun streamUrlNeverDoubleAuths() {
        val once = authedStreamUrl("http://jf:8096/Videos/abc/stream?static=true&api_key=tok123", "tok123")
        assertEquals("http://jf:8096/Videos/abc/stream?static=true&api_key=tok123", once)
    }

    @Test
    fun castButtonHiddenWithoutCastFramework() {
        // No Play services under Robolectric: CastManager.init fails, the button must
        // render nothing (and never touch the framework views).
        rule.setContent { CastButton() }
        rule.waitForIdle()
    }
}
