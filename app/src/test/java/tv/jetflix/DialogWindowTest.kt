package tv.jetflix

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import tv.jetflix.app.ui.components.AvatarPickerDialog
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.WhatsNewDialog

/**
 * Build-48 user reports from the actual TV: the What's new card rendered about a quarter of the
 * screen wide (bullets wrapped to ~14 lines, OK label unreadable), and the avatar picker showed
 * only 16 of 20 avatars - the 5th column was clipped. Both dialogs ran inside the platform dialog
 * window, whose theme gives the window a narrow fixed width on TV. The isolated card/grid
 * previews never saw this because they get the full test canvas. These tests exercise the real
 * Dialog windows: the window must be sized to the display (MATCH_PARENT), not the theme default.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DialogWindowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun whatsNewDialogWindowFillsTheDisplayWidth() {
        rule.setContent { JetflixTheme { WhatsNewDialog(listOf("One", "Two"), onDismiss = {}) } }
        rule.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("WhatsNewDialog created a real dialog window", dialog)
        assertEquals(
            "window must size to the display, not the platform dialog theme width",
            WindowManager.LayoutParams.MATCH_PARENT,
            dialog.window!!.attributes.width,
        )
    }

    @androidx.compose.ui.test.ExperimentalTestApi
    @Test fun avatarPickerDialogWindowFillsTheDisplayWidth() {
        var picked = 0
        rule.setContent { JetflixTheme { AvatarPickerDialog(selectedId = 1, onSelect = { picked = it }, onDismiss = {}) } }
        rule.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("AvatarPickerDialog created a real dialog window", dialog)
        assertEquals(
            "window must size to the display so all 5 avatar columns fit",
            WindowManager.LayoutParams.MATCH_PARENT,
            dialog.window!!.attributes.width,
        )
        // All 20 avatars exist as real nodes inside the dialog window (build 48 clipped the 5th
        // column on TV: only 16 visible).
        assertEquals(
            "all 20 avatars present in the picker",
            20,
            tv.jetflix.app.data.Avatars.all.count { entry ->
                rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(entry.label), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            },
        )
        // Remote-walk proof: from the initial focus (Astronaut, id 1), DPAD Down x3 then
        // Right x4 must land on Sloth (id 20, last row's last column), fully inside the dialog
        // window, and OK must select it - not just "the node exists".
        fun key(k: androidx.compose.ui.input.key.Key) {
            rule.onAllNodes(androidx.compose.ui.test.isRoot())[1]
                .performKeyInput { pressKey(k) }
            rule.waitForIdle()
        }
        repeat(3) { key(androidx.compose.ui.input.key.Key.DirectionDown) }
        repeat(4) { key(androidx.compose.ui.input.key.Key.DirectionRight) }
        val sloth = rule.onAllNodes(
            androidx.compose.ui.test.hasContentDescription("Sloth") and androidx.compose.ui.test.isFocused(),
        ).fetchSemanticsNodes()
        org.junit.Assert.assertTrue("DPAD walk reaches a focused Sloth (id 20)", sloth.isNotEmpty())
        val dialogBounds = rule.onAllNodes(androidx.compose.ui.test.isRoot())[1].fetchSemanticsNode().boundsInRoot
        val slothBounds = sloth[0].boundsInRoot
        org.junit.Assert.assertTrue(
            "Sloth fully inside the dialog window: " + slothBounds + " in " + dialogBounds,
            slothBounds.left >= dialogBounds.left && slothBounds.right <= dialogBounds.right &&
                slothBounds.top >= dialogBounds.top && slothBounds.bottom <= dialogBounds.bottom,
        )
        key(androidx.compose.ui.input.key.Key.DirectionCenter)
        assertEquals(20, picked)
        // Full-window pixel proof for the record.
        val view = dialog.window!!.decorView
        val dm = rule.activity.resources.displayMetrics
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/avatar-picker-dialog-window.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE avatar-picker-dialog-window ${out.length()}B")
    }
}
