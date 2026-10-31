package tv.jetflix.app.ui.details

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import tv.jetflix.app.ui.components.resumePercent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.tv.material3.LocalContentColor
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.EpisodeTarget
import tv.jetflix.app.data.NextEpisode
import tv.jetflix.app.data.fromStart
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.components.ArtImage
import tv.jetflix.app.ui.components.restoreFocus
import tv.jetflix.app.ui.components.MediaRow
import tv.jetflix.app.ui.components.PosterCard
import tv.jetflix.app.ui.components.ResumeProgressBar
import tv.jetflix.app.ui.components.WideCard
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

data class DetailsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: BaseItemDto? = null,
    val playTarget: BaseItemDto? = null,
    val episodes: List<BaseItemDto> = emptyList(),
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val similar: List<BaseItemDto> = emptyList(),
    val trailer: tv.jetflix.app.data.TrailerSource? = null,
    val is4k: Boolean = false,
    val hdrFormats: List<String> = emptyList(),
    /** Episodes the server says have a 4K version (one small query per show page). Empty when unknown or on error. */
    val fourKEpisodeIds: Set<String> = emptySet(),
)

class DetailsViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val state = _state.asStateFlow()

    private var allEpisodes: List<BaseItemDto> = emptyList()
    private var seriesTarget: BaseItemDto? = null
    private var firstLoad = true
    private var loadedId: String? = null
    private var stale = false

    /** Playback or a local trailer changed progress off-screen; the next load() must refetch instead of keeping the page. */
    fun invalidate() { stale = true }

    fun load(itemId: String, focusEpisodeId: String? = null) {
        // The tapped episode only decides the first open; coming back from the player re-picks from fresh progress.
        val focusId = if (firstLoad) focusEpisodeId else null
        firstLoad = false
        // Back from an actor page to the same already-loaded title: keep state (and scroll) exactly as it was.
        if (!stale && loadedId == itemId && _state.value.item?.Id == itemId) return
        val session = SessionManager.session.value ?: return
        val api = repo.apiFor(session.serverUrl)
        viewModelScope.launch {
            _state.value = DetailsUiState(loading = true)
            try {
                val item = api.getItem(itemId, session.userId).body()
                if (item == null) {
                    _state.value = DetailsUiState(loading = false, error = "Title not found")
                    return@launch
                }
                loadedId = itemId
                stale = false
                val trailerDef = async {
                    // Jellyfin's own list of trailer files, then the YouTube links on the item. A failed lookup just hides the button.
                    val local = if ((item.LocalTrailerCount ?: 0) > 0) {
                        try { api.getLocalTrailers(itemId, session.userId).body() ?: emptyList() } catch (_: Exception) { emptyList() }
                    } else emptyList()
                    tv.jetflix.app.data.Trailers.pick(item, local)
                }
                val similarDef = async {
                    // A failed similar-items request only hides the row; it must not replace the whole page with an error.
                    try { tv.jetflix.app.data.RelatedItems.filter(item, api.getSimilar(itemId, session.userId).body()?.Items ?: emptyList()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
                }
                if (!item.isSeries) {
                    // Movies show the page as soon as the title itself arrives; "More like this" fills in when ready.
                    _state.value = DetailsUiState(loading = false, item = item, playTarget = item, is4k = tv.jetflix.app.data.Resolution4K.badge(item), hdrFormats = tv.jetflix.app.data.HdrFormats.of(item))
                    val trailer = trailerDef.await()
                    if (_state.value.item?.Id == item.Id) _state.value = _state.value.copy(trailer = trailer)
                    val similar = similarDef.await()
                    if (_state.value.item?.Id == item.Id) _state.value = _state.value.copy(similar = similar)
                    return@launch
                }
                // Shows: the season list and the full episode list are fetched side by side, not one after the other.
                val seasonsDef = async {
                    api.getItems(
                        userId = session.userId,
                        parentId = item.Id,
                        includeItemTypes = "Season",
                        sortBy = "SortName",
                    ).body()?.Items ?: emptyList()
                }
                val episodesDef = async {
                    api.getEpisodes(item.Id, session.userId, fields = "Overview,PremiereDate,People", limit = 2000).body()?.Items ?: emptyList()
                }
                // Which episodes have a 4K version: the server's own is4K filter, IDs only, one request per page open.
                // A failure just means no episode badges.
                val fourKDef = async {
                    try {
                        api.getItems(
                            userId = session.userId, parentId = item.Id, includeItemTypes = "Episode",
                            recursive = true, is4K = true, fields = "", enableImages = false, enableUserData = false,
                        ).body()?.Items?.map { it.Id }?.toSet() ?: emptySet<String>()
                    } catch (_: Exception) { emptySet<String>() }
                }
                val seasons = seasonsDef.await()
                allEpisodes = episodesDef.await()
                val target = allEpisodes.firstOrNull { it.Id == focusId } ?: EpisodeTarget.pick(allEpisodes)
                seriesTarget = target
                val selectedSeasonId = target?.SeasonId ?: seasons.firstOrNull()?.Id
                val episodes = allEpisodes.filter { it.SeasonId == selectedSeasonId }.ifEmpty { allEpisodes }
                _state.value = DetailsUiState(
                    loading = false,
                    item = item,
                    playTarget = target ?: item,
                    episodes = episodes,
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    similar = emptyList(),
                )
                val fourK = fourKDef.await()
                if (_state.value.item?.Id == item.Id) _state.value = _state.value.copy(fourKEpisodeIds = fourK)
                val trailer = trailerDef.await()
                if (_state.value.item?.Id == item.Id) _state.value = _state.value.copy(trailer = trailer)
                val similar = similarDef.await()
                if (_state.value.item?.Id == item.Id) _state.value = _state.value.copy(similar = similar)
            } catch (e: Exception) {
                _state.value = DetailsUiState(loading = false, error = "Couldn't load details (${e.javaClass.simpleName})")
            }
        }
    }

    fun selectSeason(@Suppress("UNUSED_PARAMETER") seriesId: String, seasonId: String) {
        // Season switching only changes the list; Play/Resume keeps pointing at where you are in the series.
        _state.value = _state.value.copy(
            episodes = allEpisodes.filter { it.SeasonId == seasonId },
            selectedSeasonId = seasonId,
        )
    }

    fun toggleFavorite() {
        val session = SessionManager.session.value ?: return
        val item = _state.value.item ?: return
        val api = repo.apiFor(session.serverUrl)
        viewModelScope.launch {
            try {
                val updated = if (item.UserData?.IsFavorite == true) {
                    api.removeFavorite(item.Id, session.userId).body()
                } else {
                    api.addFavorite(item.Id, session.userId).body()
                }
                if (updated != null) {
                    _state.value = _state.value.copy(item = item.copy(UserData = updated))
                }
            } catch (_: Exception) { }
        }
    }

    /** Star on/off in Driftfin's own Watchlist (not a Jellyfin favorite). Local-only; never touches the server. */
    fun toggleWatchlist() {
        val item = _state.value.item ?: return
        tv.jetflix.app.data.Watchlist.toggle(item.Id)
    }

    fun toggleWatched() {
        val session = SessionManager.session.value ?: return
        val item = _state.value.item ?: return
        // On a show page the check acts on the play-target episode named in Play/Resume,
        // never the series itself - marking the series ID marks every episode on the server.
        val target = watchedToggleTarget(item, _state.value.playTarget)
        val api = repo.apiFor(session.serverUrl)
        viewModelScope.launch {
            try {
                val updated = if (target.UserData?.Played == true) {
                    api.markUnplayed(target.Id, session.userId).body()
                } else {
                    api.markPlayed(target.Id, session.userId).body()
                }
                if (updated != null) applyWatchedUpdate(target, updated)
            } catch (_: Exception) { }
        }
    }

    /** Applies the server's watched/unwatched response. Movies: the page item. Episodes: the
     *  episode's row below updates immediately (red check appears or clears), and Play/Resume
     *  advances to the next episode when the target was just watched, or back onto it when
     *  unwatched - "marked off loads the next one", in server order. */
    private fun applyWatchedUpdate(target: BaseItemDto, updated: UserItemDataDto) {
        val state = _state.value
        if (target.Type != "Episode") {
            state.item?.takeIf { it.Id == target.Id }?.let { item ->
                _state.value = state.copy(item = item.copy(UserData = updated))
            }
            return
        }
        val newAll = allEpisodes.map { if (it.Id == target.Id) it.copy(UserData = updated) else it }
        allEpisodes = newAll
        val newTarget = advancedTargetAfterWatched(newAll, target.Id, updated.Played == true, state.playTarget)
        _state.value = state.copy(
            playTarget = newTarget ?: state.playTarget,
            episodes = newAll.filter { it.SeasonId == state.selectedSeasonId }.ifEmpty { newAll },
        )
    }
}

/** Which item the details-page watched check toggles: on a series page, the play-target episode
 *  named in Play/Resume; anywhere else the page item itself. Never the series ID - that would
 *  mark every episode watched on the server. */
internal fun watchedToggleTarget(item: BaseItemDto, playTarget: BaseItemDto?): BaseItemDto =
    if (item.isSeries && playTarget?.Type == "Episode") playTarget else item

/** Play/Resume target after the target episode's watched state flips. Watched: the next episode in
 *  server order ("marked off loads the next one"), falling back to the general pick at season/series
 *  end. Unwatched: back onto the just-cleared episode. */
internal fun advancedTargetAfterWatched(
    all: List<BaseItemDto>,
    markedId: String,
    played: Boolean,
    current: BaseItemDto?,
): BaseItemDto? =
    if (played) NextEpisode.pick(all, markedId) ?: EpisodeTarget.pick(all) ?: current
    else all.firstOrNull { it.Id == markedId } ?: current

/** Cast row on a series page: the series' own actors first, then the play-target episode's actors the
 *  series list doesn't name (sparse series metadata must not hide the episode's own
 *  guest cast still belongs). Deduped by person Id, name as fallback; capped at 15 as before. */
internal fun mergedCast(
    seriesPeople: List<BaseItemPerson>?,
    episodePeople: List<BaseItemPerson>?,
): List<BaseItemPerson> {
    val seriesActors = seriesPeople?.filter { it.Type == "Actor" } ?: emptyList()
    val episodeActors = episodePeople?.filter { it.Type == "Actor" } ?: emptyList()
    val seen = seriesActors.map { it.Id ?: "name:" + it.Name.orEmpty() }.toMutableSet()
    return (seriesActors + episodeActors.filter { seen.add(it.Id ?: "name:" + it.Name.orEmpty()) }).take(15)
}

/** Meta-row date on a series page: the play-target episode's air date with its runtime beside it
 *  ("Aired Mar 8, 2026 · 42m"). The series premiere date holds this slot only when the episode has
 *  no date of its own, and the runtime is omitted when the episode carries none. */
internal fun metaAiredText(item: BaseItemDto, playTarget: BaseItemDto?): String? {
    val ep = playTarget?.takeIf { item.isSeries && it.Type == "Episode" }
    ep?.airDateLabel()?.let { aired ->
        return "Aired $aired" + (ep.runtimeLabel()?.let { " · $it" } ?: "")
    }
    return if (item.isSeries) item.airDateLabel()?.let { "Premiered $it" } else null
}

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onPlay: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    onTrailer: (tv.jetflix.app.data.TrailerSource) -> Unit = {},
    onActor: (personId: String, name: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    val watchlistIds by tv.jetflix.app.data.Watchlist.ids.collectAsState()
    val showWatchlist by tv.jetflix.app.data.AppSettings.showWatchlist.collectAsState()
    val watchlistFull by tv.jetflix.app.data.Watchlist.capNotice.collectAsState()
    if (watchlistFull) LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); tv.jetflix.app.data.Watchlist.clearCapNotice() }
    DetailsContent(
        state = state,
        repo = viewModel.repo,
        onToggleWatched = { viewModel.toggleWatched() },
        onToggleFavorite = { viewModel.toggleFavorite() },
        watchlisted = state.item?.Id?.let { watchlistIds.contains(it) } == true,
        showWatchlist = showWatchlist,
        watchlistFull = watchlistFull,
        onToggleWatchlist = { viewModel.toggleWatchlist() },
        onSelectSeason = { seriesId, seasonId -> viewModel.selectSeason(seriesId, seasonId) },
        onPlay = onPlay,
        onItemClick = onItemClick,
        onBack = onBack,
        onTrailer = onTrailer,
        onActor = onActor,
    )
}

