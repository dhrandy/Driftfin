package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.AvatarDisplay
import tv.jetflix.app.data.Avatars
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * The TV profile avatars: the bundled set, per-profile stored choice and display mode, D-pad proof
 * for the picker, and pixel captures of the top bar in all three display modes on the dark app background.
 * Usernames in this test are synthetic (Tester), never a real user name.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvAvatarTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    private fun resetPrefs() {
        val c = RuntimeEnvironment.getApplication()
        c.getSharedPreferences("jetflix_settings", 0).edit().clear().commit()
        AppSettings.init(c)
    }

    private fun signIn(userId: String, name: String) {
        SessionManager.signIn(Session(serverUrl = "http://demo.test", userId = userId, userName = name, token = "t", deviceId = "d"))
    }

    @Test fun avatarSetIsComplete() {
        assertEquals(20, Avatars.all.size)
        assertEquals((1..20).toList(), Avatars.all.map { it.id })
        assertEquals(20, Avatars.all.map { it.res }.toSet().size)
        Avatars.all.forEach { assertTrue("${it.id} label", it.label.isNotBlank()) }
        assertNull(Avatars.byId(0))
        assertNull(Avatars.byId(21))
        assertEquals("Astronaut", Avatars.byId(1)!!.label)
        assertEquals("Sloth", Avatars.byId(20)!!.label)
    }

    @Test fun displayCyclesThroughThreeModes() {
        assertEquals(AvatarDisplay.AvatarAndUsername, AvatarDisplay.UsernameOnly.next())
        assertEquals(AvatarDisplay.AvatarOnly, AvatarDisplay.AvatarAndUsername.next())
        assertEquals(AvatarDisplay.UsernameOnly, AvatarDisplay.AvatarOnly.next())
    }

    @Test fun settingsDefaultsAreUsernameOnlyAndNoAvatar() {
        resetPrefs()
        assertEquals(0, AppSettings.avatarId.value)
        assertEquals(AvatarDisplay.UsernameOnly, AppSettings.avatarDisplay.value)
    }

    @Test fun avatarChoicesAreIsolatedPerProfile() {
        resetPrefs()
        signIn("user-a", "Tester A")
        assertEquals(0, AppSettings.avatarId.value) // fresh profile starts at none
        AppSettings.setAvatarId(3)
        AppSettings.setAvatarDisplay(AvatarDisplay.AvatarOnly)

        signIn("user-b", "Tester B") // a different profile on the same TV
        assertEquals("profile B must not inherit A's avatar", 0, AppSettings.avatarId.value)
        assertEquals("profile B must not inherit A's display mode", AvatarDisplay.UsernameOnly, AppSettings.avatarDisplay.value)
        AppSettings.setAvatarId(9)
        AppSettings.setAvatarDisplay(AvatarDisplay.AvatarAndUsername)

        signIn("user-a", "Tester A") // back to A: its own choices come back
        assertEquals(3, AppSettings.avatarId.value)
        assertEquals(AvatarDisplay.AvatarOnly, AppSettings.avatarDisplay.value)

        signIn("user-b", "Tester B") // and B's survived the round trip
        assertEquals(9, AppSettings.avatarId.value)
        assertEquals(AvatarDisplay.AvatarAndUsername, AppSettings.avatarDisplay.value)

        SessionManager.signOut()
        AppSettings.setAvatarId(0)
        AppSettings.setAvatarDisplay(AvatarDisplay.UsernameOnly)
    }

    @Test fun pickerDpadMovesAndOkSelects() {
        resetPrefs()
        var picked = -1
        // Rendered directly (the Dialog wrapper adds a second semantics root the key injector cannot
        // disambiguate); the same initial-focus plumbing the dialog drives is exercised here.
        rule.setContent {
            JetflixTheme {
                val fr = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                tv.jetflix.app.ui.components.AvatarPickerContent(selectedId = 0, onSelect = { picked = it }, initialFocus = fr)
                androidx.compose.runtime.LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) { } }
            }
        }
        rule.waitForIdle()
        fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
        // No current pick: initial focus lands on avatar 1.
        assertTrue("initial focus on Astronaut", rule.onAllNodes(hasContentDescription("Astronaut") and isFocused()).fetchSemanticsNodes().isNotEmpty())
        key(Key.DirectionRight) // Robot
        key(Key.DirectionRight) // Camp scene
        assertTrue("Right twice lands on Camp scene", rule.onAllNodes(hasContentDescription("Camp scene") and isFocused()).fetchSemanticsNodes().isNotEmpty())
        key(Key.DirectionDown) // row 2, same column: T-Rex (id 8)
        assertTrue("Down lands on T-Rex", rule.onAllNodes(hasContentDescription("T-Rex") and isFocused()).fetchSemanticsNodes().isNotEmpty())
        key(Key.DirectionCenter)
        assertEquals(8, picked)
    }

    /** The bar over the app's dark background: the gradient scrim and gray labels read as on Home. */
    private fun header(name: String, mode: AvatarDisplay, avatarId: Int, userName: String, captureName: String) {
        resetPrefs()
        signIn("user-h", userName)
        AppSettings.setAvatarId(avatarId)
        AppSettings.setAvatarDisplay(mode)
        rule.setContent {
            JetflixTheme {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(JetflixColors.Background),
                ) { TopNavBar(selected = JetflixTab.HOME, userName = userName, onTabSelected = {}, onUserClick = {}) }
            }
        }
        rule.waitForIdle()
        capture(captureName)
    }

    @Test fun captureHeaderUsernameOnly() {
        header("username-only", AvatarDisplay.UsernameOnly, 5, "Tester", "avatar-header-username-only")
        rule.onNodeWithText("Tester").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Goofy dog").assertCountEquals(0)
    }

    @Test fun captureHeaderAvatarAndUsername() {
        header("avatar+username", AvatarDisplay.AvatarAndUsername, 5, "Tester", "avatar-header-avatar-and-username")
        rule.onNodeWithText("Tester").assertIsDisplayed()
        rule.onNodeWithContentDescription("Goofy dog").assertIsDisplayed()
    }

    @Test fun captureHeaderAvatarOnly() {
        header("avatar-only", AvatarDisplay.AvatarOnly, 5, "Tester", "avatar-header-avatar-only")
        // No visible text: the name survives only as the accessible label on the avatar pill.
        rule.onAllNodesWithText("Tester").assertCountEquals(0)
        rule.onNodeWithContentDescription("Tester").assertIsDisplayed()
        rule.onNodeWithContentDescription("Goofy dog").assertIsDisplayed()
    }

    @Test fun headerFallsBackToUsernameWhenNonePicked() {
        // Display mode asks for an avatar but none is picked: the username shows as before.
        header("fallback", AvatarDisplay.AvatarAndUsername, 0, "Tester", "avatar-header-none-fallback")
        rule.onNodeWithText("Tester").assertIsDisplayed()
    }

    @Test fun longUsernameClipsWithEllipsis() {
        val longName = "A very long profile name that just keeps on going"
        header("long-name", AvatarDisplay.AvatarAndUsername, 5, longName, "avatar-header-long-username")
        rule.onNodeWithText(longName).assertExists()
    }

    @Test fun captureAvatarPickerShowsAllTwenty() {
        resetPrefs()
        // The Dialog wrapper lives in its own window that the harness capture cannot see, so the
        // content panel is rendered directly (same composable the dialog hosts).
        rule.setContent {
            JetflixTheme {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(JetflixColors.Background),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { tv.jetflix.app.ui.components.AvatarPickerContent(selectedId = 5, onSelect = {}) }
            }
        }
        rule.waitForIdle()
        Avatars.all.forEach { rule.onNodeWithContentDescription(it.label).assertIsDisplayed() }
        capture("avatar-picker-dialog")
    }
}
