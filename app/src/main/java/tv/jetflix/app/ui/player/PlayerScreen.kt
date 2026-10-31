package tv.jetflix.app.ui.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import tv.jetflix.app.data.ticksToMs
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.PlaybackProgressInfo
import tv.jetflix.app.data.PlaybackStartInfo
import tv.jetflix.app.data.PlaybackStopInfo
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.ticksToHms
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

private const val TICKS_PER_SECOND = 10_000_000L
private const val SEEK_STEP_TICKS = 10 * TICKS_PER_SECOND

/** Two vertical bars — the pause glyph (material-icons-core has no Pause). */
val PauseIcon: ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z").toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.White),
        )
    }.build()

@Composable
fun PlayerScreen(
    item: BaseItemDto,
    repo: JellyfinRepository,
    onFinished: () -> Unit,
    onPlayNext: (BaseItemDto) -> Unit = {},
    /** Episode ended with autoplay off and a next episode exists: open that episode's page. */
    onOpenNext: (BaseItemDto) -> Unit = {},
    onOpenActor: (personId: String, name: String) -> Unit = { _, _ -> },
    /** False while the actor page is over the player: playback stays paused where it was, keys go to the page. */
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val session = SessionManager.session.value ?: return
    val api = remember { repo.apiFor(session.serverUrl) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(true) }
    // Stop the TV screensaver while a video is playing or buffering; release it on pause, end and when leaving.
    var keepAwake by remember { mutableStateOf(true) }
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { hostView.keepScreenOn = keepAwake }
    androidx.compose.runtime.DisposableEffect(hostView) { onDispose { hostView.keepScreenOn = false } }
    // Cast strip while paused: people are fetched once, the first time the video is paused.
    val pauseCastOn by tv.jetflix.app.data.AppSettings.pauseCast.collectAsState()
    val isPausedNow by tv.jetflix.app.ui.components.PlaybackPause.paused.collectAsState()
    var cast by remember { mutableStateOf(emptyList<tv.jetflix.app.data.BaseItemPerson>()) }
    var castFetched by remember { mutableStateOf(false) }
    var castIndex by remember { mutableStateOf<Int?>(null) }
    // OK on a cast person opens the actor on key release, never on the press (see CastKeySession).
    val castKeys = remember { PlayerKeys.CastKeySession() }
    LaunchedEffect(isPausedNow, pauseCastOn) {
        if (isPausedNow && pauseCastOn && !castFetched) {
            castFetched = true
            cast = tv.jetflix.app.data.loadPauseCast(api, session.userId, item)
        }
    }
    LaunchedEffect(isPausedNow, pauseCastOn) { if (!isPausedNow || !pauseCastOn) { castIndex = null; castKeys.reset() } }
    var introSegments by remember { mutableStateOf(emptyList<tv.jetflix.app.data.MediaSegmentDto>()) }
    val skipIntroEnabled by tv.jetflix.app.data.AppSettings.skipIntro.collectAsState()
    var positionTicks by remember { mutableLongStateOf(item.UserData?.PlaybackPositionTicks ?: 0L) }
    var durationTicks by remember { mutableLongStateOf(item.RunTimeTicks ?: 0L) }
    var bufferedTicks by remember { mutableLongStateOf(0L) }
    var playSessionId by remember { mutableStateOf<String?>(null) }
    var mediaSourceId by remember { mutableStateOf<String?>(null) }

    val player = remember {
        val httpClient = SessionManager.authenticatedClientBuilder()
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { it.trackSelectionParameters = TrackPrefs(tv.jetflix.app.data.AppSettings.audioLanguage, tv.jetflix.app.data.AppSettings.subtitleLanguage).apply(it.trackSelectionParameters) }
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    // If a direct stream fails on this TV (decoder/format error), ask the server to transcode once and resume in place.
    var transcodeRetry by remember { mutableStateOf(false) }
    var retryResumeMs by remember { mutableStateOf(0L) }
    var playingDirect by remember { mutableStateOf(false) }
    var trackToast by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var panelOptions by remember { mutableStateOf(emptyList<TrackCycler.Option>()) }
    var panelIndex by remember { mutableStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var upNext by remember { mutableStateOf<BaseItemDto?>(null) }
    // Milliseconds left on the up-next countdown; the card's seconds and drain bar both read this one clock.
    var countdownMs by remember { mutableStateOf(UP_NEXT_COUNTDOWN_MS) }

    // When playback ends: for episodes with autoplay on, look up the next episode in server order.
    // While an actor page covers the player (not interactive), the finish waits: the actor page stays until
    // the user leaves it with Back, and the ended player finishes then.
    LaunchedEffect(ended, interactive) {
        if (!shouldAutoFinish(ended, interactive)) return@LaunchedEffect
        val seriesId = item.SeriesId
        if (!item.isEpisode || seriesId == null) {
            // A movie that reaches its natural end is watched: the stop report and the played mark land
            // before the page behind reloads, so it shows Play and the red check, never Resume.
            reportFinishThenNavigate(api, item, session.userId, mediaSourceId, playSessionId, player.currentPosition.coerceAtLeast(0L) * 10_000) { onFinished() }
            return@LaunchedEffect
        }
        // Autoplay off: when an episode ends and there is a next one, open the next episode's
        // page instead of dropping back to where playback started. Navigation only - nothing plays.
        // Back-cancel of the countdown below is untouched: canceling stays canceling.
        if (!tv.jetflix.app.data.AppSettings.autoplayNext.value) {
            when (val outcome = lookupUpNext(api, seriesId, session.userId, item.Id)) {
                is UpNextLookup.Found -> reportStopThenOpenNext(api, item, session.userId, mediaSourceId, playSessionId, player.currentPosition.coerceAtLeast(0L) * 10_000) { onOpenNext(outcome.next) }
                else -> reportFinishThenNavigate(api, item, session.userId, mediaSourceId, playSessionId, player.currentPosition.coerceAtLeast(0L) * 10_000) { onFinished() }
            }
            return@LaunchedEffect
        }
        when (val outcome = lookupUpNext(api, seriesId, session.userId, item.Id)) {
            // The server answered and there is no next episode: leave the player as before.
            UpNextLookup.None -> { reportFinishThenNavigate(api, item, session.userId, mediaSourceId, playSessionId, player.currentPosition.coerceAtLeast(0L) * 10_000) { onFinished() }; return@LaunchedEffect }
            // The lookup itself failed: say so instead of ending playback silently (Todd row 86). Back returns.
            // The watched mark still goes out, so Back leaves a checked episode, not a resumable one.
            UpNextLookup.Failed -> { apiMarkPlayed(api, item, session.userId); playbackError = "Couldn't load the next episode."; return@LaunchedEffect }
            is UpNextLookup.Found -> {
                // The 5s countdown gives this fire-and-forget watched mark time to land before the next
                // episode's player replaces this one.
                apiMarkPlayed(api, item, session.userId)
                upNext = outcome.next
                val startedAt = System.nanoTime()
                countdownMs = UP_NEXT_COUNTDOWN_MS
                // Back during the countdown cancels the card (upNext goes null) and stops the auto-play.
                while (countdownMs > 0L && upNext != null) {
                    delay(16)
                    countdownMs = (UP_NEXT_COUNTDOWN_MS - (System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                }
                if (upNext != null) onPlayNext(outcome.next)
            }
        }
    }

    LaunchedEffect(item.Id, transcodeRetry) {
        val startTicks = if (transcodeRetry) retryResumeMs * 10_000 else item.UserData?.PlaybackPositionTicks ?: 0L
        val choice = try {
            val caps = tv.jetflix.app.data.CodecSupport.detect()
            val audioLang = tv.jetflix.app.data.AppSettings.audioLanguage
            val subsOff = tv.jetflix.app.data.AppSettings.subtitleLanguage == null
            var resp = api.getPlaybackInfo(
                item.Id,
                session.userId,
                tv.jetflix.app.data.PlaybackNegotiator.playbackInfoBody(session.userId, startTicks, caps, forceTranscode = transcodeRetry, subtitlesOff = subsOff),
            )
            // A server transcode carries one audio stream: if it picked another language, ask again for the saved one.
            tv.jetflix.app.data.PlaybackNegotiator.audioRetry(resp.body(), audioLang)?.let { (sourceId, index) ->
                try {
                    val again = api.getPlaybackInfo(
                        item.Id,
                        session.userId,
                        tv.jetflix.app.data.PlaybackNegotiator.playbackInfoBody(session.userId, startTicks, caps, forceTranscode = transcodeRetry, audioStreamIndex = index, mediaSourceId = sourceId, subtitlesOff = subsOff),
                    )
                    if (again.isSuccessful) resp = again
                } catch (_: Exception) { }
            }
            if (!resp.isSuccessful) {
                tv.jetflix.app.data.PlaybackChoice.Unplayable("The server refused playback (HTTP ${resp.code()}).")
            } else {
                playSessionId = resp.body()?.PlaySessionId
                tv.jetflix.app.data.PlaybackNegotiator.choose(resp.body(), session.serverUrl, item.Id)
            }
        } catch (e: Exception) {
            tv.jetflix.app.data.PlaybackChoice.Unplayable("Couldn't reach the server to start playback.")
        }
        when (choice) {
            is tv.jetflix.app.data.PlaybackChoice.Direct -> {
                playingDirect = true
                mediaSourceId = choice.mediaSourceId
                player.setMediaItem(mediaItemFor(choice.url, choice.subtitles), startTicks.ticksToMs())
            }
            is tv.jetflix.app.data.PlaybackChoice.Hls -> {
                playingDirect = false
                mediaSourceId = choice.mediaSourceId
                // Server-side transcode starts at StartTimeTicks already, so start at 0 here.
                player.setMediaItem(mediaItemFor(choice.url, choice.subtitles), 0L)
            }
            is tv.jetflix.app.data.PlaybackChoice.Unplayable -> {
                playbackError = choice.reason
                return@LaunchedEffect
            }
        }
        player.prepare()
        player.playWhenReady = true
        try {
            api.reportPlaybackStart(
                PlaybackStartInfo(
                    ItemId = item.Id,
                    MediaSourceId = mediaSourceId,
                    PlaySessionId = playSessionId,
                    PositionTicks = item.UserData?.PlaybackPositionTicks ?: 0L,
                ),
            )
        } catch (_: Exception) { }
    }

    // Intro spans the server knows about (Jellyfin 10.10+ Media Segments). A server without them returns none.
    LaunchedEffect(item.Id) {
        introSegments = try { api.getMediaSegments(item.Id).body()?.Items.orEmpty() } catch (_: Exception) { emptyList() }
    }
    // The prompt follows the live playhead (4x a second), not the once-a-second progress poll, so it shows and works at the segment start.
    var livePositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(introSegments) {
        if (introSegments.isEmpty()) return@LaunchedEffect
        while (isActive) {
            livePositionMs = player.currentPosition
            delay(250L)
        }
    }
    val skipSegment = tv.jetflix.app.data.SkipIntro.available(skipIntroEnabled, upNext != null || panelOpen, introSegments, livePositionMs)

    // Poll player state + report progress every 10s.
    LaunchedEffect(Unit) {
        while (isActive) {
            positionTicks = player.currentPosition * 10_000
            durationTicks = player.duration.takeIf { it > 0 }?.times(10_000) ?: durationTicks
            bufferedTicks = player.bufferedPosition.takeIf { it > 0 }?.times(10_000) ?: bufferedTicks
            isPlaying = player.isPlaying
            keepAwake = shouldKeepScreenOn(player.playWhenReady, player.playbackState)
            tv.jetflix.app.ui.components.PlaybackPause.paused.value = !player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_READY
            delay(1_000)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { tv.jetflix.app.ui.components.PlaybackPause.paused.value = false } }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(10_000)
            try {
                api.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        ItemId = item.Id,
                        MediaSourceId = mediaSourceId,
                        PlaySessionId = playSessionId,
                        PositionTicks = positionTicks,
                        IsPaused = !isPlaying,
                    ),
                )
            } catch (_: Exception) { }
        }
    }

    // Auto-hide controls after 5 idle seconds while playing.
    LaunchedEffect(controlsVisible, lastInteraction, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5_000)
            if (System.currentTimeMillis() - lastInteraction >= 5_000) controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (playingDirect && !transcodeRetry) {
                    retryResumeMs = player.currentPosition.coerceAtLeast(0L)
                    playingDirect = false
                    player.stop()
                    transcodeRetry = true
                } else {
                    playbackError = "Playback failed (${error.errorCodeName}). This file may use a format this TV can't play."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            try {
                val pos = player.currentPosition * 10_000
                player.stop()
                player.release()
                apiReportStopped(api, item.Id, mediaSourceId, playSessionId, pos)
            } catch (_: Exception) { }
        }
    }

    KeyCaptureBox(
        enabled = interactive,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        onKey = { nativeEvent ->
            run {
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    // A release the cast strip started: an armed OK opens the actor here (one press, one page) and is swallowed.
                    return@run when (castKeys.onRelease(nativeEvent.keyCode)) {
                        PlayerKeys.CastStep.Open -> {
                            castIndex?.let { idx -> cast.getOrNull(idx)?.let { p -> p.Id?.let { onOpenActor(it, p.Name.orEmpty()) } } }
                            true
                        }
                        PlayerKeys.CastStep.Consume -> true
                        else -> false
                    }
                }
                if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@run false
                lastInteraction = System.currentTimeMillis()
                upNext?.let { n ->
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> onPlayNext(n)
                        // Back cancels the card, exactly what the card's own hint says; it must not exit the player (Todd row 86).
                        KeyEvent.KEYCODE_BACK -> upNext = null
                        else -> return@run false
                    }
                    return@run true
                }
                castIndex?.let { idx ->
                    when (val step = castKeys.onPress(nativeEvent.keyCode, idx, cast.size)) {
                        is PlayerKeys.CastStep.Move -> castIndex = step.index
                        PlayerKeys.CastStep.Exit -> castIndex = null
                        else -> Unit
                    }
                    return@run true
                }
                if (panelOpen) {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> panelIndex = (panelIndex - 1).coerceAtLeast(0)
                        KeyEvent.KEYCODE_DPAD_DOWN -> panelIndex = (panelIndex + 1).coerceAtMost((panelOptions.size - 1).coerceAtLeast(0))
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            panelOptions.getOrNull(panelIndex)?.let {
                                TrackCycler.apply(player, it)
                                val saved = TrackPrefs(tv.jetflix.app.data.AppSettings.audioLanguage, tv.jetflix.app.data.AppSettings.subtitleLanguage).after(it, TrackCycler::languageOf)
                                tv.jetflix.app.data.AppSettings.audioLanguage = saved.audioLanguage
                                tv.jetflix.app.data.AppSettings.subtitleLanguage = saved.subtitleLanguage
                            }
                            panelOptions = TrackCycler.options(player)
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> panelOpen = false
                    }
                    return@run true
                }
                when (nativeEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        when (PlayerKeys.pressOk(player, introSegments, skipIntroEnabled, upNext != null || panelOpen, nativeEvent.repeatCount)) {
                            PlayerKeys.CenterAction.SkipIntro -> livePositionMs = player.currentPosition
                            PlayerKeys.CenterAction.TogglePlayback -> controlsVisible = true
                            PlayerKeys.CenterAction.Ignore -> Unit
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        PlayerKeys.togglePlayback(player); true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        controlsVisible = true
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        controlsVisible = true
                        player.seekTo(player.currentPosition + 10_000)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // First Down only brings up the progress bar and title; a second press opens audio and subtitles.
                        when (PlayerKeys.downAction(controlsVisible, nativeEvent.repeatCount, PlayerKeys.castReady(isPausedNow, controlsVisible, pauseCastOn, cast.size))) {
                            PlayerKeys.DownAction.RevealControls -> controlsVisible = true
                            PlayerKeys.DownAction.Ignore -> Unit
                            PlayerKeys.DownAction.OpenCast -> castIndex = 0
                            PlayerKeys.DownAction.OpenPanel -> {
                                panelOptions = TrackCycler.options(player)
                                panelIndex = panelOptions.indexOfFirst { it.selected }.coerceAtLeast(0)
                                panelOpen = true
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU -> {
                        controlsVisible = true
                        panelOptions = TrackCycler.options(player)
                        panelIndex = panelOptions.indexOfFirst { it.selected }.coerceAtLeast(0)
                        panelOpen = true
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> false
                    else -> {
                        controlsVisible = true
                        false
                    }
                }
            }
        },
    ) {
        // The video goes first so everything below (errors, up-next, the audio/subtitle panel) draws in front of it.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        playbackError?.let { msg -> PlayerErrorOverlay(msg, Modifier.align(androidx.compose.ui.Alignment.Center)) }
        if (skipSegment != null) {
            SkipIntroPrompt(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 48.dp, bottom = 120.dp))
        }
        upNext?.let { n ->
            // The seconds and the drain bar read the same countdown clock, so they cannot drift apart.
            UpNextCard(
                title = listOfNotNull(n.episodeLabel(), n.Name).joinToString(" \u00B7 "),
                seconds = ((countdownMs + 999L) / 1_000L).toInt(),
                progress = (countdownMs / UP_NEXT_COUNTDOWN_MS.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 48.dp, bottom = 120.dp),
            )
        }
        if (panelOpen) {
            AudioSubtitlePanel(panelOptions, panelIndex, Modifier.align(androidx.compose.ui.Alignment.CenterEnd).padding(end = 48.dp))
        }
        trackToast?.let { msg ->
            LaunchedEffect(msg) { delay(2_500); if (trackToast == msg) trackToast = null }
            androidx.tv.material3.Text(
                text = msg,
                color = Color.White,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(top = 96.dp, end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        PlayerOverlay(
            visible = controlsVisible, item = item, isPlaying = isPlaying,
            positionTicks = positionTicks, bufferedTicks = bufferedTicks, durationTicks = durationTicks,
            castVisible = PlayerKeys.castReady(isPausedNow, true, pauseCastOn, cast.size),
            cast = cast, castSelected = castIndex, personImageUrl = { repo.personImageUrl(it) },
        )
    }
}

/** Title, pause icon, progress bar and (while paused) the cast strip. Public so renders show the real overlay. */
@Composable
fun PlayerOverlay(
    visible: Boolean,
    item: BaseItemDto,
    isPlaying: Boolean,
    positionTicks: Long,
    bufferedTicks: Long,
    durationTicks: Long,
    castVisible: Boolean,
    cast: List<tv.jetflix.app.data.BaseItemPerson>,
    castSelected: Int?,
    personImageUrl: (tv.jetflix.app.data.BaseItemPerson) -> String?,
) {
AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            // Top: title
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                Text(
                    text = if (item.isEpisode) item.SeriesName ?: item.displayTitle() else item.displayTitle(),
                    style = JetflixType.sectionTitle,
                    color = Color.White,
                )
                if (item.isEpisode) {
                    Text(
                        text = listOfNotNull(item.episodeLabel(), item.Name).joinToString(" · "),
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                    )
                }
            }

            // Center: big pause indicator
            if (!isPlaying) {
                Icon(
                    PauseIcon,
                    contentDescription = "Paused",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp),
                )
            }

            if (castVisible) {
                // Dark bottom-up fade behind the cast strip so photos and names stay readable on bright frames.
                // A gradient, not another live blur: blur is one of the suspects in the TV scroll-stutter investigation.
                // Drawn BEFORE the bottom bar: over it, this scrim dimmed the times and seek bar to near-invisible.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp).background(PauseCastScrim),
                )
            }
            PlayerBottomBar(
                positionTicks, bufferedTicks, durationTicks,
                Modifier.align(Alignment.BottomCenter),
            )
            if (castVisible) {
                PauseCastStrip(
                    cast = cast, selected = castSelected, imageUrlFor = personImageUrl,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                )
            }
        }
    }
}

