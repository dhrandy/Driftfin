package tv.jetflix

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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.player.PlayerKeys
import tv.jetflix.app.ui.player.PlaybackStateSync

/** The pause UI sync is event-driven: the listener reports the new state on the player event
 *  itself. Each measured phase flushes well under the old 1s poll period, so a state that only
 *  the poll would have set cannot be what the assertion observes. Reaching READY first is media
 *  loading, not the event path, and gets its own generous budget. (Wiring proof, not a device
 *  latency measurement.) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackStateSyncTest {

    private fun pipelinePlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        val source: MediaSource = FakeMediaSource(FakeTimeline(1))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return player
    }

    /** Flush the queue until [cond] or the virtual [budgetMs] runs out; returns the virtual ms used.
     *  The fake pipeline's media loading is timer-driven in Robolectric, so reaching READY needs a
     *  little virtual time; keeping the budget under the old 1s poll period is the point. */
    /** The fake pipeline loads on a real background thread: only real time plus draining the main
     *  looper moves it to READY. A virtual-time flush can burn its budget faster than the loader's
     *  real work runs on a shared CI host, so the setup waits in REAL time. The measured event
     *  phases below keep their virtual budgets - that is what the test actually times. */
    private fun awaitReady(player: ExoPlayer) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (player.playbackState != Player.STATE_READY && System.nanoTime() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("fake pipeline reaches READY", player.playbackState == Player.STATE_READY)
    }

    private fun flushUntil(budgetMs: Long, cond: () -> Boolean): Long {
        var used = 0L
        while (used < budgetMs) {
            if (cond()) return used
            shadowOf(android.os.Looper.getMainLooper()).idleFor(10, java.util.concurrent.TimeUnit.MILLISECONDS)
            used += 10
        }
        return used
    }

    private data class Sync(val isPlaying: Boolean, val keepAwake: Boolean, val paused: Boolean)

    @Test fun pauseEventReportsPausedWellUnderTheOldPollPeriod() {
        val player = pipelinePlayer()
        // Loading to READY is not what this test measures; it waits in real time so the
        // 900ms virtual phase below times only the pause event path.
        awaitReady(player)
        val syncs = mutableListOf<Sync>()
        val listener = PlaybackStateSync.listener(player) { p, a, paused -> syncs += Sync(p, a, paused) }
        player.addListener(listener)
        syncs.clear()
        PlayerKeys.togglePlayback(player)
        val used = flushUntil(900) { syncs.lastOrNull()?.paused == true }
        assertTrue("pause event must produce a sync", syncs.isNotEmpty())
        assertTrue("paused reported on the event path in " + used + "ms virtual, under the 1s poll", syncs.last().paused)
        assertFalse(syncs.last().isPlaying)
        player.release()
    }

    @Test fun resumeEventReportsUnpausedOnTheEventPath() {
        val player = pipelinePlayer()
        awaitReady(player)
        PlayerKeys.togglePlayback(player) // pause
        flushUntil(900) { !player.playWhenReady }
        val syncs = mutableListOf<Sync>()
        val listener = PlaybackStateSync.listener(player) { p, a, paused -> syncs += Sync(p, a, paused) }
        player.addListener(listener)
        PlayerKeys.togglePlayback(player) // resume
        flushUntil(900) { syncs.isNotEmpty() }
        assertTrue("resume event must produce a sync", syncs.isNotEmpty())
        assertFalse("resumed", syncs.last().paused)
        player.release()
    }

    @Test fun pauseWhileNotReadyIsNotReportedAsPaused() {
        // Same predicate as the 1s poll: paused needs playWhenReady off AND playbackState READY.
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(MediaItem.fromUri("http://demo.test/none.mp4"))
        player.playWhenReady = true
        val syncs = mutableListOf<Sync>()
        val listener = PlaybackStateSync.listener(player) { p, a, paused -> syncs += Sync(p, a, paused) }
        player.addListener(listener)
        PlayerKeys.togglePlayback(player) // pause during buffering
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(syncs.isNotEmpty())
        assertFalse("paused while buffering stays false (poll semantics preserved)", syncs.last().paused)
        player.release()
    }

    /** Skip-back/forward while playing must NEVER surface a pause: the seek dips isPlaying for a
     *  split second (buffering) but playWhenReady stays on, so the settled predicate the pause
     *  visuals key off must stay unpaused across the whole transient (user report, build 52:
     *  pause button + black-and-white flashed for a split second on skip-back). */
    @Test fun seekWhilePlayingNeverReportsPaused() {
        val player = pipelinePlayer()
        awaitReady(player)
        val syncs = mutableListOf<Sync>()
        val listener = PlaybackStateSync.listener(player) { p, a, paused -> syncs += Sync(p, a, paused) }
        player.addListener(listener)
        syncs.clear()
        player.seekTo(if (player.duration > 2_000) player.duration / 2 else 0L)
        // Sample across the whole seek transient, buffering included: no event-path sync may
        // report paused while playWhenReady stays on. If the fake pipeline fires no events for
        // the seek, the settle checks below still pin the predicate.
        val used = flushUntil(900) {
            if (syncs.isNotEmpty()) {
                assertFalse("paused reported during a seek while playing: $syncs", syncs.last().paused)
            }
            player.playbackState == Player.STATE_READY && player.isPlaying
        }
        assertTrue("seek settled back to playing within the virtual budget", used < 900)
        assertFalse("settled after the seek: still unpaused", PlaybackStateSync.pausedNow(player))
        player.release()
    }

    @Test fun removedListenerStopsSyncing() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(MediaItem.fromUri("http://demo.test/none.mp4"))
        player.playWhenReady = true
        val syncs = mutableListOf<Sync>()
        val listener = PlaybackStateSync.listener(player) { p, a, paused -> syncs += Sync(p, a, paused) }
        player.addListener(listener)
        player.removeListener(listener)
        syncs.clear()
        PlayerKeys.togglePlayback(player)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("no callbacks after removal (screen dispose path)", 0, syncs.size)
        player.release()
    }
}
