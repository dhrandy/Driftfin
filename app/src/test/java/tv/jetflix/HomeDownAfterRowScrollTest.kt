package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Regression for the reported focus trap: scroll right along the first row (Continue Watching), go down a row and
 * right again, go back up to the banner, then Down did nothing until Home was reopened. The banner's Down target was
 * the first card of the first row, which is not composed once that row has scrolled away from its start.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeDownAfterRowScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("regression-items.json")!!.readText(),
        ).Items

    private fun focusedText(): String {
        val nodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        return nodes.joinToString(" || ") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun long(prefix: String, n: Int) = (0 until n).map { i -> items[i % items.size].copy(Id = prefix + i) }

    private fun setHome() {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        val cont = long("c", 40).map { it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }
        val state = HomeUiState(
            loading = false,
            hero = items.first { it.Name == "The Gold Rush" },
            sections = listOf(
                HomeSection("Continue Watching", cont, wide = true),
                HomeSection("Next Up", long("n", 40), wide = true),
                HomeSection("Popular Picks", long("p", 40), wide = false),
            ),
        )
        rule.setContent { JetflixTheme { HomeContent(state, JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", { }, { }, { }, { }, heroIntervalMs = 1000) } }
        rule.waitForIdle()
        if (zone() == "none") press(Key.DirectionDown)
    }

    private val navLabels = setOf("Home", "Shows", "Movies", "My Driftfin", "Search", "Settings", "demo")
    private val heroLabels = setOf("Play", "Resume", "Play from start", "More Info")
    private fun zone(): String {
        val t = focusedText()
        return when {
            t.isEmpty() -> "none"
            t in navLabels -> "nav"
            t in heroLabels || t.startsWith("Play") || t.startsWith("Resume") || t.startsWith("More") -> "hero"
            else -> "row"
        }
    }

    @Test fun downFromBannerStillWorksAfterScrollingTheFirstRowRight() {
        setHome()
        assertEquals("hero", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
        repeat(15) { press(Key.DirectionRight) }
        press(Key.DirectionDown); assertEquals("row", zone())
        repeat(8) { press(Key.DirectionRight) }
        press(Key.DirectionUp); press(Key.DirectionUp); assertEquals("hero", zone())
        press(Key.DirectionDown)
        assertEquals("Down from the banner must enter the rows again", "row", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
    }

    @Test fun downFromNavBarAlsoReachesRowsAfterFirstRowScrolled() {
        setHome()
        press(Key.DirectionDown)
        repeat(15) { press(Key.DirectionRight) }
        press(Key.DirectionUp); press(Key.DirectionUp); assertEquals("nav", zone())
        press(Key.DirectionDown); assertEquals("hero", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
    }
}
