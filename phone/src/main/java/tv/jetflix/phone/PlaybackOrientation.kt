package tv.jetflix.phone

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** The orientation each playback state asks for. Kept as plain values so a unit test can check them. */
object PlaybackOrientation {
    /** Landscape while the player is on screen, in either direction so a flipped phone still works. */
    const val PLAYING = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    /** Trailers follow the phone itself: upright stays portrait, turning the phone rotates the video (reported 10/29: "can it auto rotate?"). */
    const val TRAILER = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    /** Everywhere else the app is portrait. */
    const val BROWSING = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

/** Window-shared playback setup is claimed with tokens. Auto-next pops and re-pushes the player in one
 *  navigation, and the popped screen's dispose runs AFTER the new screen's setup (proven: add:2 then
 *  clear:1), so an unguarded old dispose would undo the new episode's keep-awake, bars and orientation
 *  (reported 10/29: the screen dimmed when the second show started). */
internal object PlayerWindowTokens {
    val window = java.util.concurrent.atomic.AtomicReference<Any?>()
    val orientation = java.util.concurrent.atomic.AtomicReference<Any?>()
}

/** Switches to landscape while this is in the composition and back to portrait when the player is left. */
@Composable
fun LandscapeWhilePlaying() {
    OrientationWhileShowing(PlaybackOrientation.PLAYING)
}

/** Lets the screen follow the phone's own rotation while it shows, back to portrait once left. */
@Composable
fun OrientationWhileShowing(orientation: Int) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val token = Any()
        PlayerWindowTokens.orientation.set(token)
        activity?.requestedOrientation = orientation
        onDispose { if (PlayerWindowTokens.orientation.compareAndSet(token, null)) activity?.requestedOrientation = PlaybackOrientation.BROWSING }
    }
}
