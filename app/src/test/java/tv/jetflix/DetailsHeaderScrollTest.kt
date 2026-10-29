package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Series view: focusing season chips/episodes scrolls the list and leaves only the header's bottom band on screen.
 * The backdrop is held against that scroll. Backdrop here is solid magenta so any bleed outside the header is visible.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class DetailsHeaderScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val series = base.first { it.isSeries }
    private val eps = (0 until 10).map { i ->
        base.first { it.isMovie }.copy(Id = "ep$i", Type = "Episode", SeriesName = series.Name, SeriesId = series.Id,
            ParentIndexNumber = 1, IndexNumber = i + 1, Name = "Ep$i", Overview = "Episode summary text.")
    }
    private lateinit var ls: LazyListState

    private fun show() {
        val red = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFFFF00FF.toInt()) }
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            ls = androidx.compose.foundation.lazy.rememberLazyListState()
            CompositionLocalProvider(LocalTestArtwork provides { url -> if (url?.contains("Backdrop") == true) BitmapPainter(red.asImageBitmap()) else null }) {
                JetflixTheme {
                    DetailsContent(
                        DetailsUiState(loading = false, item = series, playTarget = eps[0], episodes = eps,
                            seasons = listOf(tv.jetflix.app.data.BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season"), tv.jetflix.app.data.BaseItemDto(Id = "s2", Name = "Season 2", Type = "Season")),
                            selectedSeasonId = "s1"),
                        repo, {}, { _, _ -> }, {}, {}, {}, listState = ls,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shot(): Bitmap {
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b)); return b
    }
    private fun isRed(p: Int): Boolean { val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p); return r > 25 && b > 25 && g * 2 < r && g * 2 < b }
    private fun redRows(b: Bitmap): List<Int> = (0 until b.height step 4).filter { y -> (0 until b.width step 40).any { x -> isRed(b.getPixel(x, y)) } }

    /** Bottom edge (screen px) of the season-button row, where the art must finish. Null when it is scrolled off. */
    private fun seasonsBottom(): Int? = ls.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "seasons" }?.let { it.offset + it.size }

    private fun scrollTo(i: Int, off: Int) { rule.runOnUiThread { runBlocking { ls.scrollToItem(i, off) } }; rule.waitForIdle() }

    @Test fun zeroScrollFillsHeaderOnly() {
        show()
        val rows = redRows(shot())
        println("ZERO red rows ${rows.first()}..${rows.last()}")
        assertTrue("art must cover the header from the top", rows.first() <= 4)
        val end = seasonsBottom()!!
        assertTrue("art must reach below the 920px header, behind the description (was ${rows.last()})", rows.last() > 920)
        assertTrue("art ends at the season buttons' bottom ($end), was ${rows.last()}", rows.last() <= end + 4)
    }

    @Test fun focusDrivenScrollKeepsArtInsideVisibleHeaderBand() {
        show()
        // Real D-pad: Down from Play walks through the season chips into the episode rows; the list follows focus.
        repeat(8) {
            rule.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
            rule.waitForIdle()
        }
        val off = ls.firstVisibleItemScrollOffset; val idx = ls.firstVisibleItemIndex
        println("FOCUS scroll idx=$idx off=$off")
        assertTrue("focusing a lower episode must scroll the list", idx > 0 || off > 0)
        val b = shot(); val rows = redRows(b)
        if (idx == 0) {
            val end = seasonsBottom() ?: 1080
            assertTrue("art must not bleed below the season buttons ($end), last red row ${rows.lastOrNull()}", (rows.lastOrNull() ?: 0) <= end + 4)
            assertTrue("art fills the visible band from the top", rows.first() <= 4)
        } else assertTrue("header is gone, so no art", rows.isEmpty())
    }

    @Test fun partialScrollNoBleedBelowHeader() {
        show(); scrollTo(0, 700)
        val rows = redRows(shot())
        println("PARTIAL red rows ${rows.first()}..${rows.last()}")
        assertTrue("art starts at the top of the screen", rows.first() <= 4)
        val end = seasonsBottom()!!
        assertTrue("art ends at the season buttons' bottom ($end), was ${rows.last()}", rows.last() <= end + 4)
        assertTrue("art is carried past the header's visible bottom (220px), was ${rows.last()}", rows.last() > 224)
    }

    @Test fun headerGoneNoArtThenReturnUp() {
        show(); scrollTo(3, 0)
        assertTrue("header scrolled away: no art anywhere", redRows(shot()).isEmpty())
        scrollTo(0, 0)
        val rows = redRows(shot())
        assertTrue("back at top: art at the top, ending at the season buttons", rows.first() <= 4 && rows.last() <= seasonsBottom()!! + 4)
        assertEquals(0, ls.firstVisibleItemScrollOffset)
    }

    @Test fun openingStateKeepsArtAtTopWhateverTheInitialScroll() {
        show()
        rule.waitForIdle()
        val idx = ls.firstVisibleItemIndex; val off = ls.firstVisibleItemScrollOffset
        println("OPEN scroll idx=$idx off=$off")
        val rows = redRows(shot())
        if (idx == 0) {
            assertTrue("art at top of screen when opened", rows.first() <= 4)
            assertTrue("no bleed below the season buttons", rows.last() <= (seasonsBottom() ?: 1080) + 4)
        } else assertTrue(rows.isEmpty())
    }
}
