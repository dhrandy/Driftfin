package tv.jetflix

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.player.KeyCaptureBox
import tv.jetflix.app.ui.player.PlayerKeys

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlayerKeysTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun previousPlayerLayoutNeverReceivedKeys() {
        // The shipped layout: a focus requester and a key handler, but no focusable node.
        val got = mutableListOf<Int>()
        val fr = FocusRequester()
        rule.setContent {
            Box(Modifier.fillMaxSize().focusRequester(fr).onKeyEvent { got += it.nativeKeyEvent.keyCode; true })
            androidx.compose.runtime.LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) { } }
        }
        rule.waitForIdle()
        // Robolectric windows report no focus, so a first D-pad press hands out initial focus (as on a TV).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        got.clear()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertTrue("old layout should have dropped the key (this documents the bug)", got.isEmpty())
    }

    @Test fun keyCaptureBoxReceivesSelectPlayPauseAndDpad() {
        val got = mutableListOf<Int>()
        rule.setContent { KeyCaptureBox(modifier = Modifier.fillMaxSize(), onKey = { if (it.action == KeyEvent.ACTION_DOWN) got += it.keyCode; true }) { } }
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.isFocusable()).requestFocus()
        rule.waitForIdle()
        got.clear()
        rule.onRoot().performKeyInput {
            pressKey(Key.DirectionCenter); pressKey(Key.MediaPlayPause); pressKey(Key.DirectionDown); pressKey(Key.DirectionLeft)
        }
        rule.waitForIdle()
        assertEquals(
            listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT),
            got,
        )
    }

    private fun readyPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        val source: MediaSource = FakeMediaSource(FakeTimeline(1))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return player
    }

    @Test fun toggleReallyPausesAndResumesExoPlayerState() {
        val player = readyPlayer()
        assertTrue(player.playWhenReady)
        PlayerKeys.togglePlayback(player)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse("paused", player.playWhenReady)
        val pausedAt = player.currentPosition
        PlayerKeys.togglePlayback(player)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("playing again", player.playWhenReady)
        assertTrue(pausedAt >= 0 && player.playbackState != Player.STATE_IDLE)
        player.release()
    }

    @Test fun toggleWhileStillBufferingPausesInsteadOfDoingNothing() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(MediaItem.fromUri("http://demo.test/none.mp4"))
        player.playWhenReady = true // not prepared: isPlaying is false, playWhenReady true
        assertFalse(player.isPlaying)
        PlayerKeys.togglePlayback(player)
        assertFalse("OK during buffering must pause", player.playWhenReady)
        player.release()
    }
}
