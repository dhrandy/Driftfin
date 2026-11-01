package tv.jetflix

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Report: after typing the server and pressing Quick Connect the focus jumped back to the server field, and Quick
 * Connect had to be pressed again. Two focus losses are possible in LoginContent: the pressed button became disabled
 * while connecting, and it was then removed when the code panel appeared. These keep focus on the control the user is on.
 * Generic test server text only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class QuickConnectFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val state = mutableStateOf(LoginUiState(serverUrl = "http://jellyfin.example:8096"))

    private fun show(onQuick: () -> Unit = {}) = rule.setContent {
        JetflixTheme { LoginContent(state = state.value, onUpdate = { _, _, _ -> }, onConnect = {}, onQuickConnect = onQuick) }
    }

    @Test fun quickConnectKeepsFocusWhileConnecting() {
        show()
        rule.onNodeWithText("Quick Connect").requestFocus()
        rule.onNodeWithText("Quick Connect").assertIsFocused()
        state.value = state.value.copy(connecting = true)
        rule.waitForIdle()
        rule.onNodeWithText("Quick Connect").assertIsFocused()
    }

    @Test fun pressWhileConnectingDoesNotStartAnotherRequest() {
        var starts = 0
        show { starts++ }
        state.value = state.value.copy(connecting = true)
        rule.waitForIdle()
        rule.onNodeWithText("Quick Connect").requestFocus()
        rule.onNodeWithText("Quick Connect").performClickForTest()
        org.junit.Assert.assertEquals(0, starts)
    }

    @Test fun focusMovesToCancelWhenCodeAppearsAndBackWhenItLeaves() {
        show()
        rule.onNodeWithText("Quick Connect").requestFocus()
        state.value = state.value.copy(connecting = false, quickCode = "123456")
        rule.waitForIdle()
        rule.onNodeWithText("Cancel").assertIsFocused()
        state.value = state.value.copy(quickCode = null)
        rule.waitForIdle()
        rule.onNodeWithText("Quick Connect").assertIsFocused()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickForTest() =
    this.performClick()
