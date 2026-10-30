package tv.jetflix.phone

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackOrientationTest {
    @Test fun playbackIsLandscapeEitherWayAndBrowsingIsPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, PlaybackOrientation.PLAYING)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, PlaybackOrientation.BROWSING)
        assertNotEquals(PlaybackOrientation.PLAYING, PlaybackOrientation.BROWSING)
    }

    @Test fun trailersFollowThePhonesOwnRotation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, PlaybackOrientation.TRAILER)
        assertNotEquals(PlaybackOrientation.TRAILER, PlaybackOrientation.PLAYING)
        assertNotEquals(PlaybackOrientation.TRAILER, PlaybackOrientation.BROWSING)
    }
}
