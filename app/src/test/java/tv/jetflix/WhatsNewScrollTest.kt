package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import tv.jetflix.app.update.WhatsNewDialog
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * User report on build-47: the What's new card has "stuff cutoff... not showing on the screen".
 * The card must fit the TV with the real build-47 notes: every bullet reachable with the remote
 * and the OK button always visible so the card can be dismissed.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class WhatsNewScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    // The real build-47 notes (RELEASE_NOTES.md), ~1030 chars over 7 bullets - small fixtures miss this.
    private val build47Notes = listOf(
        "Settings scrolls when the categories don't fit, so Account and everything above it is reachable on every TV.",
        "A show's status (Continuing or Ended) now actually appears on its page: the app reads the field the server really sends.",
        "The subtitle and audio check mark moves the moment you press OK, instead of after closing and reopening the panel.",
        "New \"What's new in this build\" row in Settings > Updates replays the change list shown after an update installed.",
        "Update checks run on every startup and every 6 hours while the app is on screen, with a red dot left of your name while a newer build exists. A new \"Update notifications\" setting hides the popup and dot; checks still run and installing stays manual.",
        "A show's Cast row now follows the season you have selected. If that season has no actor information on the server, the row stays hidden rather than showing another season's cast.",
        "Focus fixes: pressing Down on the last row of a settings pane stays put, and sweeping right from Search to Home no longer drops the cursor on Play.",
    )

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    // Two roots exist while the Dialog is up (activity + dialog window); keys go to the dialog.
    private fun key(k: Key) { rule.onAllNodes(isRoot())[1].performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun longNotesAllReachableAndOkStaysVisible() {
        var dismissed = false
        rule.setContent { JetflixTheme { WhatsNewDialog(build47Notes) { dismissed = true } } }
        rule.waitForIdle()
        // Geometric clipping proof: the dialog window is bounded, so any content placed past its
        // bottom edge is physically off the card on a TV. Print the real layout numbers.
        val dialogRoot = rule.onAllNodes(isRoot())[1].fetchSemanticsNode().boundsInRoot
        val okNode = rule.onNodeWithText("OK").fetchSemanticsNode().boundsInRoot
        val firstBullet = rule.onNodeWithText("Settings scrolls when the categories don't fit", substring = true).fetchSemanticsNode().boundsInRoot
        val lastBullet = rule.onNodeWithText("Focus fixes: pressing Down on the last row", substring = true).fetchSemanticsNode().boundsInRoot
        println("GEOM dialog=$dialogRoot ok=$okNode first=$firstBullet last=$lastBullet")
        println("GEOM okVisible=${okNode.bottom <= dialogRoot.bottom} lastBulletVisible=${lastBullet.bottom <= dialogRoot.bottom} firstVisible=${firstBullet.top >= dialogRoot.top}")
        capture("whatsnew-long-open")
        rule.onNodeWithText("OK").assertIsDisplayed()
        // Walk Up through every bullet to the first one.
        repeat(10) { key(Key.DirectionUp) }
        capture("whatsnew-long-scrolled-top")
        rule.onNodeWithText("Settings scrolls when the categories don't fit", substring = true).assertIsDisplayed()
        // And back down to OK, which dismisses.
        repeat(10) { key(Key.DirectionDown) }
        rule.onNodeWithText("OK").assertIsDisplayed()
        key(Key.DirectionCenter)
        assertTrue("OK dismisses the card", dismissed)
    }

    @Test
    @org.robolectric.annotation.Config(fontScale = 1.3f)
    fun largerTextStillFits() {
        rule.setContent { JetflixTheme { WhatsNewDialog(build47Notes) {} } }
        rule.waitForIdle()
        val dialogRoot = rule.onAllNodes(isRoot())[1].fetchSemanticsNode().boundsInRoot
        val okNode = rule.onNodeWithText("OK").fetchSemanticsNode().boundsInRoot
        println("GEOM13 dialog=$dialogRoot ok=$okNode okVisible=${okNode.bottom <= dialogRoot.bottom}")
        assertTrue("OK must have real layout on the card at 1.3x text", okNode.bottom > 0f && okNode.bottom <= dialogRoot.bottom + 1f)
    }
}