@Composable
private fun SeekBar(positionTicks: Long, bufferedTicks: Long, durationTicks: Long, modifier: Modifier = Modifier) {
    val duration = durationTicks.coerceAtLeast(1)
    val played = (positionTicks.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (bufferedTicks.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(JetflixColors.ProgressTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(buffered)
                .height(4.dp)
                .background(Color(0xFF7A7A7A)),
        )
        Box(
            Modifier
                .fillMaxWidth(played)
                .height(4.dp)
                .background(JetflixColors.Red),
        )
    }
}

private fun apiReportStopped(
    api: tv.jetflix.app.data.JellyfinApi,
    itemId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
) {
    // onDispose is not a coroutine scope; fire-and-forget on a daemon thread.
    Thread {
        try {
            kotlinx.coroutines.runBlocking {
                api.reportPlaybackStopped(
                    PlaybackStopInfo(
                        ItemId = itemId,
                        MediaSourceId = mediaSourceId,
                        PlaySessionId = playSessionId,
                        PositionTicks = positionTicks,
                    ),
                )
            }
        } catch (_: Exception) { }
    }.start()
}

@Composable
fun AudioSubtitlePanel(panelOptions: List<TrackCycler.Option>, panelIndex: Int, modifier: Modifier = Modifier) {
            androidx.compose.foundation.layout.Column(
                modifier
                                        .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp)
                    .width(360.dp),
            ) {
                androidx.tv.material3.Text("Audio, Subtitles & Speed", color = Color.White, style = JetflixType.sectionTitle)
                var lastType = -1
                panelOptions.forEachIndexed { i, o ->
                    if (o.type != lastType) {
                        lastType = o.type
                        androidx.tv.material3.Text(
                            when (o.type) { androidx.media3.common.C.TRACK_TYPE_AUDIO -> "Audio"; TrackCycler.TYPE_SPEED -> "Speed"; else -> "Subtitles" },
                            color = JetflixColors.TextSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    androidx.tv.material3.Text(
                        text = (if (o.selected) "\u2713 " else "    ") + o.label,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (i == panelIndex) JetflixColors.SurfaceLight else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                if (panelOptions.none { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }) {
                    androidx.tv.material3.Text("No audio tracks reported yet", color = JetflixColors.TextMuted)
                }
                androidx.tv.material3.Text(
                    "Up/Down move, OK selects, Left closes. Text subtitles (SRT, VTT, ASS) only; picture subtitles (PGS, VobSub) are not supported.",
                    color = JetflixColors.TextMuted,
                    style = JetflixType.caption,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
}

private fun mediaItemFor(url: String, subs: List<tv.jetflix.app.data.SubtitleTrack>): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(
            subs.map {
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url))
                    .setMimeType(it.mimeType)
                    .setLanguage(it.language)
                    .setLabel(it.label)
                    .build()
            },
        )
        .build()

/** Seek bar, times, and the remote-control hint along the bottom of the player. */
@Composable
fun PlayerBottomBar(positionTicks: Long, bufferedTicks: Long, durationTicks: Long, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(positionTicks.ticksToHms(), style = JetflixType.caption, color = Color.White)
            Spacer(Modifier.width(14.dp))
            SeekBar(
                positionTicks = positionTicks,
                bufferedTicks = bufferedTicks,
                durationTicks = durationTicks,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(14.dp))
            val remaining = (durationTicks - positionTicks).coerceAtLeast(0)
            Text("-${remaining.ticksToHms()}", style = JetflixType.caption, color = JetflixColors.TextSecondary)
        }
        Text(
            "OK pause/play   \u25C0 \u25B6 skip 10s   \u25B2 \u25BC audio & subtitles",
            style = JetflixType.caption,
            color = JetflixColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}


/** Centered player error note; the message always tells the user how to get back. */
@Composable
internal fun PlayerErrorOverlay(msg: String, modifier: Modifier = Modifier) {
    androidx.tv.material3.Text(
        text = msg + "\nPress Back to return.",
        color = Color.White,
        modifier = modifier.padding(48.dp),
    )
}

/** Length of the up-next countdown in milliseconds. */
internal const val UP_NEXT_COUNTDOWN_MS = 5_000L

/** What the server said plays after the current episode. */
internal sealed interface UpNextLookup {
    /** There is a next episode. */
    class Found(val next: BaseItemDto) : UpNextLookup
    /** The server answered and there is no next episode (end of the series). */
    object None : UpNextLookup
    /** The lookup itself failed (timeout, server error). */
    object Failed : UpNextLookup
}

/** The next episode in server order: Found / None / Failed, so a failed lookup is never mistaken for the end of the series. */
internal suspend fun lookupUpNext(api: tv.jetflix.app.data.JellyfinApi, seriesId: String, userId: String, currentId: String): UpNextLookup {
    val resp = try {
        api.getEpisodes(seriesId, userId)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return UpNextLookup.Failed }
    if (!resp.isSuccessful) return UpNextLookup.Failed
    val next = tv.jetflix.app.data.NextEpisode.pick(resp.body()?.Items ?: emptyList(), currentId)
    return if (next != null) UpNextLookup.Found(next) else UpNextLookup.None
}

/** Only movies and episodes earn the watched check at their natural end; local trailers and other
 *  playable types never show one, so they are never marked. */
internal fun earnsWatchedAtEnd(item: BaseItemDto): Boolean = item.isMovie || item.isEpisode

/** The two writes a natural finish needs: the stop report (clears the resume point) and the watched
 *  mark (the red check). Both are best-effort; a failure leaves the server as it is. */
internal suspend fun reportStopAndMarkPlayed(
    api: tv.jetflix.app.data.JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
) {
    try {
        api.reportPlaybackStopped(
            PlaybackStopInfo(
                ItemId = item.Id,
                MediaSourceId = mediaSourceId,
                PlaySessionId = playSessionId,
                PositionTicks = positionTicks,
            ),
        )
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    if (earnsWatchedAtEnd(item)) {
        try {
            api.markPlayed(item.Id, userId)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }
}

/** Natural-end completion that leaves the player: WAIT for the stop report and the watched mark before
 *  navigating, so the page it lands on (details, Home's Continue Watching) already shows Play and the
 *  red check - the fire-and-forget dispose report alone races that fetch. A failed report or mark still
 *  navigates; the page simply shows the server as it is. The dispose report fires again afterwards with
 *  the same final position, which is harmless. */
internal suspend fun reportFinishThenNavigate(
    api: tv.jetflix.app.data.JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
    navigate: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks)
    navigate()
}

/** Watched mark for the finish paths that keep playing (the autoplay countdown) or surface an error:
 *  fire-and-forget on a daemon thread, like apiReportStopped. */
internal fun apiMarkPlayed(api: tv.jetflix.app.data.JellyfinApi, item: BaseItemDto, userId: String) {
    if (!earnsWatchedAtEnd(item)) return
    Thread {
        try {
            kotlinx.coroutines.runBlocking { api.markPlayed(item.Id, userId) }
        } catch (_: Exception) { }
    }.start()
}

/** Autoplay-off completion path: report playback stopped and mark the episode watched, WAITing for the
 *  server before opening the next episode's page, so the fresh page's fetch already carries the
 *  finished episode's red check (the fire-and-forget dispose report alone races that fetch). A failed
 *  report still navigates - nothing is marked locally, the page simply shows the server as it is. The
 *  dispose report fires again afterwards with the same final position, which is harmless. */
internal suspend fun reportStopThenOpenNext(
    api: tv.jetflix.app.data.JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
    onOpenNext: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks)
    onOpenNext()
}

/** An ended player finishes itself only while nothing covers it; covered (an actor page is open) it waits. */
internal fun shouldAutoFinish(ended: Boolean, interactive: Boolean): Boolean = ended && interactive

/** True while playback is wanted and the player is buffering or ready, so the screensaver must not start. */
internal fun shouldKeepScreenOn(playWhenReady: Boolean, playbackState: Int): Boolean =
    playWhenReady && (playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
        playbackState == androidx.media3.common.Player.STATE_READY)

/** The bottom-right prompt. OK is handled by the player's key handler; this only shows what OK will do. */
@Composable
fun SkipIntroPrompt(modifier: Modifier = Modifier) {
    // Same dark translucent surface and white text as the player's other overlays; small enough to sit with the controls.
    androidx.tv.material3.Text(
        text = "Skip Intro",
        style = JetflixType.caption,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .border(1.dp, JetflixColors.TextSecondary, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Scrim behind the paused cast strip: fully clear up top, dark enough under the hint line that light-gray keys stay readable. */
internal val PauseCastScrim = androidx.compose.ui.graphics.Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.9f),
)

/** Cast along the bottom while paused. Nothing is selected until Down is pressed, so the controls stay first. */
@Composable
fun PauseCastStrip(
    cast: List<tv.jetflix.app.data.BaseItemPerson>,
    selected: Int?,
    imageUrlFor: (tv.jetflix.app.data.BaseItemPerson) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (selected == null) "Cast   \u25BC to browse" else "Cast   \u25C0 \u25B6 choose   OK movies   \u25B2 back", style = JetflixType.caption, color = Color.White)
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = ((selected ?: 0) - 3).coerceAtLeast(0))
        // Keep the chosen actor on screen when the row is longer than the screen.
        LaunchedEffect(selected) { selected?.let { listState.animateScrollToItem((it - 3).coerceAtLeast(0)) } }
        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(cast, key = { _, p -> p.Id ?: p.Name ?: "" }) { i, p ->
                val sel = selected == i
                Column(
                    Modifier.width(96.dp)
                        .then(Modifier.border(if (sel) 3.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(12.dp)))
                        .background(if (sel) Color(0x55FFFFFF) else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tv.jetflix.app.ui.actor.PersonPhoto(url = imageUrlFor(p), name = p.Name.orEmpty(), size = 56.dp)
                    Text(p.Name ?: "", style = JetflixType.caption, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The next-episode countdown card: near-black, faint stroke, soft shadow; the seconds are the hero; a bar drains over the 5 seconds. */
@Composable
fun UpNextCard(title: String, seconds: Int, progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(300.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color(0xF2080808), shape)
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        androidx.tv.material3.Text("UP NEXT", style = JetflixType.caption, color = JetflixColors.TextSecondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        androidx.tv.material3.Text(title, style = JetflixType.body, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.tv.material3.Text("Playing in ", style = JetflixType.body, color = JetflixColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            androidx.tv.material3.Text("$seconds", color = Color.White, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, lineHeight = 34.sp)
        }
        Spacer(Modifier.height(4.dp))
        androidx.tv.material3.Text("OK plays now  \u00B7  Back cancels", style = JetflixType.caption, color = Color(0xFFD0D0D0))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Color.White))
        }
    }
}
