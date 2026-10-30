package tv.jetflix.phone.ui

import android.app.Activity
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tv.jetflix.phone.Graph
import tv.jetflix.phone.LandscapeWhilePlaying
import tv.jetflix.phone.PlaybackOrientation
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.CodecSupport
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.MediaSegmentDto
import tv.jetflix.phone.data.PlaybackChoice
import tv.jetflix.phone.data.PlaybackNegotiator
import tv.jetflix.phone.data.PlaybackProgressInfo
import tv.jetflix.phone.data.PlaybackStartInfo
import tv.jetflix.phone.data.PlaybackStopInfo
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.SkipIntro
import tv.jetflix.phone.data.ticksToMs

/**
 * Full-screen playback. Landscape while this screen is up and portrait on leaving, system bars hidden.
 * The server decides direct play or a transcode from what this phone can decode. If a direct stream fails on the device
 * the same title is asked again as a transcode once. Progress goes back to Jellyfin every 10 seconds and on exit.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(id: String, onExit: () -> Unit, onPlayNext: (String) -> Unit = {}) {
    LandscapeWhilePlaying()
    val context = LocalContext.current
    val activity = context as? Activity
    val session = SessionManager.session.value
    val api = remember { Graph.repo.apiFor(session?.serverUrl.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var playSession by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf("DirectPlay") }
    var transcodeRetry by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var segments by remember { mutableStateOf<List<MediaSegmentDto>>(emptyList()) }
    var positionMs by remember { mutableStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var upNext by remember { mutableStateOf<BaseItemDto?>(null) }
    var countdown by remember { mutableIntStateOf(5) }
    var picker by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // All exits funnel through leave(): rotate back to portrait and detach the player view a couple of
    // frames before popping, so the SurfaceView is gone before the navigation transition runs. Tearing
    // it down mid-transition left a blank screen on the phone.
    val leave: () -> Unit = { if (!leaving) leaving = true }
    val player = remember {
        val http = OkHttpDataSource.Factory(SessionManager.authenticatedClientBuilder().build())
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))).build().also {
            tv.jetflix.phone.data.TrackPrefs.init(context)
            it.trackSelectionParameters = tv.jetflix.phone.data.TrackPrefs.applyTo(it.trackSelectionParameters)
        }
    }

    LaunchedEffect(id, attempt) {
        error = null
        val loaded = Content.item(id)
        if (loaded == null || session == null) { error = "Could not load this title."; return@LaunchedEffect }
        item = loaded
        val start = if (attempt == 0) loaded.UserData?.PlaybackPositionTicks ?: 0L else (player.currentPosition * 10_000)
        val choice = try {
            val caps = CodecSupport.detect()
            val audioLang = tv.jetflix.phone.data.TrackPrefs.audioLanguage ?: "eng"
            val subsOff = tv.jetflix.phone.data.TrackPrefs.subtitleChoice.let { it == null || it == "off" }
            var resp = api.getPlaybackInfo(id, session.userId, PlaybackNegotiator.playbackInfoBody(session.userId, start, caps, forceTranscode = transcodeRetry, subtitlesOff = subsOff))
            // A server transcode carries one audio stream: if it picked another language, ask again for the saved one.
            PlaybackNegotiator.audioRetry(resp.body(), audioLang)?.let { (srcId, index) ->
                try {
                    val again = api.getPlaybackInfo(id, session.userId, PlaybackNegotiator.playbackInfoBody(session.userId, start, caps, forceTranscode = transcodeRetry, audioStreamIndex = index, mediaSourceId = srcId, subtitlesOff = subsOff))
                    if (again.isSuccessful) resp = again
                } catch (_: Exception) { }
            }
            if (!resp.isSuccessful) PlaybackChoice.Unplayable("The server refused playback (HTTP ${resp.code()}).")
            else { playSession = resp.body()?.PlaySessionId; PlaybackNegotiator.choose(resp.body(), session.serverUrl, id) }
        } catch (e: Exception) { PlaybackChoice.Unplayable("Could not reach the server to start playback.") }
        val (url, subs) = when (choice) {
            is PlaybackChoice.Direct -> { sourceId = choice.mediaSourceId; method = "DirectPlay"; choice.url to choice.subtitles }
            is PlaybackChoice.Hls -> { sourceId = choice.mediaSourceId; method = "Transcode"; choice.url to choice.subtitles }
            is PlaybackChoice.Unplayable -> { error = choice.reason; return@LaunchedEffect }
        }
        val mediaItem = MediaItem.Builder().setUri(url)
            .setSubtitleConfigurations(subs.map {
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url)).setMimeType(it.mimeType).setLanguage(it.language).setLabel(it.label).build()
            }).apply { if (choice is PlaybackChoice.Hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        player.setMediaItem(mediaItem, if (choice is PlaybackChoice.Hls) C.TIME_UNSET else start.ticksToMs())
        player.prepare(); player.playWhenReady = true
        try { api.reportPlaybackStart(PlaybackStartInfo(ItemId = id, MediaSourceId = sourceId, PlaySessionId = playSession, PositionTicks = start, PlayMethod = method)) } catch (_: Exception) {}
    }
    // After an episode ends: look up the next one in server order and count down 5 seconds. Otherwise leave the player.
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val cur = item
        val seriesId = cur?.SeriesId
        if (cur == null || !cur.isEpisode || seriesId == null || session == null || !tv.jetflix.phone.data.PhoneSettings.autoplayNext.value) { leave(); return@LaunchedEffect }
        val next = try { tv.jetflix.phone.data.NextEpisode.pick(api.getEpisodes(seriesId, session.userId).body()?.Items.orEmpty(), cur.Id) } catch (_: Exception) { null }
        if (next == null) { leave(); return@LaunchedEffect }
        upNext = next; countdown = 5
        while (countdown > 0) { delay(1000); countdown-- }
        onPlayNext(next.Id)
    }
    LaunchedEffect(id) { segments = try { api.getMediaSegments(id).body()?.Items.orEmpty() } catch (_: Exception) { emptyList() } }
    LaunchedEffect(Unit) {
        var sinceReport = 0
        while (isActive) {
            positionMs = player.currentPosition
            delay(250)
            sinceReport++
            if (sinceReport >= 10) {
                sinceReport = 0
                try {
                    api.reportPlaybackProgress(PlaybackProgressInfo(ItemId = id, MediaSourceId = sourceId, PlaySessionId = playSession,
                        PositionTicks = player.currentPosition * 10_000, IsPaused = !player.isPlaying, PlayMethod = method))
                } catch (_: Exception) {}
            }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                if (method == "DirectPlay" && !transcodeRetry) { transcodeRetry = true; attempt++ }
                else error = "This file could not be played on the phone."
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended = true }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { tracksVersion++ }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        // Token-guarded: a popped episode's dispose must not undo the next episode's window setup.
        val windowToken = Any()
        tv.jetflix.phone.PlayerWindowTokens.window.set(windowToken)
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val pos = player.currentPosition * 10_000
            // The player is about to be released, so the stop report runs on a short-lived thread.
            Thread { try { kotlinx.coroutines.runBlocking { api.reportPlaybackStopped(PlaybackStopInfo(ItemId = id, MediaSourceId = sourceId, PlaySessionId = playSession, PositionTicks = pos)) } } catch (_: Exception) {} }.start()
            if (tv.jetflix.phone.PlayerWindowTokens.window.compareAndSet(windowToken, null)) {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        activity?.requestedOrientation = PlaybackOrientation.BROWSING
        withFrameNanos { }
        withFrameNanos { }
        onExit()
    }
    BackHandler(onBack = leave)
    val skip = SkipIntro.active(segments, positionMs)
    // The pinch handler lives on the container so single-finger taps still reach the player controls:
    // two fingers spread fills the screen (cropping the edges), pinch back in fits the picture again.
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startDistance = -1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val d = kotlin.math.hypot(a.x - b.x, a.y - b.y)
                    if (startDistance < 0f) {
                        startDistance = d
                    } else if (d > 0f) {
                        val ratio = d / startDistance
                        if (ratio > 1.25f && !fill) {
                            fill = true
                            playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            startDistance = d
                        } else if (ratio < 0.8f && fill) {
                            fill = false
                            playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            startDistance = d
                        }
                        pressed.forEach { c -> if (c.positionChanged()) c.consume() }
                    }
                }
            }
        }
    }) {
        if (!leaving) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE })
                        playerView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        PlayerChrome(
            visible = controlsVisible || !playing || ended,
            fill = fill,
            onBack = leave,
            onAudioSubtitles = { picker = true },
            onToggleZoom = {
                fill = !fill
                playerView?.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (skip != null) {
            // The press decides from the live position, not the polled state.
            SkipIntroButton(
                onSkip = { SkipIntro.active(segments, player.currentPosition)?.let { player.seekTo(SkipIntro.endMs(it)) } },
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp).padding(bottom = 56.dp),
            )
        }
        upNext?.let { n ->
            Column(Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Up next", color = Color.White, fontSize = 14.sp)
                Text(n.Name.orEmpty(), color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Starting in $countdown", color = Color.White, fontSize = 16.sp)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Button(onClick = { onPlayNext(n.Id) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Play now", fontSize = 16.sp) }
                    OutlinedButton(onClick = leave, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel", fontSize = 16.sp) }
                }
            }
        }
        error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center)) }
    }
    if (picker) TrackPicker(player, tracksVersion) { picker = false }
}

/** Audio and subtitle choices from what the player found in the stream. */
@Composable
private fun TrackPicker(player: Player, version: Int, onClose: () -> Unit) {
    val groups = player.currentTracks.groups
    fun label(g: androidx.media3.common.Tracks.Group): String {
        val f = g.getTrackFormat(0)
        return listOfNotNull(f.label, f.language?.let { java.util.Locale(it).displayLanguage }).distinct().joinToString(" · ").ifBlank { "Track" }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Audio and subtitles") },
        text = {
            Column(Modifier.padding(0.dp)) {
                Text("Audio")
                groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
                    TextButton(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                        tv.jetflix.phone.data.TrackPrefs.audioLanguage = g.getTrackFormat(0).language
                    }) { Text((if (g.isSelected) "✓ " else "") + label(g)) }
                }
                Text("Subtitles")
                TextButton(onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    tv.jetflix.phone.data.TrackPrefs.subtitleChoice = "off"
                }) { Text((if (groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }) "✓ " else "") + "Off") }
                groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
                    TextButton(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                        tv.jetflix.phone.data.TrackPrefs.subtitleChoice = g.getTrackFormat(0).language
                    }) { Text((if (g.isSelected) "✓ " else "") + label(g)) }
                }
            }
        },
    )
}

/** Compact dark prompt, same look as the TV app. Extracted so pixel checks can render it without a player. */
@Composable
fun SkipIntroButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onSkip,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8A8A8A)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xD9000000), contentColor = Color.White)) { Text("Skip Intro", fontSize = 13.sp) }
}

/**
 * Player overlay chrome: back, fill/fit and the Audio · Subtitles entry point. Shown with the player
 * controls and while paused, hidden with them. Extracted so visibility and clicks are unit testable.
 */
@Composable
fun PlayerChrome(
    visible: Boolean,
    fill: Boolean,
    onBack: () -> Unit,
    onAudioSubtitles: () -> Unit,
    onToggleZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            BackIconButton(onBack)
            OutlinedButton(onClick = onToggleZoom, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x66000000), contentColor = Color.White)) { Text(if (fill) "Fit" else "Fill") }
        }
        Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
            OutlinedButton(onClick = onAudioSubtitles, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x66000000), contentColor = Color.White)) { Text("Audio · Subtitles") }
        }
    }
}
