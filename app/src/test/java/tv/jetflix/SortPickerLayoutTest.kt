package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** The labels "Date Added",
 *  "Release Year", "Play Count" and the Ascending/Descending order buttons were clipped ("Date",
 *  "Release", "Ascendi", "Descen") and a large empty area sat under the two option rows. The grid was
 *  a fixed 4 columns at a fixed height with text that scales up while the cells stay fixed dp.
 *  These checks open the real dialog from the library screen and scan the actual rendered pixels. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SortPickerLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val items = base.take(12).mapIndexed { i, it -> it.copy(Id = "sp$i", Name = "Title $i") }
    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val density get() = rule.activity.resources.displayMetrics.density

    /** Opens the real Sort by dialog from the library screen and draws its window to a bitmap.
     *  The QA capture is written BEFORE any assertion so CI always has the render to inspect. */
    private fun openSortDialog(capture: String): Bitmap {
        rule.setContent {
            JetflixTheme { LibraryContent(LibraryUiState(loading = false, items = items), repo, JetflixTab.MOVIES, "demo", {}, {}, {}) }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        // Real TV interaction (the MyJetflixAndExitTest pattern): focus the chip and press Enter.
        // performClick uses touch injection, which fails on nodes outside the TV window geometry.
        rule.onNodeWithText("Sort: Name ↑").requestFocus(); rule.waitForIdle()
        rule.onNodeWithText("Sort: Name ↑").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog() ?: error("Sort by dialog did not open")
        val view = dialog.window!!.decorView
        assertTrue("sort dialog window laid out (${view.width}x${view.height})", view.width > 0 && view.height > 0)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/$capture").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun buttonBoundsInWindow(label: String) = rule.onNodeWithText(label).fetchSemanticsNode().boundsInWindow

    /** The label's text is white on the dark panel. Its rightmost white pixel must end well before
     *  the button's right edge (padding plus visible slack); a clipped label ends flush at the
     *  content edge, as in the report photo. */
    private fun assertLabelFullyRendered(label: String, bmp: Bitmap) {
        val b = buttonBoundsInWindow(label)
        val cy = ((b.top + b.bottom) / 2).toInt()
        var maxX = Int.MIN_VALUE
        for (x in b.left.toInt() until b.right.toInt() - 1) {
            val p = bmp.getPixel(x, cy)
            if (android.graphics.Color.red(p) > 200 && android.graphics.Color.green(p) > 200 && android.graphics.Color.blue(p) > 200) maxX = x
        }
        assertTrue("$label: expected white label pixels", maxX > b.left)
        val margin = b.right - maxX
        assertTrue("$label must be fully rendered, not clipped (right margin ${margin.toInt()}px)", margin >= 30 * density)
    }

    private fun checkAllLabels(capture: String) {
        val bmp = openSortDialog(capture)
        for (label in listOf("Name", "Date Added", "Release Year", "Rating", "Play Count", "Ascending", "Descending")) {
            rule.onNodeWithText(label).assertExists()
        }
        assertLabelFullyRendered("Descending", bmp)
        assertLabelFullyRendered("Ascending", bmp)
        assertLabelFullyRendered("Date Added", bmp)
        assertLabelFullyRendered("Release Year", bmp)
        assertLabelFullyRendered("Play Count", bmp)
    }

    @Test fun sortLabelsFullyRendered() = checkAllLabels("sort-picker.png")

    /** The report TV renders text much larger than the dp layout assumed (labels clipped on device).
     *  1.5x font scale reproduces the clip against the old fixed 4-column grid. */
    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", fontScale = 1.5f)
    fun sortLabelsFullyRenderedLargeText() = checkAllLabels("sort-picker-largetext.png")

    /** 1080p at mdpi (a 1920dp-wide UI surface) with large text: another real TV geometry. */
    @Test
    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi", fontScale = 1.5f)
    fun sortLabelsFullyRenderedMdpiLargeText() = checkAllLabels("sort-picker-mdpi-largetext.png")

    /** The dialog must fit its content: little panel space below the last option row (the report
     *  photo showed a large empty block). Panel colour 0xFF222222, scanned at the dialog centre. */
    @Test fun dialogFitsItsContent() {
        val bmp = openSortDialog("sort-picker-fit.png")
        val last = buttonBoundsInWindow("Descending")
        val cx = bmp.width / 2
        var panelBottom = -1
        for (y in bmp.height - 1 downTo 0) {
            val p = bmp.getPixel(cx, y)
            if (Math.abs(android.graphics.Color.red(p) - 0x22) < 10 &&
                Math.abs(android.graphics.Color.green(p) - 0x22) < 10 &&
                Math.abs(android.graphics.Color.blue(p) - 0x22) < 10) { panelBottom = y; break }
        }
        assertTrue("dialog panel found below the options", panelBottom > last.bottom)
        val gap = panelBottom - last.bottom
        assertTrue("little empty panel below the last row (gap ${gap.toInt()}px)", gap <= 48 * density)
    }
    private fun releaseYearRowOffsetPx(): Float =
        buttonBoundsInWindow("Release Year").top - buttonBoundsInWindow("Name").top

    /** The column choice is driven by TEXT SCALE, not density. At normal text scale the sort dialog
     *  uses 3 columns: Release Year shares the first row with Name. */
    @Test fun normalTextScaleUsesThreeColumns() {
        openSortDialog("sort-picker-cols3.png")
        assertTrue("Release Year shares the first row at normal scale (offset ${releaseYearRowOffsetPx()}px)",
            Math.abs(releaseYearRowOffsetPx()) < 4 * density)
    }

    /** A lower-density surface (mdpi, 1920dp wide) with normal text scale must keep 3 columns:
     *  density alone does not change the layout. */
    @Test
    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    fun mdpiAloneKeepsThreeColumns() {
        openSortDialog("sort-picker-cols3-mdpi.png")
        assertTrue("density alone must not change the column count (offset ${releaseYearRowOffsetPx()}px)",
            Math.abs(releaseYearRowOffsetPx()) < 4 * density)
    }

    /** At 1.5x text scale the dialog switches to 2 columns: Release Year moves to the second row. */
    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", fontScale = 1.5f)
    fun largeTextScaleUsesTwoColumns() {
        openSortDialog("sort-picker-cols2-largetext.png")
        assertTrue("Release Year drops to the second row at large text (offset ${releaseYearRowOffsetPx()}px)",
            releaseYearRowOffsetPx() > 20 * density)
    }

}

/** Long genre names must stay fully readable when the TV's text scale is up: the shared picker drops
 *  to wider (2) columns above 1.3x font scale instead of clipping labels to "Ascendi"/"Descen"-style
 *  fragments. Genre shares the picker. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", fontScale = 1.5f)
class GenrePickerLargeTextTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val items = base.take(12).mapIndexed { i, it -> it.copy(Id = "gp$i", Name = "Title $i") }
    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val density get() = rule.activity.resources.displayMetrics.density

    private fun openGenrePicker(): Bitmap {
        rule.setContent {
            JetflixTheme {
                LibraryContent(
                    LibraryUiState(loading = false, items = items,
                        genres = listOf("Action", "Documentary", "Science Fiction", "Western")),
                    repo, JetflixTab.MOVIES, "demo", {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        // Focus + Enter, not performClick (touch injection): see openSortDialog.
        rule.onNodeWithText("Filter").requestFocus(); rule.waitForIdle()
        rule.onNodeWithText("Filter").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        rule.onNodeWithText("Genre: All").requestFocus(); rule.waitForIdle()
        rule.onNodeWithText("Genre: All").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog() ?: error("Genre dialog did not open")
        val view = dialog.window!!.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/genre-picker-largetext.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    @Test fun longGenreLabelsFullyRendered() {
        val bmp = openGenrePicker()
        for (label in listOf("All", "Action", "Documentary", "Science Fiction", "Western")) {
            rule.onNodeWithText(label).assertExists()
        }
        for (label in listOf("Documentary", "Science Fiction")) {
            val b = rule.onNodeWithText(label).fetchSemanticsNode().boundsInWindow
            val cy = ((b.top + b.bottom) / 2).toInt()
            var maxX = Int.MIN_VALUE
            for (x in b.left.toInt() until b.right.toInt() - 1) {
                val px = bmp.getPixel(x, cy)
                if (android.graphics.Color.red(px) > 200 && android.graphics.Color.green(px) > 200 && android.graphics.Color.blue(px) > 200) maxX = x
            }
            assertTrue("$label: expected white label pixels", maxX > b.left)
            val margin = b.right - maxX
            assertTrue("$label must be fully rendered at 1.5x text scale, not clipped (right margin ${margin.toInt()}px)", margin >= 30 * density)
        }
    }
}
