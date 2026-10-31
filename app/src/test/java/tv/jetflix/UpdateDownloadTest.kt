package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.settings.SettingsScreen
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.GhAsset
import tv.jetflix.app.update.ReleaseInfo
import tv.jetflix.app.update.UpdateNoticeState
import tv.jetflix.app.update.UpdateUi

/**
 * The user on build 25: "the download button too, I'm having to select twice to download".
 * The Download row itself fires on a single OK; the second press he hit was the intentional
 * Check -> Download gate. When the startup notice already found the build, Settings now opens
 * straight on Download, so installing is one press.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UpdateDownloadTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val info = ReleaseInfo(26, GhAsset("jetflix-release.apk", "https://demo.test/jetflix-release.apk", 100), null)

    @After fun reset() { UpdateNoticeState.available.value = null }

    @Test fun downloadRowFiresOnASingleOk() {
        var clicks = 0
        rule.setContent {
            JetflixTheme {
                SettingsContent(
                    true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {},
                    update = UpdateUi.Available(info), onUpdateClick = { clicks++ },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Download").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals("one OK must fire exactly one action", 1, clicks)
    }

    @Test fun settingsOpensOnDownloadWhenTheStartupNoticeFoundTheBuild() {
        UpdateNoticeState.available.value = info
        rule.setContent { JetflixTheme { SettingsScreen("demo", "http://demo.test", {}, {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Download").assertIsDisplayed()
        rule.onNodeWithText("Build 26 is available. Press OK to download it, verify it, and hand it to Android's installer.").assertIsDisplayed()
    }
}
