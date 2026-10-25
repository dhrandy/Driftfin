package tv.jetflix.app.ui.details

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
import tv.jetflix.app.data.fromStart
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.ArtImage
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
)

class DetailsViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val state = _state.asStateFlow()

    private var allEpisodes: List<BaseItemDto> = emptyList()
    private var seriesTarget: BaseItemDto? = null
    private var firstLoad = true

    fun load(itemId: String, focusEpisodeId: String? = null) {
        // The tapped episode only decides the first open; coming back from the player re-picks from fresh progress.
        val focusId = if (firstLoad) focusEpisodeId else null
        firstLoad = false
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
                val trailerDef = async {
                    // Jellyfin's own list of trailer files, then the YouTube links on the item. A failed lookup just hides the button.
                    val local = if ((item.LocalTrailerCount ?: 0) > 0) {
                        try { api.getLocalTrailers(itemId, session.userId).body() ?: emptyList() } catch (_: Exception) { emptyList() }
                    } else emptyList()
                    tv.jetflix.app.data.Trailers.pick(item, local)
                }
                val similarDef = async {
                    tv.jetflix.app.data.RelatedItems.filter(item, api.getSimilar(itemId, session.userId).body()?.Items ?: emptyList())
                }
                if (!item.isSeries) {
                    // Movies show the page as soon as the title itself arrives; "More like this" fills in when ready.
                    _state.value = DetailsUiState(loading = false, item = item, playTarget = item)
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
                    api.getEpisodes(item.Id, session.userId, fields = "Overview", limit = 2000).body()?.Items ?: emptyList()
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

    fun toggleWatched() {
        val session = SessionManager.session.value ?: return
        val item = _state.value.item ?: return
        val api = repo.apiFor(session.serverUrl)
        viewModelScope.launch {
            try {
                val updated = if (item.UserData?.Played == true) {
                    api.markUnplayed(item.Id, session.userId).body()
                } else {
                    api.markPlayed(item.Id, session.userId).body()
                }
                if (updated != null) {
                    _state.value = _state.value.copy(item = item.copy(UserData = updated))
                }
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onPlay: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    onTrailer: (tv.jetflix.app.data.TrailerSource) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    DetailsContent(
        state = state,
        repo = viewModel.repo,
        onToggleWatched = { viewModel.toggleWatched() },
        onToggleFavorite = { viewModel.toggleFavorite() },
        onSelectSeason = { seriesId, seasonId -> viewModel.selectSeason(seriesId, seasonId) },
        onPlay = onPlay,
        onItemClick = onItemClick,
        onBack = onBack,
        onTrailer = onTrailer,
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
) {
    val item = state.item
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item?.Id) {
        if (item != null) {
            try { playFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    LazyColumn(
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
                if (item != null) {
                    ArtImage(
                        url = repo.backdropUrl(item, 1920),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to JetflixColors.Background.copy(alpha = 0.35f),
                                1f to JetflixColors.Background,
                            ),
                        ),
                )
                if (item != null) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 48.dp, bottom = 12.dp)
                            .fillMaxWidth(0.8f),
                    ) {
                        Text(
                            text = item.displayTitle(),
                            style = JetflixType.heroTitle,
                            color = JetflixColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        MetaRow(item)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val target = state.playTarget ?: item
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
                                    Text("Trailer")
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
                                if (item.UserData?.Played == true) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (item.UserData?.Played == true) "Watched" else "Mark Watched")
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
                                if (item.UserData?.IsFavorite == true) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (item.UserData?.IsFavorite == true) "In Favorites" else "Add to Favorites")
                            }
                        }
                    }
                }
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
                    text = item.Genres.joinToString(" · "),
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
                ) {
                    items(state.seasons, key = { it.Id }) { season ->
                        SeasonChip(
                            season = season,
                            selected = season.Id == state.selectedSeasonId,
                            onClick = { onSelectSeason(item.Id, season.Id) },
                        )
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
                    thumbUrl = repo.thumbUrl(ep, 480),
                    onClick = { onPlay(ep) },
                )
            }
        }

        val cast = item?.People?.filter { it.Type == "Actor" }?.take(15) ?: emptyList()
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
                        CastChip(person = person, imageUrl = repo.personImageUrl(person))
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
private fun MetaRow(item: BaseItemDto) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.ProductionYear?.let {
            Text("$it", style = JetflixType.body, color = JetflixColors.TextSecondary)
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
private fun EpisodeRow(episode: BaseItemDto, thumbUrl: String?, onClick: () -> Unit) {
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

@Composable
private fun CastChip(person: BaseItemPerson, imageUrl: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        ArtImage(
            url = imageUrl,
            contentDescription = person.Name,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(person.Name ?: "", style = JetflixType.caption, color = JetflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        person.Role?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Resume S2:E5" / "Play S2:E5" for episodes, plain "Resume" / "Play" otherwise. */
fun playLabel(target: BaseItemDto): String {
    val verb = if (target.isResumeable) "Resume" else "Play"
    val ep = target.episodeLabel()
    return if (ep != null) "$verb $ep" else verb
}
