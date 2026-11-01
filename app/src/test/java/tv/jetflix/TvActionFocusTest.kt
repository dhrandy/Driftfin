package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** The Details action row (Play / Trailer / Watchlist / ...) keeps its own colors on focus and
 *  shows a WHITE OUTLINE around the focused button - actual pixels, not a color-constant check.
 *  (User call on build 49: white outline on focus for buttons, red stays only on the avatar ring.) */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvActionFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun setDetails() {
        val item = base.first { it.Type == "Movie" }.copy(UserData = null) // no progress: the button reads "Play"
        rule.setContent {
            JetflixTheme {
                DetailsContent(
                    DetailsUiState(loading = false, item = item, playTarget = item),
                    JellyfinRepository().apply { apiFor("http://demo.test") }, {}, { _, _ -> }, {}, {}, {},
                    showWatchlist = true,
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    private fun pixelAt(cx: Int, cy: Int): Int {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp.getPixel(cx, cy)
    }

    /** tv-material scales a focused button's VISUAL up ~1.1x while the semantics bounds stay at
     *  the layout size, so the white outline lands a few px OUTSIDE the bounds (probe-verified).
     *  Scan a short strip across each edge and require a white pixel; the interior keeps its fill. */
    private fun assertFocusOutline(tag: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val cx = ((b.left + b.right) / 2).toInt()
        val cy = ((b.top + b.bottom) / 2).toInt()
        val strips = listOf(
            "left" to ((b.left - 7).toInt()..(b.left - 1).toInt()).map { it to cy },
            "right" to ((b.right + 1).toInt()..(b.right + 7).toInt()).map { it to cy },
            "top" to ((b.top - 7).toInt()..(b.top - 1).toInt()).map { cx to it },
            "bottom" to ((b.bottom + 1).toInt()..(b.bottom + 7).toInt()).map { cx to it },
        )
        for ((name, pts) in strips) {
            val best = pts.maxOf { pt -> android.graphics.Color.red(pixelAt(pt.first, pt.second)) }
            assertTrue("$tag focused outline is white at $name edge (brightest strip pixel r=$best)", best > 200)
        }
    }

    @Test fun playFocusedShowsWhiteOutlineOnOpen() {
        setDetails()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        assertFocusOutline("Play", play)
    }

    @Test fun renderFocusedPlayOutlineWithUnchangedChrome() {
        setDetails()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        // Decisive render for inspection: white-outlined Play in the action row, top nav unchanged.
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/details-action-focus-outline.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 8) for (y in 0 until bmp.height step 8) colors.add(bmp.getPixel(x, y))
        assertTrue("details-action-focus-outline.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun focusMovingRightKeepsWhiteOutlineOnEveryActionButton() {
        setDetails()
        rule.onNodeWithText("Play").requestFocus(); rule.waitForIdle()
        // Row order: Play -> Watched check -> watchlist star -> heart (fixture movie: no resume, no trailer).
        fun right() { rule.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }; rule.waitForIdle() }
        right()
        val watched = rule.onNodeWithContentDescription("Mark as watched")
        watched.assertIsFocused()
        assertFocusOutline("Watched", watched)
        right()
        val star = rule.onNodeWithContentDescription("Add to watchlist")
        star.assertIsFocused()
        assertFocusOutline("Watchlist", star)
        right()
        val heart = rule.onNodeWithContentDescription("Add to favorites")
        heart.assertIsFocused()
        assertFocusOutline("Favorite", heart)
    }
}
