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
        // Loading to READY is not what this test measures; give it a generous budget so the
        // 900ms phase below times only the pause event path, on any host speed.
        assertTrue("fake pipeline reaches READY", flushUntil(10_000) { player.playbackState == Player.STATE_READY } < 10_000)
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
        assertTrue("fake pipeline reaches READY", flushUntil(10_000) { player.playbackState == Player.STATE_READY } < 10_000)
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
