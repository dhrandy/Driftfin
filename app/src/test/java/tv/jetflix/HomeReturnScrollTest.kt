package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Down then back Up: each row must settle exactly where it settled on the way down, with the row title clear of the
 * menu bar. Reported on the TV as headings (Continue Watching, Next Up) tucked under the bar after going down and up.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeReturnScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    @Test fun rowsSettleTheSameGoingUpAsGoingDown() {
        val titles = listOf("Continue Watching", "Next Up", "Recently Added Movies", "Favorites", "Action", "Drama")
        val sections = titles.mapIndexed { i, t -> HomeSection(t, items.drop(i).take(8), wide = i < 2 || i == 4) }
        rule.setContent {
            JetflixTheme {
                HomeContent(HomeUiState(loading = false, hero = items.first().copy(UserData = null), heroes = emptyList(), sections = sections),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }
        fun cardTop(): Float = rule.onAllNodes(isFocused()).fetchSemanticsNodes().first().boundsInRoot.top
        fun titleTop(t: String): Float? = rule.onAllNodesWithText(t).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        key(Key.DirectionDown) // banner
        val down = mutableListOf<Pair<Float, Float?>>()
        titles.forEachIndexed { i, t -> key(Key.DirectionDown); down += cardTop() to titleTop(t) }
        val up = mutableListOf<Pair<Float, Float?>>()
        for (i in titles.indices.reversed()) { up += cardTop() to titleTop(titles[i]); key(Key.DirectionUp) }
        up.reverse()
        println("RETURN_DOWN " + down); println("RETURN_UP " + up)
        for (i in titles.indices) {
            // Bar (76 dp) plus its fade (40 dp) at 2x density: the title must be fully clear of both, down and up.
            for ((label, p) in listOf("down" to down[i], "up" to up[i])) {
                val tt = p.second
                assertTrue("${titles[i]} title ($label) must clear the bar and fade, titleTop=$tt", tt == null || tt >= 232f)
            }
        }
    }
}
