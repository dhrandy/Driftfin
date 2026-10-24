package tv.jetflix.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import tv.jetflix.app.ui.components.rememberFocusMemory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.ArtImage
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.MediaRow
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

data class HomeSection(val title: String, val items: List<BaseItemDto>, val wide: Boolean)

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val hero: BaseItemDto? = null,
    val sections: List<HomeSection> = emptyList(),
)

class HomeViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    fun load() {
        val session = SessionManager.session.value ?: return
        val api = repo.apiFor(session.serverUrl)
        viewModelScope.launch {
            if (_state.value.sections.isEmpty()) _state.value = HomeUiState(loading = true)
            try {
                val resume = async { api.getResumeItems(session.userId).body()?.Items ?: emptyList() }
                val nextUp = async { api.getNextUp(session.userId).body()?.Items ?: emptyList() }
                val latestMovies = async {
                    api.getLatest(session.userId, limit = 16, includeItemTypes = "Movie").body() ?: emptyList()
                }
                val latestEpisodes = async {
                    api.getLatest(session.userId, limit = 16, includeItemTypes = "Episode").body() ?: emptyList()
                }
                val viewsResp = api.getUserViews(session.userId)
                val views = viewsResp.body()?.Items ?: emptyList()
                val librarySections = views
                    .filter { it.CollectionType == "movies" || it.CollectionType == "tvshows" }
                    .map { view ->
                        val items = api.getItems(
                            userId = session.userId,
                            parentId = view.Id,
                            includeItemTypes = if (view.CollectionType == "movies") "Movie" else "Series",
                            recursive = true,
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            limit = 16,
                        ).body()?.Items ?: emptyList()
                        HomeSection(view.Name ?: "Library", items, wide = false)
                    }

                val myList = if (tv.jetflix.app.data.AppSettings.showMyList.value) {
                    api.getItems(
                        userId = session.userId,
                        includeItemTypes = "Movie,Series",
                        recursive = true,
                        sortBy = "DateCreated",
                        sortOrder = "Descending",
                        limit = 24,
                        filters = "IsFavorite",
                    ).body()?.Items ?: emptyList()
                } else emptyList()
                // "More like <last watched>": Jellyfin's own similar-items endpoint, nothing invented.
                val moreLike = try {
                    val last = api.getItems(
                        userId = session.userId,
                        includeItemTypes = "Movie,Series",
                        recursive = true,
                        sortBy = "DatePlayed",
                        sortOrder = "Descending",
                        limit = 1,
                        filters = "IsPlayed",
                    ).body()?.Items?.firstOrNull()
                    if (last == null) null else {
                        val sim = api.getSimilar(last.Id, session.userId, limit = 16).body()?.Items ?: emptyList()
                        if (sim.isEmpty()) null else HomeSection("More like ${last.Name}", sim, wide = false)
                    }
                } catch (_: Exception) { null }
                val resumeItems = resume.await().filter { it.isResumeable }
                val nextUpItems = nextUp.await()
                val latestMovieItems = latestMovies.await()
                val latestEpisodeItems = latestEpisodes.await()

                val sections = mutableListOf<HomeSection>()
                if (resumeItems.isNotEmpty()) sections += HomeSection("Continue Watching", resumeItems, wide = true)
                if (myList.isNotEmpty()) sections += HomeSection("My List", myList, wide = false)
                if (nextUpItems.isNotEmpty() && tv.jetflix.app.data.AppSettings.showNextUp.value) sections += HomeSection("Next Up", nextUpItems, wide = true)
                if (latestMovieItems.isNotEmpty()) sections += HomeSection("Recently Added Movies", latestMovieItems, wide = false)
                if (latestEpisodeItems.isNotEmpty()) sections += HomeSection("Recently Added Episodes", latestEpisodeItems, wide = true)
                moreLike?.let { sections += it }
                sections += librarySections.filter { it.items.isNotEmpty() }

                val hero = (resumeItems + latestMovieItems + sections.flatMap { it.items })
                    .firstOrNull { repo.backdropUrl(it) != null && (it.isMovie || it.isSeries || it.isEpisode) }

                _state.value = HomeUiState(loading = false, hero = hero, sections = sections)
            } catch (e: Exception) {
                _state.value = HomeUiState(loading = false, error = "Couldn't load your library (${e.javaClass.simpleName})")
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    HomeContent(
        state = state,
        repo = viewModel.repo,
        userName = userName,
        onTabSelected = onTabSelected,
        onItemClick = onItemClick,
        onPlay = onPlay,
        onSignOut = onSignOut,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    repo: JellyfinRepository,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
) {
    val focusMemory = rememberFocusMemory()
    val heroPlayFocus = remember { FocusRequester() }
    // First visit: land on the hero Play button. Returning: the remembered card wins.
    LaunchedEffect(state.hero?.Id) {
        if (state.hero != null && !focusMemory.hasKey) {
            try { heroPlayFocus.requestFocus() } catch (_: Exception) { }
        }
    }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "hero") {
                HeroBanner(
                    hero = state.hero,
                    backdropUrl = state.hero?.let { repo.backdropUrl(it, 1920) },
                    logoUrl = state.hero?.let { repo.logoUrl(it.Id) },
                    onPlay = onPlay,
                    onMoreInfo = onItemClick,
                    playFocus = heroPlayFocus,
                )
            }
            state.sections.forEach { section ->
                item(key = section.title) {
                    MediaRow(
                        title = section.title,
                        items = section.items,
                        wide = section.wide,
                        imageUrlFor = { item ->
                            if (section.wide) repo.backdropUrl(item, 480) else repo.posterUrl(item)
                        },
                        onItemClick = onItemClick,
                        focusMemory = focusMemory,
                    )
                }
            }
            if (!state.loading && state.sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        state.error ?: "Nothing here yet. Add some media in Jellyfin and it will show up.",
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                        modifier = Modifier.padding(48.dp),
                    )
                }
            }
        }
        TopNavBar(
            selected = JetflixTab.HOME,
            userName = userName,
            onTabSelected = onTabSelected,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
fun HeroBanner(
    hero: BaseItemDto?,
    backdropUrl: String?,
    logoUrl: String?,
    onPlay: (BaseItemDto) -> Unit,
    onMoreInfo: (BaseItemDto) -> Unit,
    playFocus: FocusRequester? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp),
    ) {
        if (backdropUrl != null) {
            ArtImage(
                url = backdropUrl,
                contentDescription = hero?.displayTitle(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF141414).copy(alpha = 0.92f),
                        0.45f to Color(0xFF141414).copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to JetflixColors.Background,
                    ),
                ),
        )
        if (hero != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 24.dp)
                    .fillMaxWidth(0.45f),
            ) {
                Text(
                    text = hero.displayTitle(),
                    style = JetflixType.heroTitle,
                    color = JetflixColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                hero.Overview?.let {
                    Text(
                        text = it,
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onPlay(hero) },
                        modifier = if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (hero.isResumeable) "Resume" else "Play")
                    }
                    Button(
                        onClick = { onMoreInfo(hero) },
                        colors = ButtonDefaults.colors(
                            containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                            contentColor = Color.White,
                            focusedContainerColor = JetflixColors.SurfaceLight,
                            focusedContentColor = Color.White,
                        ),
                    ) {
                        Text("More Info")
                    }
                }
            }
        }
    }
}
