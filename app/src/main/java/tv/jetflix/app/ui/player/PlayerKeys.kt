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
    /** False while another screen (the actor page) is drawn over this one: it then takes no focus and no keys. */
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val requester = remember { FocusRequester() }
    Box(
        modifier
            .then(if (enabled) Modifier.onKeyEvent { onKey(it.nativeKeyEvent) }.focusRequester(requester).focusable() else Modifier),
    ) { content() }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(20) {
            try { requester.requestFocus(); return@LaunchedEffect } catch (_: Exception) { delay(50) }
        }
    }
}

object PlayerKeys {
    /** What a Down press does during playback. */
    enum class DownAction { RevealControls, OpenPanel, OpenCast, Ignore }

    /**
     * First Down with the controls hidden only reveals them (progress bar, title). Down again, as a fresh press,
     * opens the audio/subtitle panel. Auto-repeat events of a held key never act, so the press that revealed the
     * controls cannot also move on to the panel.
     */
    fun downAction(controlsVisible: Boolean, repeatCount: Int, castReady: Boolean = false): DownAction = when {
        !controlsVisible -> DownAction.RevealControls
        repeatCount > 0 -> DownAction.Ignore
        castReady -> DownAction.OpenCast
        else -> DownAction.OpenPanel
    }

    /** The cast strip can be entered only while paused, with the controls up, the setting on and cast known. */
    fun castReady(paused: Boolean, controlsVisible: Boolean, settingOn: Boolean, castCount: Int) =
        paused && controlsVisible && settingOn && castCount > 0

    sealed class CastStep {
        data class Move(val index: Int) : CastStep()
        data object Open : CastStep()
        data object Exit : CastStep()
        data object Consume : CastStep()
    }

    /** Keys while the cast strip has focus: Left/Right move, OK targets the actor, Up/Back leave. Everything else is swallowed. */
    fun castStep(keyCode: Int, index: Int, count: Int): CastStep = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> CastStep.Move((index - 1).coerceAtLeast(0))
        KeyEvent.KEYCODE_DPAD_RIGHT -> CastStep.Move((index + 1).coerceAtMost((count - 1).coerceAtLeast(0)))
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CastStep.Open
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK -> CastStep.Exit
        else -> CastStep.Consume
    }

    /**
     * One OK press on a cast person opens exactly one page. The press only arms the open; the
     * release performs it and is swallowed. Navigating on the press instead let the same release
     * land on the actor page, where a focused tv-material card fires its click on ANY Enter
     * release - even one whose press happened here - so the actor's first movie opened on its own,
     * and a held OK re-opened the actor on every repeat. Back/Up leave on the press as before, but
     * their release is swallowed too, so closing the strip never also backs out of the player.
     */
    class CastKeySession {
        var openArmed = false
            private set
        var exitArmedKey: Int? = null
            private set

        /** A key press while the strip has focus. Open only arms; the real open happens in [onRelease]. */
        fun onPress(keyCode: Int, index: Int, count: Int): CastStep = when (val step = castStep(keyCode, index, count)) {
            CastStep.Open -> { openArmed = true; CastStep.Consume }
            CastStep.Exit -> { exitArmedKey = keyCode; step }
            else -> step
        }

        /** A key release after the strip handled its press: fires an armed Open, swallows a strip Exit's release. Null = not ours, let it through. */
        fun onRelease(keyCode: Int): CastStep? = when {
            openArmed && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) -> { openArmed = false; CastStep.Open }
            exitArmedKey == keyCode -> { exitArmedKey = null; CastStep.Consume }
            else -> null
        }

        /** The strip closed without a press (playback resumed): drop anything left armed. */
        fun reset() { openArmed = false; exitArmedKey = null }
    }

    enum class CenterAction { SkipIntro, TogglePlayback, Ignore }

    /** OK while the Skip Intro prompt is showing skips; otherwise it pauses or plays. A held key never repeats the action. */
    fun centerAction(skipIntroVisible: Boolean, repeatCount: Int): CenterAction = when {
        repeatCount > 0 -> CenterAction.Ignore
        skipIntroVisible -> CenterAction.SkipIntro
        else -> CenterAction.TogglePlayback
    }

    /**
     * What OK does right now, decided from the player's position at the press: skip an active intro, pause/play, or nothing
     * for a held key. Performs the action and returns it so the caller can react (show controls, remember the new position).
     */
    fun pressOk(
        player: Player,
        segments: List<tv.jetflix.app.data.MediaSegmentDto>,
        skipEnabled: Boolean,
        covered: Boolean,
        repeatCount: Int,
    ): CenterAction {
        val segment = tv.jetflix.app.data.SkipIntro.available(skipEnabled, covered, segments, player.currentPosition)
        val action = centerAction(segment != null, repeatCount)
        when (action) {
            CenterAction.SkipIntro -> segment?.let { player.seekTo(tv.jetflix.app.data.SkipIntro.endMs(it)) }
            CenterAction.TogglePlayback -> togglePlayback(player)
            CenterAction.Ignore -> Unit
        }
        return action
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

/** Whether the player is on screen and who owns the remote: decided only from the top two screens of the back stack. */
object PlayerHost {
    enum class Layer { Absent, Interactive, CoveredByActor }

    /** The player is drawn for itself, or kept (paused, not rebuilt) under an actor page opened from it. Anything else drops it. */
    fun layer(top: String, below: String?): Layer = when {
        top == "Player" -> Layer.Interactive
        top == "Actor" && below == "Player" -> Layer.CoveredByActor
        else -> Layer.Absent
    }
}
