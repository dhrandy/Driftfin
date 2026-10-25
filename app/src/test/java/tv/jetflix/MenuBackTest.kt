package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibrarySort
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Sort row on the Shows/Movies grid, driven by real D-pad keys (Robolectric, no TV involved). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class MenuBackTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun focused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString("||") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun settingsBackGoesToTheTopMenu() {
        rule.setContent { JetflixTheme { tv.jetflix.app.ui.settings.SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}) } }
        rule.waitForIdle()
        press(Key.DirectionDown); press(Key.DirectionDown)
        assertTrue("in the page body, was ${focused()}", focused() !in setOf("Settings", "Home"))
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("Back lands on the selected tab", "Settings", focused())
    }
}
