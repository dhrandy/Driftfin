package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.collectAsState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.PosterSize
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Walks the Settings rail to the Appearance pane and presses OK on Poster size. JVM test, not a device test. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsPosterSizeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dpadReachesPosterSizeAndOkChangesIt() {
        AppSettings.setPosterSize(PosterSize.Medium)
        rule.setContent {
            JetflixTheme {
                val size = AppSettings.posterSize.collectAsState().value
                SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {},
                    posterSize = size, onCyclePosterSize = { AppSettings.setPosterSize(AppSettings.posterSize.value.next()) })
            }
        }
        rule.waitForIdle()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
        fun t(x: androidx.compose.ui.semantics.SemanticsNode): String? =
            x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text ?: x.children.firstNotNullOfOrNull { t(it) }
        fun focusedText(): String = rule.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { t(it) } ?: "none"
        // Opening focus is Check for updates; Poster size heads the Appearance pane via the rail.
        val seen = mutableListOf<String>()
        key(Key.DirectionLeft); seen += focusedText()
        repeat(3) { key(Key.DirectionDown); seen += focusedText() }
        key(Key.DirectionRight); seen += focusedText()
        println("SETTINGS_FOCUS_SEQ $seen")
        assertTrue("Poster size row reachable through the rail, seq=$seen", focusedText().startsWith("Poster size"))
        println("FOCUS_ON " + focusedText())
        key(Key.Enter)
        println("POSTER_AFTER " + AppSettings.posterSize.value)
        assertTrue("OK on Poster size changes it", AppSettings.posterSize.value != PosterSize.Medium)
        rule.onNode(hasText("Large")).assertExists()
        AppSettings.setPosterSize(PosterSize.Medium)
    }
}
