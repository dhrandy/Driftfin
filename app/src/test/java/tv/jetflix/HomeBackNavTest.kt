package tv.jetflix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.NavSweep
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Randy on build 21: "on the home screen, if you click the back button, it should go to the top and you
 * be able to click right, just like how it works on movies and shows". Movies/Shows land Back on the top
 * menu; Home used to land it on the hero Play button, where Right only walks the hero buttons.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeBackNavTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    private fun focusedText(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(900); rule.waitForIdle() // let the scroll-to-top settle
    }

    @Test fun backFromTheRowsLandsOnTheHomeTabAndRightStaysInTheNav() {
        val titles = listOf("Continue Watching", "Next Up", "Recently Added Movies")
        val sections = titles.mapIndexed { i, t -> HomeSection(t, items.drop(i).take(8), wide = i < 2) }
        rule.setContent {
            JetflixTheme {
                HomeContent(HomeUiState(loading = false, hero = items.first().copy(UserData = null), heroes = emptyList(), sections = sections),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        press(Key.DirectionDown) // into the first row
        press(Key.DirectionDown) // deeper: second row
        back()
        assertEquals("Back from the rows must land on the Home tab in the top bar, was '${focusedText()}'", "Home", focusedText())
        press(Key.DirectionRight)
        assertEquals("Right from the Home tab stays in the top nav (Shows), was '${focusedText()}'", "Shows", focusedText())
    }

    @Test fun backToHomeFromAnotherSectionLandsOnTheHomeTabAndStays() {
        // Randy: "when it goes to home screen, the button defaults to the play instead staying on home".
        // MainActivity's Back-to-Home handler raises the sweep handoff for HOME; Home must put focus on the
        // Home tab and a late-loading hero must not steal it down to Play.
        NavSweep.active = true; NavSweep.tab = JetflixTab.HOME
        val titles = listOf("Continue Watching", "Next Up")
        val sections = titles.mapIndexed { i, t -> HomeSection(t, items.drop(i).take(8), wide = i < 2) }
        var heroState by androidx.compose.runtime.mutableStateOf<tv.jetflix.app.data.BaseItemDto?>(null)
        rule.setContent {
            JetflixTheme {
                HomeContent(HomeUiState(loading = false, hero = heroState, heroes = emptyList(), sections = sections),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        assertEquals("Back-to-Home lands on the Home tab, was '${focusedText()}'", "Home", focusedText())
        // The hero arrives AFTER the handoff cleared the live sweep flag: it must not pull focus down.
        rule.runOnIdle { heroState = items.first().copy(UserData = null) }
        rule.mainClock.advanceTimeBy(3000); rule.waitForIdle()
        assertEquals("a late hero must not steal focus from the tab, was '${focusedText()}'", "Home", focusedText())
        press(Key.DirectionRight)
        assertEquals("Right from the Home tab stays in the top nav (Shows), was '${focusedText()}'", "Shows", focusedText())
        NavSweep.active = false; NavSweep.tab = null
    }
}