@Composable
fun DetailsContent(
    state: DetailsUiState,
    repo: JellyfinRepository,
    onToggleWatched: () -> Unit,
    onSelectSeason: (String, String) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onTrailer: (tv.jetflix.app.data.TrailerSource) -> Unit = {},
    onActor: (personId: String, name: String) -> Unit = { _, _ -> },
    watchlisted: Boolean = false,
    showWatchlist: Boolean = false,
    onToggleWatchlist: () -> Unit = {},
    watchlistFull: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    tv.jetflix.app.ui.components.CountRecompose("details")
    val item = state.item
    val playFocus = remember { FocusRequester() }
    // Which cast chip (if any) had focus, so Back from an actor page lands on it without a scroll jump.
    val castFocus = tv.jetflix.app.ui.components.rememberFocusMemory()
    LaunchedEffect(item?.Id) {
        if (item != null && !castFocus.hasKey) {
            try { playFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(JetflixColors.Background),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(460.dp),
            ) {
                // The list scrolls the header up when focus moves down (season chips, episodes), leaving only the
                // header's bottom band on screen. Hold the art against that scroll so the band shows the top of the
                // art (the subject). Text and buttons still scroll normally. For a series the art (same scale and
                // top-aligned framing) carries down behind the description and season buttons and finishes fading to
                // the page background right below the season buttons.
                if (item != null) {
                    val headerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 460.dp.roundToPx() }
                    val extend = state.seasons.isNotEmpty()
                    Box(
                        Modifier
                            .layout { measurable, c ->
                                val info = listState.layoutInfo.visibleItemsInfo
                                val atTop = listState.firstVisibleItemIndex == 0
                                val shift = if (atTop) listState.firstVisibleItemScrollOffset else 0
                                val hdr = info.firstOrNull { it.key == "header" }
                                val last = info.firstOrNull { it.key == "seasons" }
                                val h = if (extend && hdr != null && last != null) {
                                    val artTop = if (atTop) 0 else hdr.offset
                                    (last.offset + last.size - artTop).coerceAtLeast(1)
                                } else (headerPx - shift).coerceAtLeast(1)
                                val pl = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth, h))
                                layout(c.maxWidth, c.maxHeight) { pl.place(0, shift) }
                            }
                            .drawWithContent {
                                drawContent()
                                // Gradient is laid out in header coordinates: the header top is `shift` above the art top.
                                val shiftPx = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0
                                val span = size.height + shiftPx
                                val hp = headerPx.toFloat()
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        (hp * 0.55f / span).coerceAtMost(1f) to JetflixColors.Background.copy(alpha = 0.35f),
                                        (hp / span).coerceAtMost(1f) to JetflixColors.Background.copy(alpha = if (extend) 0.78f else 1f),
                                        1f to JetflixColors.Background,
                                        startY = -shiftPx.toFloat(),
                                        endY = size.height,
                                    ),
                                )
                            },
                    ) {
                        ArtImage(
                            url = repo.backdropUrl(item, 1920),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            // Same scale the 460dp header always used (Crop against the header height), so a taller
                            // box shows more of the art below instead of enlarging it.
                            contentScale = remember(headerPx) {
                                object : ContentScale {
                                    override fun computeScaleFactor(srcSize: androidx.compose.ui.geometry.Size, dstSize: androidx.compose.ui.geometry.Size): androidx.compose.ui.layout.ScaleFactor {
                                        val f = maxOf(dstSize.width / srcSize.width, headerPx / srcSize.height)
                                        return androidx.compose.ui.layout.ScaleFactor(f, f)
                                    }
                                }
                            },
                            alignment = HeaderArtAlignment,
                        )
                    }
                }
                if (item != null) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 48.dp, bottom = 12.dp)
                            .fillMaxWidth(0.8f),
                    ) {
                        tv.jetflix.app.ui.components.TitleOrLogo(
                            title = item.displayTitle(),
                            logoUrl = repo.logoUrl(item),
                        )
                        Spacer(Modifier.height(8.dp))
                        MetaRow(item, state.playTarget, state.is4k, state.hdrFormats)
                        val target = state.playTarget ?: item
                        // The episode's air date sits in the meta row and the premiere date leads the genre
                        // line below (the swap settled after the genre-line pick); the button names the episode.
                        // Compact gap, as in the mock he approved - reserving the removed line's height here
                        // pushed the title and meta row too high ("the title is way too high", "the air date
                        // row shouldn't be so high"). The column is bottom-anchored, so the buttons stay put.
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onPlay(target) },
                                modifier = Modifier.focusRequester(playFocus),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black,
                                ),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(playLabel(target))
                            }
                            if (target.isResumeable) {
                                Button(
                                    onClick = { onPlay(target.fromStart()) },
                                    colors = ButtonDefaults.colors(
                                        containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                        contentColor = Color.White,
                                        focusedContainerColor = JetflixColors.SurfaceLight,
                                        focusedContentColor = Color.White,
                                    ),
                                ) {
                                    Text("Play from start")
                                }
                            }
                            state.trailer?.let { trailer ->
                                Button(
                                    onClick = { onTrailer(trailer) },
                                    colors = ButtonDefaults.colors(
                                        containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                        contentColor = Color.White,
                                        focusedContainerColor = JetflixColors.SurfaceLight,
                                        focusedContentColor = Color.White,
                                    ),
                                ) {
                                    Icon(TrailerIcon, contentDescription = "Watch trailer", modifier = Modifier.size(24.dp))
                                }
                            }
                            Button(
                                onClick = { onToggleWatched() },
                                colors = ButtonDefaults.colors(
                                    containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                    contentColor = Color.White,
                                    focusedContainerColor = JetflixColors.SurfaceLight,
                                    focusedContentColor = Color.White,
                                ),
                            ) {
                                val played = watchedToggleTarget(item, state.playTarget).UserData?.Played == true
                                Icon(
                                    if (played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                    contentDescription = if (played) "Watched. Press OK to mark unwatched" else "Mark as watched",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            // Star first, then the heart (his order): Driftfin's own Watchlist, movies and
                            // shows only (Marquee's rule). Outline = not starred, gold fill = starred.
                            if (showWatchlist && (item.Type == "Movie" || item.Type == "Series")) {
                                Button(
                                    onClick = { onToggleWatchlist() },
                                    colors = ButtonDefaults.colors(
                                        containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                        contentColor = Color.White,
                                        focusedContainerColor = JetflixColors.SurfaceLight,
                                        focusedContentColor = Color.White,
                                    ),
                                ) {
                                    Icon(
                                        if (watchlisted) Icons.Filled.Star else StarOutlineIcon,
                                        contentDescription = if (watchlisted) "In watchlist. Press OK to remove" else "Add to watchlist",
                                        tint = if (watchlisted) Color(0xFFE5C778) else LocalContentColor.current,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Button(
                                onClick = { onToggleFavorite() },
                                colors = ButtonDefaults.colors(
                                    containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                    contentColor = Color.White,
                                    focusedContainerColor = JetflixColors.SurfaceLight,
                                    focusedContentColor = Color.White,
                                ),
                            ) {
                                val fav = item.UserData?.IsFavorite == true
                                Icon(
                                    if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (fav) "In favorites. Press OK to remove" else "Add to favorites",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (watchlistFull) {
            item(key = "watchlistFull") {
                Text("Watchlist is full (500 titles). Remove one to add another.", style = JetflixType.caption, color = JetflixColors.TextMuted)
            }
        }

        item?.Overview?.let { overview ->
            item(key = "overview") {
                Text(
                    text = overview,
                    style = JetflixType.body,
                    color = JetflixColors.TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp),
                )
            }
        }

        if (item != null && item.Genres.isNotEmpty()) {
            item(key = "genres") {
                Text(
                    text = genreLine(item, state.playTarget),
                    style = JetflixType.caption,
                    color = JetflixColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }

        if (state.seasons.size > 1 && item != null) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // Center on the cross axis so the quiet status text rides on the chips' own center line.
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(state.seasons, key = { it.Id }) { season ->
                        SeasonChip(
                            season = season,
                            selected = season.Id == state.selectedSeasonId,
                            onClick = { onSelectSeason(item.Id, season.Id) },
                        )
                    }
                    // The server's own series status sits quietly at the end of the season row.
                    seriesStatusLabel(item.SeriesStatus)?.let { status ->
                        item(key = "series-status") {
                            Text(status, style = JetflixType.caption, color = JetflixColors.TextMuted, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        if (state.episodes.isNotEmpty()) {
            item(key = "episodes-title") {
                Text(
                    "Episodes",
                    style = JetflixType.sectionTitle,
                    color = JetflixColors.TextPrimary,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            items(state.episodes, key = { "ep-" + it.Id }) { ep ->
                EpisodeRow(
                    episode = ep,
                    is4k = ep.Id in state.fourKEpisodeIds,
                    thumbUrl = repo.thumbUrl(ep, 480),
                    onClick = { onPlay(ep) },
                )
            }
        }

        val cast = mergedCast(item?.People, state.playTarget?.People)
        if (cast.isNotEmpty()) {
            item(key = "cast-title") {
                Text(
                    "Cast",
                    style = JetflixType.sectionTitle,
                    color = JetflixColors.TextPrimary,
                    modifier = Modifier.padding(start = 48.dp, top = 12.dp, bottom = 8.dp),
                )
            }
            item(key = "cast") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(cast, key = { it.Id ?: it.Name ?: "" }) { person ->
                        CastChip(
                            person = person,
                            imageUrl = repo.personImageUrl(person),
                            onClick = { person.Id?.let { id -> onActor(id, person.Name.orEmpty()) } },
                            modifier = Modifier.restoreFocus(castFocus, "cast:" + (person.Id ?: person.Name.orEmpty())),
                        )
                    }
                }
            }
        }

        if (state.similar.isNotEmpty()) {
            item(key = "similar") {
                MediaRow(
                    title = "More Like This",
                    items = state.similar,
                    wide = false,
                    imageUrlFor = { repo.posterUrl(it) },
                    onItemClick = onItemClick,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        state.error?.let { err ->
            item(key = "error") {
                Text(err, style = JetflixType.body, color = JetflixColors.TextSecondary, modifier = Modifier.padding(48.dp))
            }
        }
    }
}

@Composable
private fun MetaRow(item: BaseItemDto, playTarget: BaseItemDto?, is4k: Boolean = false, hdrFormats: List<String> = emptyList()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A series with a premiere date skips the standalone year - the date already carries it (review note:
        // "no, I mean the date by itself. i like the primier and current"). The year stays for movies and
        // for a series whose server has no premiere date.
        metaYearText(item)?.let {
            Text(it, style = JetflixType.body, color = JetflixColors.TextSecondary)
        }
        // The swap settled after the genre-line pick ("premiered goes down beside the genre, aired goes up
        // top"): the play target episode's air date takes the premiere date's slot up here, and the
        // premiere date moves to the genre line. If the episode has no date, the premiere date stays
        // here as before so the date isn't lost.
        // Same swap as before, plus the requested addition: the target episode's runtime sits beside its
        // air date ("Aired Mar 8, 2026 · 42m"). No runtime on the episode - nothing invented, the
        // date shows alone. The "N Seasons" segment further right is untouched.
        val dateText = metaAiredText(item, playTarget)
        dateText?.let {
            Text(it, style = JetflixType.body, color = JetflixColors.TextSecondary)
        }
        item.OfficialRating?.takeIf { it.isNotBlank() }?.let { rating ->
            Box(
                Modifier
                    .border(1.dp, JetflixColors.TextMuted, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(rating, style = JetflixType.caption, color = JetflixColors.TextSecondary)
            }
        }
        val duration = when {
            item.isSeries -> item.ChildCount?.let { if (it == 1) "1 Season" else "$it Seasons" }
            else -> item.runtimeLabel()
        }
        duration?.let { Text(it, style = JetflixType.body, color = JetflixColors.TextSecondary) }
        item.CommunityRating?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = JetflixColors.Red,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("%.1f".format(rating), style = JetflixType.body, color = JetflixColors.TextSecondary)
            }
        }
        if (is4k) FourKTag()
        if (tv.jetflix.app.data.AppSettings.formatBadges.collectAsState().value) hdrFormats.forEach { FormatTag(it) }
    }
}

/** Small outlined format tag, same look as the 4K tag. */
@Composable
private fun FormatTag(label: String) {
    Box(Modifier.border(1.dp, JetflixColors.TextMuted, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(label, style = JetflixType.caption, color = JetflixColors.TextSecondary)
    }
}

/** Small outlined "4K" tag: this title (or episode) has a 4K version. */
@Composable
private fun FourKTag() {
    Box(
        Modifier
            .border(1.dp, JetflixColors.TextMuted, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("4K", style = JetflixType.caption, color = JetflixColors.TextSecondary)
    }
}

@Composable
private fun SeasonChip(season: BaseItemDto, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color.White else JetflixColors.Surface,
            contentColor = if (selected) Color.Black else JetflixColors.TextSecondary,
            focusedContainerColor = JetflixColors.Red,
            focusedContentColor = Color.White,
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
    ) {
        Text(season.Name ?: "Season", style = JetflixType.caption)
    }
}

@Composable
private fun EpisodeRow(episode: BaseItemDto, thumbUrl: String?, onClick: () -> Unit, is4k: Boolean = false) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = JetflixColors.Surface,
            contentColor = JetflixColors.TextPrimary,
            focusedContainerColor = JetflixColors.SurfaceLight,
            focusedContentColor = JetflixColors.TextPrimary,
        ),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(200.dp)) {
                ArtImage(
                    url = thumbUrl,
                    contentDescription = episode.Name,
                    modifier = Modifier
                        .width(200.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    ResumeProgressBar(episode.resumePercent())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        episode.episodeLabel()?.let { "$it · ${episode.Name ?: ""}" } ?: episode.Name ?: "Episode",
                        style = JetflixType.body,
                        color = JetflixColors.TextPrimary,
                        maxLines = 1,
                    )
                    episode.runtimeLabel()?.let {
                        Spacer(Modifier.width(12.dp))
                        Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted)
                    }
                    episode.airDateLabel()?.let {
                        Spacer(Modifier.width(12.dp))
                        Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted)
                    }
                    if (is4k) {
                        Spacer(Modifier.width(10.dp))
                        FourKTag()
                    }
                    if (episode.UserData?.Played == true) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Check, contentDescription = "Watched", tint = JetflixColors.Red, modifier = Modifier.size(16.dp))
                    }
                }
                // This episode's own Jellyfin summary; never the series overview. Missing data is said plainly.
                val summary = episode.Overview?.takeIf { it.isNotBlank() }
                Text(
                    summary ?: "No summary available for this episode.",
                    style = JetflixType.caption,
                    color = if (summary != null) JetflixColors.TextSecondary else JetflixColors.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun CastChip(person: BaseItemPerson, imageUrl: String?, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = person.Id != null,
        modifier = modifier.width(100.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = JetflixColors.SurfaceLight),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, JetflixColors.FocusBorder), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(5.dp)) {
            tv.jetflix.app.ui.actor.PersonPhoto(url = imageUrl, name = person.Name.orEmpty(), size = 72.dp)
            Spacer(Modifier.height(6.dp))
            Text(person.Name ?: "", style = JetflixType.caption, color = JetflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            person.Role?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The genre line on a series page leads with the play target's air date: "Aired Oct 22, 2026 · Drama · Comedy"
 * (settled after build 40 - the date leaves the header and joins this existing quiet line). Movies and
 * missing dates leave the genres alone. */
fun genreLine(item: BaseItemDto, playTarget: BaseItemDto?): String {
    val genres = item.Genres.joinToString(" · ")
    // The premiere date leads the genre line only when the episode's air date took its meta-row slot
    // (otherwise it stays up top and this line is plain genres). A little extra room between the date
    // and the first genre, on the "little, not a lot" review note.
    val epAired = playTarget?.takeIf { item.isSeries && it.Type == "Episode" }?.airDateLabel()
    val premiered = if (item.isSeries && epAired != null) item.airDateLabel() else null
    return listOfNotNull(premiered?.let { "Premiered $it" }, genres.takeIf { it.isNotBlank() }).joinToString("  ·  ")
}

/** "Returning Series" / "Ended" from the server's SeriesStatus; anything unknown shows nothing. */
fun seriesStatusLabel(status: String?): String? = when (status?.trim()?.lowercase()) {
    "continuing" -> "Returning Series"
    "ended" -> "Ended"
    "canceled", "cancelled" -> "Canceled"
    else -> null
}

/** The meta row's standalone year; dropped when a series already shows "Premiered <date>" (settled in build 40). */
fun metaYearText(item: BaseItemDto): String? =
    if (item.isSeries && item.airDateLabel() != null) null else item.ProductionYear?.toString()

/** "Resume S2:E5" / "Play S2:E5" for episodes, plain "Resume" / "Play" otherwise (movie, or the server
 * has no episode numbers). The episode is named here and nowhere else up top (settled in build 41). */
fun playLabel(target: BaseItemDto): String {
    val verb = if (target.isResumeable) "Resume" else "Play"
    val ep = target.episodeLabel()
    return if (ep != null) "$verb $ep" else verb
}

/** Where the wide backdrop sits inside the fixed header box: anchored to the top of the art (heads/titles sit high in fanart). */
val HeaderArtAlignment: Alignment = Alignment.TopCenter

/** Hollow star, for "not on the Watchlist yet": the material-icons core outlined star renders solid,
 *  so this is the Material star_border geometry (outer star with the inner star punched out). */
val StarOutlineIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(
                "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24z" +
                "M12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"
            ),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}

/** Clapperboard, for "Watch trailer". Artwork chosen from the icon options (option 1). */
val TrailerIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Trailer", 24.dp, 24.dp, 24f, 24f).apply {
        // Tilted slate; the four stripe cutouts come from the even-odd fill.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(2.734f, 6.609f); lineTo(21.211f, 2.003f); lineTo(22.028f, 5.281f); lineTo(3.552f, 9.888f); close()
                moveTo(3.321f, 5.418f); lineTo(4.891f, 5.027f); lineTo(7.308f, 9.996f); lineTo(5.737f, 10.388f); close()
                moveTo(7.940f, 4.267f); lineTo(9.510f, 3.875f); lineTo(11.927f, 8.845f); lineTo(10.356f, 9.236f); close()
                moveTo(12.559f, 3.115f); lineTo(14.129f, 2.723f); lineTo(16.546f, 7.693f); lineTo(14.975f, 8.085f); close()
                moveTo(17.178f, 1.963f); lineTo(18.748f, 1.572f); lineTo(21.165f, 6.541f); lineTo(19.594f, 6.933f); close()
            },
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        // Board body (rounded rect, separate path so the slate's even-odd fill does not punch through).
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(4.397f, 9.888f)
                horizontalLineTo(19.603f)
                arcTo(2.112f, 2.112f, 0f, false, true, 21.715f, 12.0f)
                verticalLineTo(19.181f)
                arcTo(2.112f, 2.112f, 0f, false, true, 19.603f, 21.293f)
                horizontalLineTo(4.397f)
                arcTo(2.112f, 2.112f, 0f, false, true, 2.285f, 19.181f)
                verticalLineTo(12.0f)
                arcTo(2.112f, 2.112f, 0f, false, true, 4.397f, 9.888f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        // Hinge dot.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(4.608f, 8.198f)
                arcTo(1.056f, 1.056f, 0f, true, false, 2.496f, 8.198f)
                arcTo(1.056f, 1.056f, 0f, true, true, 4.608f, 8.198f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}
