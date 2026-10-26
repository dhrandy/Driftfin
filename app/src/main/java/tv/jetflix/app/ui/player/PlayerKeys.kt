package tv.jetflix.app.ui.player

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * Full-screen box that really owns D-pad focus. A key handler only receives events when it sits
 * above a focusable node that holds focus, so the box is focusable and requests focus itself
 * (retrying until the window lets it).
 */
@Composable
fun KeyCaptureBox(
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val requester = remember { FocusRequester() }
    Box(
        modifier
            .onKeyEvent { onKey(it.nativeKeyEvent) }
            .focusRequester(requester)
            .focusable(),
    ) { content() }
    LaunchedEffect(Unit) {
        repeat(20) {
            try { requester.requestFocus(); return@LaunchedEffect } catch (_: Exception) { delay(50) }
        }
    }
}

object PlayerKeys {
    /** What a Down press does during playback. */
    enum class DownAction { RevealControls, OpenPanel, Ignore }

    /**
     * First Down with the controls hidden only reveals them (progress bar, title). Down again, as a fresh press,
     * opens the audio/subtitle panel. Auto-repeat events of a held key never act, so the press that revealed the
     * controls cannot also move on to the panel.
     */
    fun downAction(controlsVisible: Boolean, repeatCount: Int): DownAction = when {
        !controlsVisible -> DownAction.RevealControls
        repeatCount > 0 -> DownAction.Ignore
        else -> DownAction.OpenPanel
    }

    enum class CenterAction { SkipIntro, TogglePlayback, Ignore }

    /** OK while the Skip Intro prompt is showing skips; otherwise it pauses or plays. A held key never repeats the action. */
    fun centerAction(skipIntroVisible: Boolean, repeatCount: Int): CenterAction = when {
        repeatCount > 0 -> CenterAction.Ignore
        skipIntroVisible -> CenterAction.SkipIntro
        else -> CenterAction.TogglePlayback
    }

    /** Pause when the player is meant to be playing (even if still buffering); otherwise play, restarting if ended. */
    fun togglePlayback(player: Player) {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }
}
