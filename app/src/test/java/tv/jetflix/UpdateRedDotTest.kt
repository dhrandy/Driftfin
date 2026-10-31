package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.update.GhAsset
import tv.jetflix.app.update.ReleaseInfo
import tv.jetflix.app.update.UpdateNoticeState
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixTheme

/** User request: "put a red dot to the left of the username if there is an update" - shown only while one exists. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UpdateRedDotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() { UpdateNoticeState.available.value = null }

    private fun bar() {
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    TopNavBar(JetflixTab.HOME, "Demo", {}, {}, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun dotShowsWhileUpdateAvailable() {
        UpdateNoticeState.available.value = ReleaseInfo(47, GhAsset("jetflix-release.apk", "https://example.test/x", 1L), null)
        bar()
        rule.onNodeWithContentDescription("Update available").assertExists()
    }

    @Test fun noDotWhenUpToDate() {
        UpdateNoticeState.available.value = null
        bar()
        rule.onNodeWithContentDescription("Update available").assertDoesNotExist()
    }
}
