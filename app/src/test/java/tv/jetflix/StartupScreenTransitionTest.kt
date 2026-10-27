package tv.jetflix

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.InitialHomeLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class StartupScreenTransitionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun loadingEndsWithoutAnyMinimumDurationAndDoesNotReturnOnRefresh() {
        val loading = mutableStateOf(true)
        rule.setContent {
            InitialHomeLoad(loading.value, false) { Text("Home or error") }
        }
        rule.onNodeWithContentDescription("Driftfin is loading").assertExists()
        rule.runOnIdle { loading.value = false }
        rule.onNodeWithContentDescription("Driftfin is loading").assertDoesNotExist()
        rule.onNodeWithText("Home or error").assertExists()
        rule.runOnIdle { loading.value = true }
        rule.onNodeWithContentDescription("Driftfin is loading").assertDoesNotExist()
        rule.onNodeWithText("Home or error").assertExists()
    }

    @Test fun disabledWallShowsOrdinaryHomeImmediately() {
        val loading = mutableStateOf(true)
        rule.setContent {
            InitialHomeLoad(loading.value, false, enabled = false) { Text("Ordinary Home") }
        }
        rule.onAllNodesWithContentDescription("Driftfin is loading").assertCountEquals(0)
        rule.onNodeWithText("Ordinary Home").assertIsDisplayed()
        rule.runOnIdle { loading.value = false }
        rule.onNodeWithText("Ordinary Home").assertIsDisplayed()
    }

    @Test fun retainedContentNeverGetsCoveredDuringRefresh() {
        rule.setContent { InitialHomeLoad(true, true) { Text("Existing Home") } }
        rule.onNodeWithContentDescription("Driftfin is loading").assertDoesNotExist()
        rule.onNodeWithText("Existing Home").assertExists()
    }

    @Test fun emptyOrFailedInitialLoadDoesNotGetCovered() {
        rule.setContent { InitialHomeLoad(false, false) { Text("Empty or error") } }
        rule.onNodeWithContentDescription("Driftfin is loading").assertDoesNotExist()
        rule.onNodeWithText("Empty or error").assertExists()
    }
}
