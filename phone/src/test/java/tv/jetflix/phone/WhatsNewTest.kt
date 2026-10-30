package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.update.WhatsNew
import tv.jetflix.phone.update.WhatsNewDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WhatsNewTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsOnceForANewerBuild() {
        assertTrue(WhatsNew.shouldShow(31, 0))
        assertTrue(WhatsNew.shouldShow(32, 31))
        assertFalse(WhatsNew.shouldShow(32, 32))
        assertFalse(WhatsNew.shouldShow(31, 32))
    }

    @Test fun parseKeepsBulletsAndDropsHeadings() {
        assertEquals(listOf("One", "Two"), WhatsNew.parse(listOf("## Build 5", "", "- One", "Fixes:", "* Two")))
    }

    @Test fun cardListsNotesAndOkDismisses() {
        var dismissed = false
        rule.setContent { DriftfinTheme { WhatsNewDialog(listOf("First change", "Second change")) { dismissed = true } } }
        rule.onNodeWithText("What's new").assertIsDisplayed()
        rule.onNodeWithText("\u2022  First change").assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()
        assertTrue(dismissed)
    }
}
