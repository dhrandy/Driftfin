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

/** The Details action row (Play / Trailer / Watchlist / ...) takes the season chip's RED focus
 *  background - actual pixels at the focused button's center, not a color-constant comparison. */
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

    /** Center hits the icon/text glyph; background shows at the horizontal insets. Both must be red. */
    private fun assertRedCenter(tag: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val cy = ((b.top + b.bottom) / 2).toInt()
        for (frac in listOf(0.10f, 0.90f)) {
            val px = pixelAt((b.left + (b.right - b.left) * frac).toInt(), cy)
            val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
            assertTrue("$tag focused background is red at ${(frac * 100).toInt()}% inset (was rgb($r,$g,$bl))", r > 150 && g < 90 && bl < 90)
        }
    }

    @Test fun playFocusedIsRedOnOpen() {
        setDetails()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        assertRedCenter("Play", play)
    }

    @Test fun renderFocusedPlayRedWithUnchangedChrome() {
        setDetails()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        // Decisive render for inspection: red-focused Play in the action row, top nav unchanged.
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/details-action-focus-red.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 8) for (y in 0 until bmp.height step 8) colors.add(bmp.getPixel(x, y))
        assertTrue("details-action-focus-red.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun focusMovingRightKeepsRedOnEveryActionButton() {
        setDetails()
        rule.onNodeWithText("Play").requestFocus(); rule.waitForIdle()
        // Row order: Play -> Watched check -> watchlist star -> heart (fixture movie: no resume, no trailer).
        fun right() { rule.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }; rule.waitForIdle() }
        right()
        val watched = rule.onNodeWithContentDescription("Mark as watched")
        watched.assertIsFocused()
        assertRedCenter("Watched", watched)
        right()
        val star = rule.onNodeWithContentDescription("Add to watchlist")
        star.assertIsFocused()
        assertRedCenter("Watchlist", star)
        right()
        val heart = rule.onNodeWithContentDescription("Add to favorites")
        heart.assertIsFocused()
        assertRedCenter("Favorite", heart)
    }
}
