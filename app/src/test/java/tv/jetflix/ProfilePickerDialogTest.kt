package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.ProfilePickerDialog
import tv.jetflix.app.ui.theme.JetflixTheme

/** Real D-pad key events through the real profile-picker dialog (Robolectric, no TV involved). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ProfilePickerDialogTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun setPicker(
        picked: MutableList<String> = mutableListOf(),
        signOuts: MutableList<Int> = mutableListOf(),
        dismisses: MutableList<Int> = mutableListOf(),
        connecting: Boolean = false,
        error: String? = null,
    ) {
        rule.setContent {
            JetflixTheme {
                ProfilePickerDialog(
                    profiles = listOf("Alex", "Sam"),
                    currentName = "Sam",
                    connecting = connecting,
                    error = error,
                    onSelect = { picked += it },
                    onSignOut = { signOuts += 1 },
                    onDismiss = { dismisses += 1 },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test fun listsSavedProfilesAndMarksTheSignedInOne() {
        setPicker()
        rule.onNodeWithText("Who's watching?").assertIsDisplayed()
        rule.onNodeWithText("Alex").assertIsDisplayed()
        rule.onNodeWithText("Sam (signed in)").assertIsDisplayed()
        rule.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test fun opensWithTheSignedInProfileFocusedAndDpadWalksTheList() {
        setPicker()
        // Initial focus lands on the signed-in profile, so an accidental OK just closes the picker.
        rule.onNodeWithText("Sam (signed in)").assertIsFocused()
        rule.onNodeWithText("Sam (signed in)").performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        rule.onNodeWithText("Sign out").assertIsFocused()
        rule.onNodeWithText("Sign out").performKeyInput { pressKey(Key.DirectionUp) }; rule.waitForIdle()
        rule.onNodeWithText("Sam (signed in)").assertIsFocused()
        rule.onNodeWithText("Sam (signed in)").performKeyInput { pressKey(Key.DirectionUp) }; rule.waitForIdle()
        rule.onNodeWithText("Alex").assertIsFocused()
    }

    @Test fun okOnAnotherProfilePicksIt() {
        val picked = mutableListOf<String>()
        setPicker(picked = picked)
        rule.onNodeWithText("Alex").requestFocus(); rule.waitForIdle()
        rule.onNodeWithText("Alex").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(listOf("Alex"), picked)
    }

    @Test fun okOnTheSignedInProfileReportsItSoTheCallerCanJustClose() {
        val picked = mutableListOf<String>()
        setPicker(picked = picked)
        rule.onNodeWithText("Sam (signed in)").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(listOf("Sam"), picked)
    }

    @Test fun signOutButtonFiresSignOut() {
        val signOuts = mutableListOf<Int>()
        setPicker(signOuts = signOuts)
        rule.onNodeWithText("Sign out").requestFocus(); rule.waitForIdle()
        rule.onNodeWithText("Sign out").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(1, signOuts.size)
    }

    @Test fun switchingAndErrorStatesAreShown() {
        setPicker(connecting = true, error = "The saved sign-in for Alex expired.")
        rule.onNodeWithText("Switching...").assertIsDisplayed()
        rule.onNodeWithText("The saved sign-in for Alex expired.").assertIsDisplayed()
    }
}
