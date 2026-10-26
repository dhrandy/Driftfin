package tv.jetflix

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.components.blurSource
import tv.jetflix.app.ui.theme.JetflixTheme

/** Android 8 to 11 cannot run a live blur; they use a tiny server-resized copy of the same art scaled up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-xhdpi")
class BlurFallbackTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun olderAndroidUsesTinySameArtworkInsteadOfAnOpaqueBar() {
        assertEquals("http://s/Items/a/Images/Backdrop/0?maxWidth=48", blurSource("http://s/Items/a/Images/Backdrop/0?maxWidth=1280"))
        assertEquals("http://s/Items/a/Images/Backdrop?maxWidth=48", blurSource("http://s/Items/a/Images/Backdrop"))
        assertTrue(blurSource("http://s/x?tag=t&maxWidth=1920").contains("maxWidth=48"))
    }

    @Test fun barStillComposesOnApi28WithArtwork() {
        rule.setContent {
            JetflixTheme { TopNavBar(solid = true, selected = JetflixTab.MOVIES, userName = "demo", onTabSelected = {}, onUserClick = {}, blurArtUrl = "http://s/Items/a/Images/Backdrop/0?maxWidth=1280") }
        }
        rule.onNodeWithText("Movies").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlurSourceModernTest {
    @Test fun modernAndroidRequestsASmallCopyToo() {
        assertEquals("http://s/Items/a/Images/Backdrop/0?maxWidth=320", blurSource("http://s/Items/a/Images/Backdrop/0?maxWidth=1920"))
    }
}
