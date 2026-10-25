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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import tv.jetflix.app.data.fromStart
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import tv.jetflix.app.ui.components.rememberFocusMemory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyColumn
import tv.jetflix.app.ui.components.fadeUnderMenuBar
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
import tv.jetflix.app.ui.components.blurIfSupported
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.MediaRow
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/** Which banner title is showing. Cycles through the list and wraps. */
object HeroRotation {
    const val MAX_TITLES = 6
    const val INTERVAL_MS = 8_000L
    fun next(index: Int, size: Int): Int = if (size <= 1) 0 else (index + 1) % size
}

data class HomeSection(val title: String, val items: List<BaseItemDto>, val wide: Boolean)

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val hero: BaseItemDto? = null,
    /** Titles the banner cycles through; [hero] is the first. Empty means just [hero]. */
    val heroes: List<BaseItemDto> = emptyList(),
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
                    .filter { tv.jetflix.app.ui.library.isVideoLibrary(it) }
                    .map { view ->
                        val items = api.getItems(
                            userId = session.userId,
                            parentId = view.Id,
                            includeItemTypes = when (view.CollectionType) { "movies" -> "Movie"; "tvshows" -> "Series"; else -> "Movie,Series" },
                            recursive = true,
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            limit = 16,
                        ).body()?.Items ?: emptyList()
                        HomeSection(view.Name ?: "Library", items, wide = false)
                    }.filter { it.items.isNotEmpty() }

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
                        fields = "Genres",
                        filters = "IsPlayed",
                    ).body()?.Items?.firstOrNull()
                    if (last == null) null else {
                        val sim = tv.jetflix.app.data.RelatedItems.filter(last, api.getSimilar(last.Id, session.userId, limit = 24).body()?.Items ?: emptyList()).take(16)
                        if (sim.isEmpty()) null else HomeSection("More like ${last.Name}", sim, wide = false)
                    }
                } catch (_: Exception) { null }
                val resumeItems = resume.await().filter { it.isResumeable }
                val nextUpItems = nextUp.await()
                val latestMovieItems = latestMovies.await()
                val latestEpisodeItems = latestEpisodes.await()

                val sections = orderHomeSections(
                    resumeItems, nextUpItems.takeIf { tv.jetflix.app.data.AppSettings.showNextUp.value } ?: emptyList(),
                    latestMovieItems, myList, latestEpisodeItems, moreLike, librarySections,
                ).toMutableList()

                // Banner: unwatched movies from this library that rate highest (Jellyfin CommunityRating, 7.0 or more).
                // If none reach 7.0 the best-rated unwatched movies are used; if there are no unwatched movies there is no banner.
                val unwatchedMovies = try {
                    api.getItems(
                        userId = session.userId,
                        includeItemTypes = "Movie",
                        recursive = true,
                        sortBy = "CommunityRating",
                        sortOrder = "Descending",
                        limit = 60,
                        fields = "Overview,Genres",
                        filters = "IsUnplayed",
                    ).body()?.Items ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val heroes = tv.jetflix.app.data.BannerPicks.topUnwatchedMovies(unwatchedMovies, hasBackdrop = { repo.backdropUrl(it) != null }, count = HeroRotation.MAX_TITLES)
                val hero = heroes.firstOrNull()

                _state.value = HomeUiState(loading = false, hero = hero, heroes = heroes, sections = sections)
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

/**
 * Focus scrolling for Home: a focused child is brought fully into view and never left under the menu bar
 * ([topInsetPx] covers the bar plus the row title above the cards).
 */
@androidx.compose.foundation.ExperimentalFoundationApi
class MenuAwareScrollSpec(
    private val topInsetPx: Float,
    private val bottomMarginPx: Float = 0f,
    /** Where the banner currently ends on screen, or null when it is not on screen. Focus inside the banner scrolls the list back to the top. */
    private val bannerBottom: () -> Float? = { null },
    private val scrolledBy: () -> Float = { 0f },
) : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // The focus zoom and outline grow past the card bounds, so keep a margin below it inside the screen.
        val heroEnd = bannerBottom()
        // A button inside the banner: show the whole banner (title, synopsis, buttons), not just the button.
        if (heroEnd != null && offset + size <= heroEnd + 1f) return -scrolledBy()
        val bottom = offset + size + bottomMarginPx
        return when {
            offset < topInsetPx -> offset - topInsetPx
            bottom > containerSize -> minOf(bottom - containerSize, offset - topInsetPx)
            else -> 0f
        }
    }
}

