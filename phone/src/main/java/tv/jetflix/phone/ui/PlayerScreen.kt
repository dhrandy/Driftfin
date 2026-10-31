package tv.jetflix.phone.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import tv.jetflix.phone.data.ticksToHms
import tv.jetflix.phone.data.ticksToMs

/**
 * Full-screen playback. Landscape while this screen is up and portrait on leaving, system bars hidden.
 * The server decides direct play or a transcode from what this phone can decode. If a direct stream fails on the device
 * the same title is asked again as a transcode once. Progress goes back to Jellyfin every 10 seconds and on exit.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(id: String, onExit: () -> Unit, onPlayNext: (String) -> Unit = {}, onOpenNext: (String) -> Unit = {}) {
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
    var bufferedMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
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
        ExoPlayer.Builder(context).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))).build().also {
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
    // Natural end = watched. A movie, an episode with no next, and any autoplay-off path leave the
    // player only after the server has both writes (stop report + watched mark), so the page it lands
    // on already shows the title as played. Autoplay-off with a next episode opens the next episode's
    // page after the same awaited writes. The countdown path keeps a fire-and-forget mark: the 5
    // seconds cover the write. Same finish semantics as the TV player.
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val cur = item
        if (cur == null || session == null) { leave(); return@LaunchedEffect }
        val userId = session.userId
        val positionTicks = player.currentPosition.coerceAtLeast(0L) * 10_000
        val seriesId = cur.SeriesId
        if (!cur.isEpisode || seriesId == null) {
            reportFinishThenNavigate(api, cur, userId, sourceId, playSession, positionTicks) { leave() }
            return@LaunchedEffect
        }
        val outcome = lookupUpNext(api, seriesId, userId, cur.Id)
        if (!tv.jetflix.phone.data.PhoneSettings.autoplayNext.value) {
            when (outcome) {
                is UpNextLookup.Found -> reportStopThenOpenNext(api, cur, userId, sourceId, playSession, positionTicks) { onOpenNext(outcome.next.Id) }
                else -> reportFinishThenNavigate(api, cur, userId, sourceId, playSession, positionTicks) { leave() }
            }
            return@LaunchedEffect
        }
        when (outcome) {
            is UpNextLookup.None -> reportFinishThenNavigate(api, cur, userId, sourceId, playSession, positionTicks) { leave() }
            is UpNextLookup.Failed -> { apiMarkPlayed(api, cur, userId); error = "Finished. Couldn't load the next episode." }
            is UpNextLookup.Found -> {
                apiMarkPlayed(api, cur, userId)
                upNext = outcome.next; countdown = 5
                while (countdown > 0) { delay(1000); countdown-- }
                onPlayNext(outcome.next.Id)
            }
        }
    }
    LaunchedEffect(id) { segments = try { api.getMediaSegments(id).body()?.Items.orEmpty() } catch (_: Exception) { emptyList() } }
    LaunchedEffect(Unit) {
        var sinceReport = 0
        while (isActive) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
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
    // Our controls hide themselves a few seconds into playback, like the old controller did.
    // Paused or finished keeps them up so there is always a way out.
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) { delay(3_500); controlsVisible = false }
    }
    BackHandler(onBack = leave)
    val skip = SkipIntro.active(segments, positionMs)
    // The pinch handler lives on the container so single-finger taps still reach the player controls:
    // two fingers spread fills the screen (cropping the edges), pinch back in fits the picture again.
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        detectTapGestures { controlsVisible = !controlsVisible }
    }.pointerInput(Unit) {
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
                        // The compose overlay below replaces the default controller: its progress bar
                        // matches the TV app (track, buffered gray, red played) and stays touch-sized.
                        useController = false
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
        PlayerCenterControls(
            visible = controlsVisible || !playing || ended,
            playing = playing,
            onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
            onSkipBack = { player.seekBack() },
            onSkipForward = { player.seekForward() },
            modifier = Modifier.align(Alignment.Center),
        )
        PlayerProgressBar(
            visible = controlsVisible || !playing || ended,
            positionMs = positionMs,
            bufferedMs = bufferedMs,
            durationMs = durationMs,
            onSeekToFraction = { f -> val d = player.duration; if (d > 0) player.seekTo((d * f).toLong()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (skip != null) {
            // The press decides from the live position, not the polled state.
            SkipIntroButton(
                onSkip = { SkipIntro.active(segments, player.currentPosition)?.let { player.seekTo(SkipIntro.endMs(it)) } },
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp).padding(bottom = 84.dp),
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

/** Play/pause with 10 second skips, the same jump the TV remote's left/right makes. */
@Composable
fun PlayerCenterControls(
    visible: Boolean,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(28.dp)) {
        OutlinedButton(onClick = onSkipBack, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x66000000), contentColor = Color.White)) { Text("-10s") }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                // material-icons-core has no pause glyph, so the two bars are drawn.
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(8.dp).height(28.dp).background(Color.White))
                    Box(Modifier.width(8.dp).height(28.dp).background(Color.White))
                }
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        OutlinedButton(onClick = onSkipForward, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x66000000), contentColor = Color.White)) { Text("+10s") }
    }
}

/** The TV player's seek bar: a 4.dp rounded track, buffered gray, played red. The touch target
 *  around it is 32.dp so a thumb can drag it; tapping anywhere on it jumps there. Public for pixel tests. */
@Composable
fun PlayerSeekBar(positionMs: Long, bufferedMs: Long, durationMs: Long, onSeekToFraction: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val played = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { onSeekToFraction((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragFraction?.let(onSeekToFraction); dragFraction = null },
                    onDragCancel = { dragFraction = null },
                )
            },
    ) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(DriftfinColors.ProgressTrack),
        ) {
            Box(Modifier.fillMaxWidth(buffered).height(4.dp).background(Color(0xFF7A7A7A)))
            Box(Modifier.fillMaxWidth(played).height(4.dp).background(DriftfinColors.Red))
        }
    }
}

/** Bottom strip mirroring the TV player's: current time, seek bar, remaining time, on a dark fade. */
@Composable
fun PlayerProgressBar(
    visible: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((positionMs * 10_000L).ticksToHms(), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            PlayerSeekBar(positionMs, bufferedMs, durationMs, onSeekToFraction, Modifier.weight(1f).testTag("player-seek-bar"))
            Spacer(Modifier.width(10.dp))
            val remaining = (durationMs - positionMs).coerceAtLeast(0L)
            Text("-${(remaining * 10_000L).ticksToHms()}", color = DriftfinColors.TextSecondary, fontSize = 12.sp)
        }
    }
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
