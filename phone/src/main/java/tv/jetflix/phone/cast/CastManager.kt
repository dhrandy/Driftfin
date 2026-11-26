package tv.jetflix.phone.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.jetflix.phone.data.SessionManager

/** What the Chromecast is doing right now, for the cast button tint and the player handoff. */
sealed interface CastState {
    data object Disconnected : CastState
    data object Connecting : CastState
    data class Connected(val deviceName: String, val playingItemId: String?) : CastState
}

/** One place that owns the CastContext, the session lifecycle, and loading media
 *  onto the receiver. The Chromecast fetches the Jellyfin stream itself, so the
 *  URL carries the session token as api_key (Jellyfin's query-param auth). */
object CastManager {

    private var castContext: CastContext? = null
    private val _state = MutableStateFlow<CastState>(CastState.Disconnected)
    val state: StateFlow<CastState> = _state

    /** Last position (ms) the receiver reported; the player reads it when the
     *  session ends so resume survives the handoff back to the phone. */
    var lastRemotePositionMs: Long? = null
        private set

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = update(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = update(session)
        override fun onSessionStarting(session: CastSession) { _state.value = CastState.Connecting }
        override fun onSessionResuming(session: CastSession, sessionId: String) { _state.value = CastState.Connecting }
        override fun onSessionEnding(session: CastSession) { capturePosition(session) }
        override fun onSessionEnded(session: CastSession, error: Int) { _state.value = CastState.Disconnected }
        override fun onSessionResumeFailed(session: CastSession, error: Int) { _state.value = CastState.Disconnected }
        override fun onSessionStartFailed(session: CastSession, error: Int) { _state.value = CastState.Disconnected }
        override fun onSessionSuspended(session: CastSession, reason: Int) { capturePosition(session); _state.value = CastState.Disconnected }
        private fun update(session: CastSession) {
            val name = session.castDevice?.friendlyName ?: "Chromecast"
            _state.value = CastState.Connected(name, session.remoteMediaClient?.currentItem?.media?.contentId)
        }
    }

    fun init(context: Context) {
        if (castContext != null) return
        try {
            val ctx = CastContext.getSharedInstance(context.applicationContext)
            castContext = ctx
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        } catch (e: Exception) {
            // Play services missing or cast unavailable on this device: leave the
            // button hidden, the app works exactly as before.
            castContext = null
        }
    }

    val available: Boolean get() = castContext != null

    fun currentSession(): CastSession? =
        castContext?.sessionManager?.currentCastSession

    /** Load an item onto the connected receiver, starting at [positionMs].
     *  Returns false when no session is connected. */
    fun load(itemId: String, mediaSourceId: String?, title: String, subtitle: String?,
             imageUrl: String?, streamUrl: String, mimeType: String, positionMs: Long): Boolean {
        val session = currentSession() ?: return false
        val client = session.remoteMediaClient ?: return false
        val token = SessionManager.session.value?.token ?: return false
        val authed = authedStreamUrl(streamUrl, token)
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (subtitle != null) putString(MediaMetadata.KEY_SUBTITLE, subtitle)
            if (imageUrl != null) addImage(WebImage(Uri.parse(imageUrl)))
        }
        val info = MediaInfo.Builder(authed)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(meta)
            .build()
        lastRemotePositionMs = null
        client.load(MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setCurrentTime(positionMs)
            .setAutoplay(true)
            .build())
        _state.value = CastState.Connected(session.castDevice?.friendlyName ?: "Chromecast", itemId)
        return true
    }

    fun pauseOrPlay() {
        val client = currentSession()?.remoteMediaClient ?: return
        if (client.isPlaying) client.pause() else client.play()
    }

    fun seekTo(positionMs: Long) {
        currentSession()?.remoteMediaClient?.seek(positionMs)
    }

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private fun capturePosition(session: CastSession) {
        try {
            lastRemotePositionMs = session.remoteMediaClient?.approximateStreamPosition
        } catch (_: Exception) {}
    }
}

/** Jellyfin accepts the session token as a query param; the receiver has no other way to
 *  authenticate, so the cast URL carries it. Never double-appends. */
internal fun authedStreamUrl(url: String, token: String): String {
    if (url.contains("api_key=")) return url
    return url + (if (url.contains('?')) "&" else "?") + "api_key=" + token
}
