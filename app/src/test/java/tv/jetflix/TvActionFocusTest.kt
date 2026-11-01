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
    private fun assertFocusOutline(tag: String, node: androidx.compose.ui.test.SemanticsNodeInteraction, whitePill: Boolean = false) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val cx = ((b.left + b.right) / 2).toInt()
        val cy = ((b.top + b.bottom) / 2).toInt()
        // tv-material scales the focused VISUAL ~1.1x past the semantics bounds, so the ring
        // lands ~5% of the button's own width/height OUTSIDE each edge: 18px out on a 360px-wide
        // hero button, far beyond any fixed band (Run10: hero Play left strip brightest r86 with
        // the ring plainly in the capture). Reach per edge from the actual bounds, inward +4.
        val outX = ((b.right - b.left) * 0.06f).toInt() + 4
        val outY = ((b.bottom - b.top) * 0.06f).toInt() + 4
        val strips = listOf(
            "left" to ((b.left - outX).toInt()..(b.left + 4).toInt()).map { it to cy },
            "right" to ((b.right - 4).toInt()..(b.right + outX).toInt()).map { it to cy },
            "top" to ((b.top - outY).toInt()..(b.top + 4).toInt()).map { cx to it },
            "bottom" to ((b.bottom - 4).toInt()..(b.bottom + outY).toInt()).map { cx to it },
        )
        for ((name, pts) in strips) {
            val best = pts.maxOf { pt -> android.graphics.Color.red(pixelAt(pt.first, pt.second)) }
            assertTrue("$tag focused outline is white at $name edge (brightest strip pixel r=$best)", best > 200)
        }
        if (whitePill) {
            // Primary actions (Play/Resume) KEEP the white pill while focused; the outline ring is
            // the focus cue. Glyphs can reach the 10%/90% column on the center row, so scan the
            // column inside the pill: white fill must show somewhere along it.
            for (frac in listOf(0.10f, 0.90f)) {
                val x = (b.left + (b.right - b.left) * frac).toInt()
                val y0 = (b.top + (b.bottom - b.top) * 0.12f).toInt()
                val y1 = (b.bottom - (b.bottom - b.top) * 0.12f).toInt()
                val best = (y0..y1 step 2).maxOf { y -> android.graphics.Color.red(pixelAt(x, y)) }
                assertTrue("$tag focused body keeps the white fill at ${(frac * 100).toInt()}% width (brightest column pixel r=$best)", best > 200)
            }
        } else {
            // Secondary actions keep the DARK fill, not a white block: sample the fill zone between
            // the outline and the glyphs on the center row.
            for (frac in listOf(0.10f, 0.90f)) {
                val px = pixelAt((b.left + (b.right - b.left) * frac).toInt(), cy)
                val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
                assertTrue("$tag focused body is dark at ${(frac * 100).toInt()}% width (was rgb($r,$g,$bl))", r < 120 && g < 120 && bl < 120)
            }
        }
    }

    @Test fun playFocusedShowsWhiteOutlineOnOpen() {
        setDetails()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        assertFocusOutline("Play", play, whitePill = true)
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