/** Home row order: Continue Watching, Up Next, Recently Added Movies, then Favorites, Recently Added Episodes, library rows, and More like X last. */
fun orderHomeSections(
    resume: List<BaseItemDto>, nextUp: List<BaseItemDto>, latestMovies: List<BaseItemDto>, favorites: List<BaseItemDto>,
    latestEpisodes: List<BaseItemDto>, moreLike: HomeSection?, library: List<HomeSection>,
): List<HomeSection> {
    val out = mutableListOf<HomeSection>()
    if (resume.isNotEmpty()) out += HomeSection("Continue Watching", resume, wide = true)
    if (nextUp.isNotEmpty()) out += HomeSection("Next Up", nextUp, wide = true)
    if (latestMovies.isNotEmpty()) out += HomeSection("Recently Added Movies", latestMovies, wide = false)
    if (favorites.isNotEmpty()) out += HomeSection("Favorites", favorites, wide = false)
    if (latestEpisodes.isNotEmpty()) out += HomeSection("Recently Added Episodes", latestEpisodes, wide = true)
    out += library.filter { it.items.isNotEmpty() }
    moreLike?.let { out += it } // recommendations always sit at the very bottom
    return out
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    repo: JellyfinRepository,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
    restoredFocusKey: String? = null,
    previewFocusedItem: BaseItemDto? = null,
    previewScrollIndex: Int = 0,
    heroIntervalMs: Long = HeroRotation.INTERVAL_MS,
) {
    val focusMemory = rememberFocusMemory(restoredFocusKey)
    val heroPlayFocus = remember { FocusRequester() }
    val navFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = previewScrollIndex)
    val scope = rememberCoroutineScope()
    val heroes = state.heroes.ifEmpty { listOfNotNull(state.hero) }
    var heroIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val currentHero = heroes.getOrNull(heroIndex) ?: state.hero
    // The banner moves to the next title every few seconds while it is on screen; it holds still while you browse the rows.
    LaunchedEffect(heroes.size, focusMemory.key == null) {
        if (heroes.size > 1 && focusMemory.key == null) {
            while (true) {
                delay(heroIntervalMs)
                heroIndex = HeroRotation.next(heroIndex, heroes.size)
            }
        }
    }
    val navSolid by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 }
    }
    val focusedItemState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<BaseItemDto?>(previewFocusedItem) }
    var focusedItem by focusedItemState
    var heroHasFocus by androidx.compose.runtime.mutableStateOf(false)
    // Coming back up to the banner (or the menu) shows the whole banner again, title and synopsis included.
    // The focus scroll for the Play button would otherwise stop with the banner's title above the screen, so the banner
    // is corrected here: once the banner has focus, the list goes back to the very top.
    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus) {
            repeat(3) {
                delay(if (it == 0) 80L else 250L)
                if (heroHasFocus && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) listState.animateScrollToItem(0)
            }
        }
    }
    suspend fun focusHero() {
        // The first request can land before the button is attached or the window has focus; retry until it sticks.
        repeat(20) {
            try { heroPlayFocus.requestFocus() } catch (_: Exception) { }
            delay(60)
            if (heroHasFocus) return
        }
    }
    // First visit: land on the hero Play button. Returning: the remembered card wins.
    LaunchedEffect(state.hero?.Id) {
        if (state.hero != null && !focusMemory.hasKey) focusHero()
    }
    // Returning from playback: the remembered card may be gone (Next Up and Continue Watching change after
    // watching) or scrolled out of composition. If focus was not restored, fall back to the hero instead of
    // leaving the screen with nothing focused.
    LaunchedEffect(state.sections.size, state.hero?.Id) {
        if (focusMemory.pending && focusMemory.key != null) {
            delay(1200)
            if (focusMemory.pending) {
                focusMemory.pending = false
                focusMemory.key = null
                listState.scrollToItem(0)
                focusHero()
            }
        }
    }
    // Back from deep in the rows goes back to the top of Home first; the next Back exits.
    BackHandler(enabled = focusMemory.key != null) {
        focusMemory.key = null
        scope.launch {
            listState.scrollToItem(0)
            focusHero()
        }
    }
    // The details strip takes its own space under the rows so it never covers the focused row.
    // Only changes (and so only recomposes Home) when the nav bar is solid and the art really differs.
    val navArtUrl by androidx.compose.runtime.remember(repo, heroes, state.hero) {
        androidx.compose.runtime.derivedStateOf {
            (if (navSolid) focusedItemState.value?.let { repo.backdropUrl(it, 960) } else null)
                ?: (heroes.getOrNull(heroIndex) ?: state.hero)?.let { repo.backdropUrl(it, 960) }
        }
    }
    val showStrip = false // Randy: no description under the rows; the synopsis lives in More Info
    val stripSpace by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showStrip) 112.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "stripSpace",
    )
    // Focus scrolling keeps the focused card AND its row title below the menu bar (default scrolling stops at the screen edge).
    val topInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 124.dp.toPx() }
    val bottomMarginPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val spec = remember(topInsetPx, bottomMarginPx) {
        MenuAwareScrollSpec(
            topInsetPx, bottomMarginPx,
            bannerBottom = { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" }?.let { (it.offset + it.size).toFloat() } },
            scrolledBy = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f },
        )
    }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(bottom = stripSpace)
                .fadeUnderMenuBar(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "hero") {
                val currentHero = heroes.getOrNull(heroIndex) ?: state.hero
                val heroBackdrop = currentHero?.let { repo.backdropUrl(it, 1920) }
                Box(Modifier.fillMaxWidth()) {
                    // Blurred, colour-matched copy of the featured title surrounds the inset hero and sits behind the menu.
                    if (heroBackdrop != null) {
                        androidx.compose.animation.Crossfade(
                            targetState = heroBackdrop,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 800),
                            label = "heroBlur",
                            modifier = Modifier.matchParentSize().clipToBounds(),
                        ) { url ->
                            ArtImage(url = tv.jetflix.app.ui.components.blurSource(url), contentDescription = null, modifier = Modifier.fillMaxSize().then(Modifier.blurIfSupported(36.dp)), contentScale = ContentScale.Crop)
                        }
                        Box(Modifier.matchParentSize().background(Color(0xFF141414).copy(alpha = 0.35f)))
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(0.7f to Color.Transparent, 1f to JetflixColors.Background),
                            ),
                        )
                    }
                    HeroBanner(
                        hero = currentHero,
                        backdropUrl = heroBackdrop,
                        logoUrl = currentHero?.let { repo.logoUrl(it.Id) },
                        onPlay = onPlay,
                        onMoreInfo = onItemClick,
                        playFocus = heroPlayFocus,
                        upFocus = navFocus,
                        downFocus = if (state.sections.isNotEmpty()) firstRowFocus else null,
                        onFocused = { focusMemory.key = null },
                        onFocusChange = { heroHasFocus = it },
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 10.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                    )
                }
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
                        onItemFocused = { focusedItem = it },
                        firstItemFocus = if (section === state.sections.first()) firstRowFocus else null,
                        upFocus = if (section === state.sections.first() && state.hero != null) heroPlayFocus else null,
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
        }
        if (showStrip) {
            // Read inside its own scope so a focus move does not recompose all of Home.
            FocusedStripHost(focusedItemState, Modifier.align(Alignment.BottomStart))
        }
        TopNavBar(
            selected = JetflixTab.HOME,
            userName = userName,
            onTabSelected = onTabSelected,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopCenter),
            downFocus = if (state.hero != null) heroPlayFocus else if (state.sections.isNotEmpty()) firstRowFocus else null,
            selectedFocus = navFocus,
            solid = navSolid,
            blurArtUrl = navArtUrl,
        )
    }
}

