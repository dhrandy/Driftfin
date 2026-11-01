package tv.jetflix.app.ui.player

import androidx.media3.common.Player

/** Immediate pause/play state sync on player events, so the pause UI (gray frame, cast strip,
 *  screensaver, Back handling) reacts to the pause event itself instead of waiting for the 1s
 *  progress poll to notice. The predicate is the poll's own, unchanged: paused only when
 *  playWhenReady is off and the player is READY - not while buffering, not after the stream ended. */
object PlaybackStateSync {
    fun pausedNow(player: Player): Boolean =
        !player.playWhenReady && player.playbackState == Player.STATE_READY

    /** Calls [onSync] immediately on every play/pause/state transition with the same values the
     *  1s progress poll computes. The poll stays as the reconciliation path; this removes the wait. */
    fun listener(player: Player, onSync: (isPlaying: Boolean, keepAwake: Boolean, paused: Boolean) -> Unit): Player.Listener {
        fun sync() = onSync(player.isPlaying, shouldKeepScreenOn(player.playWhenReady, player.playbackState), pausedNow(player))
        return object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { sync() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { sync() }
            override fun onPlaybackStateChanged(playbackState: Int) { sync() }
        }
    }
}
