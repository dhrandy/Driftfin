package tv.jetflix.phone

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.update.GhAsset
import tv.jetflix.phone.update.ReleaseInfo
import tv.jetflix.phone.update.SettingsTabIcon
import tv.jetflix.phone.update.UpdateNoticeState

/**
 * The update-available indicator: a red dot on the Settings tab icon while the quiet checker knows a
 * newer phone build, and Settings opening straight on that build's Download button (TV parity).
 * The checker itself never downloads or installs; installing stays a manual choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneUpdateDotTest {
    @get:Rule val compose = createComposeRule()

    private val build12 = ReleaseInfo(
        build = 12,
        apk = GhAsset(
            name = "driftfin-phone-release.apk",
            browser_download_url = "https://github.com/dhrandy/Driftfin/releases/download/phone-build-12/driftfin-phone-release.apk",
            size = 123,
        ),
        sha = null,
    )

    @After fun reset() { UpdateNoticeState.available.value = null }

    @Test fun dotHiddenWhenNoUpdateKnown() {
        UpdateNoticeState.available.value = null
        compose.setContent { DriftfinTheme { SettingsTabIcon(Icons.Filled.Settings, "Settings") } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("update-dot")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun dotShownWhileUpdateAvailable() {
        UpdateNoticeState.available.value = build12
        compose.setContent { DriftfinTheme { SettingsTabIcon(Icons.Filled.Settings, "Settings") } }
        compose.waitForIdle()
        compose.onNodeWithTag("update-dot").assertIsDisplayed()
    }

    @Test fun dotClearsWhenCheckerSaysUpToDate() {
        UpdateNoticeState.available.value = build12
        compose.setContent { DriftfinTheme { SettingsTabIcon(Icons.Filled.Settings, "Settings") } }
        compose.waitForIdle()
        compose.onNodeWithTag("update-dot").assertIsDisplayed()
        UpdateNoticeState.available.value = null
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("update-dot")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun settingsOpensStraightOnTheAvailableBuild() {
        UpdateNoticeState.available.value = build12
        compose.setContent { DriftfinTheme { tv.jetflix.phone.ui.SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Build 12 is available.").assertIsDisplayed()
        compose.onNodeWithText("Download and install").assertIsDisplayed()
    }
}
