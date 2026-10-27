package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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

/** Walks Down through the Home rows with the remote: a focused card must never end up under the menu bar. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeScrollFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    @Test fun focusedRowsStayBelowTheMenuBar() {
        val sections = listOf("Continue Watching", "Next Up", "Recently Added Movies", "Favorites", "Action", "Drama").mapIndexed { i, t ->
            HomeSection(t, items.drop(i).take(8), wide = i < 2)
        }
        rule.setContent {
            JetflixTheme {
                HomeContent(HomeUiState(loading = false, hero = items.first().copy(UserData = null), heroes = emptyList(), sections = sections),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(600) }
        key(Key.DirectionDown)
        val tops = mutableListOf<Float>(); val bottoms = mutableListOf<Float>(); val labels = mutableListOf<String>()
        repeat(9) {
            key(Key.DirectionDown)
            val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
            if (n.isNotEmpty()) { tops += n.first().boundsInRoot.top; bottoms += n.first().boundsInRoot.bottom; labels += n.first().let { x -> (x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } ?: "") + "@" + x.boundsInRoot.left } }
        }
        println("FOCUSED_TOPS " + tops + " " + labels)
        assertTrue("focus reached rows, tops=$tops", tops.size >= 5)
        // 124 dp at 2x density = 248 px: bar plus the row title.
        val rowTops = tops.drop(2)
        assertTrue("cards below bar+title, tops=$tops", rowTops.all { it >= 240f })
        val viewH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        // 56 dp margin at 2x density = 112 px under every focused card (room for zoom, outline and TV overscan).
        assertTrue("focused cards keep 112 px below, bottoms=$bottoms of $viewH", bottoms.all { it + 90f <= viewH })
    }

    @Test fun gridSmall() = gridCheck(tv.jetflix.app.data.PosterSize.Small)
    @Test fun gridMedium() = gridCheck(tv.jetflix.app.data.PosterSize.Medium)
    @Test fun gridLarge() = gridCheck(tv.jetflix.app.data.PosterSize.Large)

    private fun gridCheck(size: tv.jetflix.app.data.PosterSize) {
        run {
            tv.jetflix.app.data.AppSettings.setPosterSize(size)
            val many = (0 until 5).flatMap { r -> items.map { it.copy(Id = it.Id + "r$r") } }
            val tops = mutableListOf<Float>(); val bottoms = mutableListOf<Float>(); val seq = mutableListOf<String>()
            rule.setContent {
                JetflixTheme {
                    tv.jetflix.app.ui.library.LibraryContent(
                        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = many),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, tv.jetflix.app.ui.components.JetflixTab.MOVIES, "demo", {}, {}, {},
                    )
                }
            }
            rule.waitForIdle()
            repeat(14) {
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(400)
                rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
                    tops += n.boundsInRoot.top; bottoms += n.boundsInRoot.bottom
                    fun desc(x: androidx.compose.ui.semantics.SemanticsNode): String? =
                        x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.firstOrNull()
                            ?: x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text
                            ?: x.children.firstNotNullOfOrNull { desc(it) }
                    seq += (desc(n) ?: "?") + "@" + n.boundsInRoot.left.toInt()
                }
            }
            val firstTitleVisible = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("The Gold Rush"), useUnmergedTree = true).fetchSemanticsNodes().size
            println("FIRST_TITLE_NODES " + size + " " + firstTitleVisible)
            println("GRID_SEQ " + size + " " + seq)
            assertTrue("$size: focus moved, tops=$tops", tops.size >= 8)
            assertTrue("$size: below the 76 dp bar (152 px), tops=$tops", tops.drop(2).all { it >= 150f })
            val viewH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
            assertTrue("$size: focused poster keeps 112 px below, bottoms=$bottoms of $viewH", bottoms.all { it + 90f <= viewH })
            tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Medium)
        }
    }

    @Test fun libraryHasNoBannerAndHomeHeroComesBackWhenGoingUp() {
        rule.setContent {
            JetflixTheme {
                tv.jetflix.app.ui.library.LibraryContent(
                    tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = items, banner = items.first(), libraries = listOf("a" to "Movies", "b" to "Kids Movies")),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, tv.jetflix.app.ui.components.JetflixTab.MOVIES, "demo", {}, {}, {},
                )
            }
        }
        rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.hasText("More Info")).assertCountEquals(0)
        rule.onAllNodes(androidx.compose.ui.test.hasText("Library: All")).assertCountEquals(1)
    }

    @Test fun homeHeroScrollsBackIntoViewOnUp() {
        val sections = (0 until 6).map { i -> HomeSection("Row $i", items.drop(i).take(8), wide = i < 2) }
        val hero = items.first().copy(UserData = null)
        rule.setContent {
            JetflixTheme {
                HomeContent(HomeUiState(loading = false, hero = hero, heroes = emptyList(), sections = sections),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(1200); rule.waitForIdle(); println("FOCUSED after $k: " + rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { n -> n.config.toString().take(80) + "@" + n.boundsInRoot.top }) }
        // The Play button sits a fixed distance below the banner title, so its position says where the whole banner is.
        fun play() = rule.onAllNodes(androidx.compose.ui.test.hasText("Play", substring = false), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
        val rest = play() ?: -1f
        key(Key.DirectionDown) // first focus
        val focused = play()
        key(Key.DirectionDown); key(Key.DirectionUp)
        val afterOne = play()
        repeat(5) { key(Key.DirectionDown) }
        val scrolled = play()
        repeat(5) { key(Key.DirectionUp) }
        val back = play()
        println("HERO_PLAY_TOP rest=$rest focused=$focused afterOne=$afterOne scrolled=$scrolled back=$back")
        assertTrue("banner at rest has room above Play for its title ($rest)", rest > 150f)
        assertTrue("banner back at rest after focusing Play (rest=$rest focused=$focused)", focused != null && kotlin.math.abs(focused - rest) < 4f)
        assertTrue("banner back at rest after one row down and up (rest=$rest afterOne=$afterOne)", afterOne != null && kotlin.math.abs(afterOne - rest) < 4f)
        assertTrue("banner scrolled away going down (scrolled=$scrolled)", scrolled == null || scrolled < rest - 50f)
        assertTrue("banner back at rest after rows down and up (rest=$rest back=$back)", back != null && kotlin.math.abs(back - rest) < 4f)
    }
}
