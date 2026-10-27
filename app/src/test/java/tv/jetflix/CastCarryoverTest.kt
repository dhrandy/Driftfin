package tv.jetflix

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.player.PlayerKeys
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The cast-strip OK bug: while paused, OK on a person opened the actor page on the key PRESS, so the
 * RELEASE of that same press landed on the actor page and clicked its first focused movie (tv-material
 * fires a focused card's click on any Enter release). One press opened two pages, and a held OK
 * re-opened the actor on every auto-repeat. These tests pin the library behaviour and the fix:
 * the strip now arms on the press and opens on the release, which it swallows.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalTvMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class CastCarryoverTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun navigatingOnThePressIsWhatLetTheReleaseClickThrough() {
        // Reproduces the old cast-strip flow: OK press navigates straight to the actor page...
        var movieOpened = 0
        var showActor by mutableStateOf(false)
        rule.setContent {
            JetflixTheme {
                if (!showActor) {
                    val fr = remember { FocusRequester() }
                    Box(
                        Modifier.size(300.dp).focusRequester(fr).focusable().onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { showActor = true; true } else false
                        },
                    ) { Text("strip") }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                } else {
                    val fr = remember { FocusRequester() }
                    androidx.tv.material3.Surface(onClick = { movieOpened++ }, modifier = Modifier.focusRequester(fr)) { Text("movie") }
                    LaunchedEffect(Unit) { repeat(10) { try { fr.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        // ...and its release clicked the actor page's first movie without a second press: the bug.
        assertTrue(showActor)
        assertEquals(1, movieOpened)
    }

    @Test fun theDetailsCastChipPathNeverCarriedARelease() {
        // The Details-page cast chip is a plain tv-material Surface: its click fires on the release
        // and consumes it, so that press cannot reach the actor page it opens.
        var movieOpened = 0
        var showActor by mutableStateOf(false)
        rule.setContent {
            JetflixTheme {
                if (!showActor) {
                    val fr = remember { FocusRequester() }
                    androidx.tv.material3.Surface(onClick = { showActor = true }, modifier = Modifier.focusRequester(fr)) { Text("chip") }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                } else {
                    val fr = remember { FocusRequester() }
                    androidx.tv.material3.Surface(onClick = { movieOpened++ }, modifier = Modifier.focusRequester(fr)) { Text("movie") }
                    LaunchedEffect(Unit) { repeat(10) { try { fr.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertTrue("the chip's press opens the actor page", showActor)
        assertEquals("the same press cannot click anything on the new page", 0, movieOpened)
        // A deliberate second press still opens the movie.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, movieOpened)
    }

    @Test fun theCastKeySessionOpensOnTheReleaseAndTheReleaseReachesNothingElse() {
        var movieOpened = 0
        var showActor by mutableStateOf(false)
        val keys = PlayerKeys.CastKeySession()
        rule.setContent {
            JetflixTheme {
                if (!showActor) {
                    val fr = remember { FocusRequester() }
                    Box(
                        Modifier.size(300.dp).focusRequester(fr).focusable().onKeyEvent { ev ->
                            val n = ev.nativeKeyEvent
                            when {
                                n.action == KeyEvent.ACTION_UP -> when (keys.onRelease(n.keyCode)) {
                                    PlayerKeys.CastStep.Open -> { showActor = true; true }
                                    PlayerKeys.CastStep.Consume -> true
                                    else -> false
                                }
                                n.action == KeyEvent.ACTION_DOWN -> { keys.onPress(n.keyCode, 0, 4); true }
                                else -> false
                            }
                        },
                    ) { Text("strip") }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                } else {
                    val fr = remember { FocusRequester() }
                    androidx.tv.material3.Surface(onClick = { movieOpened++ }, modifier = Modifier.focusRequester(fr)) { Text("movie") }
                    LaunchedEffect(Unit) { repeat(10) { try { fr.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.waitForIdle()
        assertFalse("the press alone must not navigate", showActor)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        assertTrue("the release opens the actor page", showActor)
        assertEquals("the same release must not click anything on the new page", 0, movieOpened)
        // A deliberate second press still opens the movie.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, movieOpened)
    }
}
