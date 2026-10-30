package tv.jetflix.phone.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The page that hosts YouTube's own embedded player (IFrame Player API), the same approach Jellyfin Web uses for
 * YouTube trailers. The video id is validated (11 characters of A-Z a-z 0-9 _ -), so it is safe to inline.
 * YouTube's own touch controls stay visible and handle taps.
 */
object YouTubeTrailerPage {
    private val idRegex = Regex("^[A-Za-z0-9_-]{11}$")

    /** YouTube asks embedded players to identify the app with a Referer; this is the https form of the app's package id. */
    fun origin(packageName: String): String = "https://" + packageName.lowercase()

    fun html(videoId: String, origin: String): String {
        require(idRegex.matches(videoId)) { "bad video id" }
        return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#player{position:absolute;inset:0;width:100%;height:100%}</style></head>
<body><div id="player"></div>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('player',{width:'100%',height:'100%',videoId:'$videoId',
    playerVars:{autoplay:1,playsinline:1,rel:0,enablejsapi:1,cc_load_policy:0,origin:'$origin'},
    events:{
      onReady:function(e){noCaptions(e.target); e.target.playVideo();},
      onStateChange:function(e){if(e.data==YT.PlayerState.PLAYING){noCaptions(e.target);} Bridge.onState(e.data);},
      onError:function(e){Bridge.onError(e.data);}
    }});
}
function noCaptions(p){try{p.unloadModule('captions');}catch(x){} try{p.setOption('captions','track',{});}catch(x){}}
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>
""".trimIndent()
    }

    /** Short plain-language text for the player error codes in the IFrame API reference. */
    fun errorMessage(code: Int): String = when (code) {
        2 -> "This trailer link isn't valid."
        5 -> "This trailer can't be played here."
        100 -> "This trailer was removed or is private."
        101, 150 -> "The owner doesn't allow this trailer to play outside YouTube."
        153 -> "YouTube didn't accept this app's identity for embedded playback."
        else -> "This trailer can't be played here (error $code)."
    }
}

private class TrailerBridge(
    private val post: (() -> Unit) -> Unit,
    private val onErrorCode: (Int) -> Unit,
    private val onEnded: () -> Unit,
) {
    @JavascriptInterface fun onError(code: Int) = post { onErrorCode(code) }
    @JavascriptInterface fun onState(state: Int) { if (state == 0) post { onEnded() } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeTrailerScreen(videoId: String, title: String, onClose: () -> Unit) {
    tv.jetflix.phone.OrientationWhileShowing(tv.jetflix.phone.PlaybackOrientation.TRAILER)
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val origin = remember { YouTubeTrailerPage.origin(context.packageName) }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        // Stay on YouTube's player; anything else the page tries to open is ignored.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host?.lowercase().orEmpty()
                            val ok = host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtube-nocookie.com") || host.endsWith(".ytimg.com") || host.endsWith(".google.com") || host.endsWith(".googlevideo.com")
                            return !ok
                        }
                    }
                    addJavascriptInterface(
                        TrailerBridge(
                            post = { block -> post { block() } },
                            onErrorCode = { error = YouTubeTrailerPage.errorMessage(it) },
                            onEnded = onClose,
                        ),
                        "Bridge",
                    )
                    loadDataWithBaseURL(origin, YouTubeTrailerPage.html(videoId, origin), "text/html", "utf-8", null)
                }
            },
            onRelease = { wv ->
                wv.stopLoading()
                wv.removeJavascriptInterface("Bridge")
                wv.destroy()
            },
        )
        BackIconButton(onClose, Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart))
        error?.let { TrailerErrorOverlay(it) }
    }
}

@Composable
fun TrailerErrorOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Text(message + "\nTap Back to return.", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(48.dp))
    }
}
