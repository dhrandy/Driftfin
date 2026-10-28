package tv.jetflix

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import tv.jetflix.app.data.MediaSegmentDto
import tv.jetflix.app.ui.player.PlayerKeys

/** First OK press against a real ExoPlayer, with synthetic segments. */
@RunWith(RobolectricTestRunner::class)
class SkipIntroPressTest {
    private fun seg(startMs: Long, endMs: Long) = MediaSegmentDto("s", "i", "Intro", startMs * 10_000, endMs * 10_000)
    private val intro = listOf(seg(2_000, 8_000))

    private fun player(): ExoPlayer {
        val p = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        p.setMediaSource(FakeMediaSource(FakeTimeline(1)) as MediaSource)
        p.prepare(); p.playWhenReady = true
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return p
    }

    @Test fun firstPressInsideIntroSkipsEvenWithNoPriorPoll() {
        val p = player(); p.seekTo(3_000); shadowOf(android.os.Looper.getMainLooper()).idle()
        val a = PlayerKeys.pressOk(p, intro, true, false, 0)
        assertEquals(PlayerKeys.CenterAction.SkipIntro, a)
        assertEquals(8_000L, p.currentPosition)
        assertTrue("skip must not pause", p.playWhenReady)
        p.release()
    }
    @Test fun pressOutsideIntroStillTogglesPlayback() {
        val p = player()
        assertEquals(PlayerKeys.CenterAction.TogglePlayback, PlayerKeys.pressOk(p, intro, true, false, 0))
        assertFalse(p.playWhenReady); p.release()
    }
    @Test fun segmentsNotLoadedYetBehavesAsPlayPause() {
        val p = player(); p.seekTo(3_000); shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(PlayerKeys.CenterAction.TogglePlayback, PlayerKeys.pressOk(p, emptyList(), true, false, 0))
        p.release()
    }
    @Test fun settingOffOrCoveredDoesNotSkip() {
        val p = player(); p.seekTo(3_000); shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(PlayerKeys.CenterAction.TogglePlayback, PlayerKeys.pressOk(p, intro, false, false, 0))
        assertEquals(PlayerKeys.CenterAction.TogglePlayback, PlayerKeys.pressOk(p, intro, true, true, 0))
        p.release()
    }
    @Test fun heldKeyIsIgnored() {
        val p = player(); p.seekTo(3_000); shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(PlayerKeys.CenterAction.Ignore, PlayerKeys.pressOk(p, intro, true, false, 2))
        assertEquals(3_000L, p.currentPosition); p.release()
    }
    @Test fun justBeforeIntroStartDoesNotSkip() {
        val p = player(); p.seekTo(1_900); shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(PlayerKeys.CenterAction.TogglePlayback, PlayerKeys.pressOk(p, intro, true, false, 0))
        p.release()
    }
}