@Composable
private fun FocusedStripHost(item: androidx.compose.runtime.State<BaseItemDto?>, modifier: Modifier) {
    item.value?.let { FocusedDetails(it, modifier) }
}

@Composable
fun HeroBanner(
    hero: BaseItemDto?,
    backdropUrl: String?,
    logoUrl: String?,
    onPlay: (BaseItemDto) -> Unit,
    onMoreInfo: (BaseItemDto) -> Unit,
    playFocus: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 420.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (backdropUrl != null) {
            // Slow cross-fade between featured titles; the buttons are outside it so focus is never disturbed.
            androidx.compose.animation.Crossfade(
                targetState = backdropUrl,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 800),
                label = "heroBackdrop",
                modifier = Modifier.fillMaxSize(),
            ) { url ->
                ArtImage(
                    url = url,
                    contentDescription = hero?.displayTitle(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
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
                // The banner is "focused" while any of its buttons is. Each button reports for itself, because a
                // Row with no focus target of its own did not report focus gained by its children.
                val buttonFocus = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
                val reportFocus: (String) -> (androidx.compose.ui.focus.FocusState) -> Unit = { name ->
                    { st ->
                        buttonFocus[name] = st.isFocused
                        val any = buttonFocus.values.any { it }
                        onFocusChange(any)
                        if (st.isFocused) onFocused()
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .focusProperties {
                            if (upFocus != null) up = upFocus
                            if (downFocus != null) down = downFocus
                        },
                ) {
                    Button(
                        onClick = { onPlay(hero) },
                        modifier = (if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier).onFocusChanged(reportFocus("play")),
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
                    if (hero.isResumeable) {
                        Button(
                            onClick = { onPlay(hero.fromStart()) },
                            modifier = Modifier.onFocusChanged(reportFocus("start")),
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
                    Button(
                        onClick = { onMoreInfo(hero) },
                        modifier = Modifier.onFocusChanged(reportFocus("more")),
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


/** Details for the focused title, shown along the bottom while browsing rows: genre, year, rating, length and synopsis. */
@Composable
fun FocusedDetails(item: BaseItemDto, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(JetflixColors.Background)
            .padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Text(item.displayTitle(), style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(
            item.ProductionYear?.toString(),
            item.OfficialRating,
            item.runtimeLabel(),
            item.Genres.take(3).joinToString(" • ").ifEmpty { null },
        ).joinToString("  |  ")
        if (meta.isNotEmpty()) Text(meta, style = JetflixType.body, color = JetflixColors.TextSecondary, maxLines = 1)
        item.Overview?.let { Text(it, style = JetflixType.body, color = JetflixColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
