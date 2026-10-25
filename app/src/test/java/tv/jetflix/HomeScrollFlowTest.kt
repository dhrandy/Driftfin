package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
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
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

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
        val tops = mutableListOf<Float>(); val labels = mutableListOf<String>()
        repeat(9) {
            key(Key.DirectionDown)
            val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
            if (n.isNotEmpty()) { tops += n.first().boundsInRoot.top; labels += n.first().let { x -> (x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } ?: "") + "@" + x.boundsInRoot.left } }
        }
        println("FOCUSED_TOPS " + tops + " " + labels)
        assertTrue("focus reached rows, tops=$tops", tops.size >= 5)
        // 124 dp at 2x density = 248 px: bar plus the row title.
        val rowTops = tops.drop(2)
        assertTrue("cards below bar+title, tops=$tops", rowTops.all { it >= 240f })
    }

    @Test fun gridSmall() = gridCheck(tv.jetflix.app.data.PosterSize.Small)
    @Test fun gridMedium() = gridCheck(tv.jetflix.app.data.PosterSize.Medium)
    @Test fun gridLarge() = gridCheck(tv.jetflix.app.data.PosterSize.Large)

    private fun gridCheck(size: tv.jetflix.app.data.PosterSize) {
        run {
            tv.jetflix.app.data.AppSettings.setPosterSize(size)
            val many = (0 until 5).flatMap { r -> items.map { it.copy(Id = it.Id + "r$r") } }
            val tops = mutableListOf<Float>(); val seq = mutableListOf<String>()
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
                    tops += n.boundsInRoot.top
                    fun desc(x: androidx.compose.ui.semantics.SemanticsNode): String? =
                        x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.firstOrNull()
                            ?: x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text
                            ?: x.children.firstNotNullOfOrNull { desc(it) }
                    seq += (desc(n) ?: "?") + "@" + n.boundsInRoot.left.toInt()
                }
            }
            val firstTitleVisible = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Jungle Book"), useUnmergedTree = true).fetchSemanticsNodes().size
            println("FIRST_TITLE_NODES " + size + " " + firstTitleVisible)
            println("GRID_SEQ " + size + " " + seq)
            assertTrue("$size: focus moved, tops=$tops", tops.size >= 8)
            assertTrue("$size: below the 76 dp bar (152 px), tops=$tops", tops.drop(2).all { it >= 150f })
            tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Medium)
        }
    }
}
