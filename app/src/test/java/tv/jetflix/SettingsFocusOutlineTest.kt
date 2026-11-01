package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** The white focus outline lost its top edge on the first row of a
 *  Settings pane (Sound > Button sound) and its bottom edge on the rail's last row (Account); the
 *  rail's first row (Updates) clipped the same way. Both containers are scrolling columns whose
 *  viewport edges sat flush with the first/last rows, clipping the outline drawn past the row bounds.
 *  Now: scroll content carries vertical room at the extremes, and a focused row revealed flush at an
 *  edge mid-scroll is nudged off it. These checks scan actual pixels on all four sides. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsFocusOutlineTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun open() {
        rule.setContent { JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
    }

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
    }

    private fun focusedLabel(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
        return n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }?.takeIf { it.isNotEmpty() }
            ?: n.children.joinToString("|") { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    }

    private fun downUntilStuck(max: Int = 12) {
        var last = ""
        repeat(max) {
            val cur = focusedLabel()
            if (cur == last) return
            last = cur
            press(Key.DirectionDown)
        }
    }

    /** One render per check: drawing the window is the expensive part, reading pixels is cheap. */
    private fun screenBitmap(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    /** Render for CI inspection, written BEFORE the assertions so it exists even when a check fails. */
    private fun writeQaCapture(name: String, bmp: Bitmap) {
        val out = java.io.File("build/outputs/qa/$name").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The focus outline is drawn just past the row's bounds. For each side, scan a strip crossing
     *  that edge and require a white pixel in the strip's MIDDLE third: the vertical strokes near the
     *  corners must not satisfy a missing top/bottom edge (exactly the clipped cases reported). */
    private fun assertFullOutline(tag: String, bmp: Bitmap) {
        val node = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
        assertTrue("$tag: expected a focused row", node != null)
        val b = node!!.boundsInRoot
        val w = b.right - b.left; val h = b.bottom - b.top
        val outY = (h * 0.15f).toInt() + 8
        val outX = (w * 0.06f).toInt() + 8
        fun white(x: Int, y: Int) = android.graphics.Color.red(bmp.getPixel(x, y)) > 200
        fun brightest(pts: List<Pair<Int, Int>>) = pts.maxOf { android.graphics.Color.red(bmp.getPixel(it.first, it.second)) }
        val midX = (b.left + w / 3).toInt()..(b.right - w / 3).toInt() step 2
        val midY = (b.top + h / 3).toInt()..(b.bottom - h / 3).toInt() step 2
        // Scan bands are clamped to the bitmap: a band past the window edge is a geometry fact to
        // report, not a crash. Each edge reports WHERE its brightest pixel sits, so a failure tells
        // a missing ring from a misplaced band.
        fun scan(pts: List<Pair<Int, Int>>): Pair<Int, Pair<Int, Int>?> {
            val inBounds = pts.filter { it.first in 0 until bmp.width && it.second in 0 until bmp.height }
            if (inBounds.isEmpty()) return -1 to null
            val bestPt = inBounds.maxByOrNull { android.graphics.Color.red(bmp.getPixel(it.first, it.second)) }!!
            return android.graphics.Color.red(bmp.getPixel(bestPt.first, bestPt.second)) to bestPt
        }
        val (top, topAt) = scan(midX.flatMap { x -> ((b.top - outY).toInt()..(b.top + 2).toInt()).map { x to it } })
        val (bottom, bottomAt) = scan(midX.flatMap { x -> ((b.bottom - 2).toInt()..(b.bottom + outY).toInt()).map { x to it } })
        val (left, leftAt) = scan(midY.flatMap { y -> ((b.left - outX).toInt()..(b.left + 2).toInt()).map { it to y } })
        val (right, rightAt) = scan(midY.flatMap { y -> ((b.right - 2).toInt()..(b.right + outX).toInt()).map { it to y } })
        val where = "row=$b bmp=${bmp.width}x${bmp.height}"
        assertTrue("$tag: top edge of the focus outline must be visible (brightest r=$top at $topAt, $where)", top > 200)
        assertTrue("$tag: bottom edge of the focus outline must be visible (brightest r=$bottom at $bottomAt, $where)", bottom > 200)
        assertTrue("$tag: left edge of the focus outline must be visible (brightest r=$left at $leftAt, $where)", left > 200)
        assertTrue("$tag: right edge of the focus outline must be visible (brightest r=$right at $rightAt, $where)", right > 200)
    }

    private fun checkPaneFirstRow(capture: String) {
        open()
        assertTrue("opening focus on the pane's first row, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
        val bmp = screenBitmap()
        writeQaCapture(capture, bmp)
        assertFullOutline("Pane first row (Check for updates)", bmp)
    }

    private fun checkRailFirstRow(capture: String) {
        open()
        press(Key.DirectionLeft)
        assertTrue("Left reaches the rail's first group, was: ${focusedLabel()}", focusedLabel().contains("Updates"))
        val bmp = screenBitmap()
        writeQaCapture(capture, bmp)
        assertFullOutline("Rail first row (Updates)", bmp)
    }

    private fun checkRailLastRow(capture: String) {
        open()
        press(Key.DirectionLeft)
        downUntilStuck()
        assertTrue("rail's last group is Account, was: ${focusedLabel()}", focusedLabel().contains("Account"))
        val bmp = screenBitmap()
        writeQaCapture(capture, bmp)
        assertFullOutline("Rail last row (Account)", bmp)
    }

    private fun enterPlaybackPane() {
        open()
        press(Key.DirectionLeft)   // rail: Updates
        press(Key.DirectionDown)   // rail: Playback
        press(Key.DirectionRight)  // pane: first Playback row
        assertTrue("entered the Playback pane, was: ${focusedLabel()}", focusedLabel().contains("Autoplay next episode"))
    }

    @Test fun paneFirstRowOutlineFullyVisible() = checkPaneFirstRow("settings-pane-first-row.png")
    @Test
    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    fun paneFirstRowOutlineFullyVisibleMdpi1080() = checkPaneFirstRow("settings-pane-first-row-mdpi.png")
    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", fontScale = 1.5f)
    fun paneFirstRowOutlineFullyVisibleLargeText() = checkPaneFirstRow("settings-pane-first-row-largetext.png")

    @Test fun railFirstRowOutlineFullyVisible() = checkRailFirstRow("settings-rail-first.png")
    @Test
    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    fun railFirstRowOutlineFullyVisibleMdpi1080() = checkRailFirstRow("settings-rail-first-mdpi.png")
    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", fontScale = 1.5f)
    fun railFirstRowOutlineFullyVisibleLargeText() = checkRailFirstRow("settings-rail-first-largetext.png")

    @Test fun railLastRowOutlineFullyVisible() = checkRailLastRow("settings-rail-last.png")
    @Test
    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    fun railLastRowOutlineFullyVisibleMdpi1080() = checkRailLastRow("settings-rail-last-mdpi.png")

    /** The pane's LAST row must keep its full outline after the scroll reveal, not just at rest.
     *  Playback has more rows than the viewport at this height, so reaching it scrolls. */
    @Test fun paneLastRowOutlineFullyVisibleAfterScroll() {
        enterPlaybackPane()
        downUntilStuck()
        val last = focusedLabel()
        assertTrue("reached a Playback pane row, was: $last", last.isNotEmpty())
        val bmp = screenBitmap()
        writeQaCapture("settings-pane-last-row-scrolled.png", bmp)
        assertFullOutline("Pane last row after scroll ($last)", bmp)
    }

    /** Every stop while moving DOWN the scrollable Playback pane: a reveal that leaves the focused
     *  row flush with a viewport edge must not clip the outline (mid-scroll, not just extremes). */
    @Test fun paneRowsKeepFullOutlineWhileScrolling() {
        enterPlaybackPane()
        var mid: Bitmap? = null
        repeat(8) { i ->
            val bmp = screenBitmap()
            if (i == 3) mid = bmp
            assertFullOutline("Playback row after Down #$i (${focusedLabel()})", bmp)
            press(Key.DirectionDown)
        }
        writeQaCapture("settings-pane-mid-scroll.png", mid ?: screenBitmap())
    }

    /** The Update notifications toggle controls the red dot only; the description must not promise a
     *  startup popup. */
    @Test fun updateNotificationsDescribesTheRedDotOnly() {
        open()
        rule.onNodeWithText("Turns the red dot by your name on or off.", substring = true).assertExists()
        rule.onAllNodesWithText("startup popup", substring = true).assertCountEquals(0)
    }
}
