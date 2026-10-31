package tv.jetflix.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.PosterCard
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<BaseItemDto> = emptyList(),
    /** Actors whose name matches the query. */
    val people: List<BaseItemDto> = emptyList(),
    /** "Recommended Movies"/"Recommended Shows" rows for the idle page, posters only. */
    val recommendations: List<Pair<String, List<BaseItemDto>>> = emptyList(),
    /** The last search request failed; shown as a note instead of silently keeping or clearing results. */
    val searchFailed: Boolean = false,
)

class SearchViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init { loadRecommendations() }

    /** Idle-page rows: Jellyfin's own suggestions when the server has them, else the top-rated unwatched with posters. */
    fun loadRecommendations() {
        if (!tv.jetflix.app.data.AppSettings.searchRecommendations.value) {
            _state.value = _state.value.copy(recommendations = emptyList())
            return
        }
        val session = SessionManager.session.value ?: return
        viewModelScope.launch {
            val api = repo.apiFor(session.serverUrl)
            val movies = recommend(api, session.userId, "Movie")
            val shows = recommend(api, session.userId, "Series")
            _state.value = _state.value.copy(
                recommendations = listOfNotNull(
                    movies.takeIf { it.isNotEmpty() }?.let { "Recommended Movies" to it },
                    shows.takeIf { it.isNotEmpty() }?.let { "Recommended Shows" to it },
                ),
            )
        }
    }

    private suspend fun recommend(api: tv.jetflix.app.data.JellyfinApi, userId: String, type: String): List<BaseItemDto> {
        val suggested = try {
            api.getSuggestions(userId = userId, type = type, limit = 24).body()?.Items
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        if (!suggested.isNullOrEmpty()) return suggested.filter { repo.posterUrl(it) != null }.take(20)
        val fallback = try {
            api.getItems(
                userId = userId, includeItemTypes = type, recursive = true,
                sortBy = "CommunityRating", sortOrder = "Descending", limit = 40, filters = "IsUnplayed",
            ).body()?.Items
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        return (fallback ?: emptyList()).filter { repo.posterUrl(it) != null }.take(20)
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, searchFailed = false)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), people = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search(query)
        }
    }

    /** Empties the bar and results for a fresh visit after Search was fully left. The idle
     * recommendations reload with it: the reset state starts without them, and leaving them
     * empty made the rows vanish on every later visit (The user on build 40: "go back off and go
     * back then the recommended movies and shows are not there"). */
    fun clear() {
        searchJob?.cancel()
        _state.value = SearchUiState()
        loadRecommendations()
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(_state.value.query) }
    }

    private suspend fun search(query: String) {
        val session = SessionManager.session.value ?: return
        if (query.isBlank()) return
        val api = repo.apiFor(session.serverUrl)
        _state.value = _state.value.copy(searching = true, searchFailed = false)
        try {
            val people = try {
                api.searchPersons(userId = session.userId, searchTerm = query).body()?.Items.orEmpty().filter { it.Name != null }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val resp = api.getItems(
                userId = session.userId,
                searchTerm = query,
                recursive = true,
                includeItemTypes = "Movie,Series",
                sortBy = "SortName",
                limit = 60,
            )
            if (!resp.isSuccessful) throw java.io.IOException("search HTTP " + resp.code())
            val results = resp.body()?.Items ?: emptyList()
            _state.value = _state.value.copy(searching = false, results = results, people = people)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            // A failed search must say so (like Home's load error), not silently keep or drop results (Todd row 86).
            _state.value = _state.value.copy(searching = false, results = emptyList(), people = emptyList(), searchFailed = true)
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onUserClick: () -> Unit,
    onActorClick: (String, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    SearchContent(
        state = state,
        repo = viewModel.repo,
        userName = userName,
        onQueryChange = viewModel::onQueryChange,
        onSearchNow = { viewModel.searchNow() },
        onTabSelected = onTabSelected,
        onItemClick = onItemClick,
        onUserClick = onUserClick,
        onActorClick = onActorClick,
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    repo: JellyfinRepository,
    userName: String?,
    onQueryChange: (String) -> Unit,
    onSearchNow: () -> Unit,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onUserClick: () -> Unit,
    onActorClick: (String, String) -> Unit = { _, _ -> },
) {
    // Back from Search goes straight to the page you came from (handled by the app), not to the menu first.
    val menuBack = remember { tv.jetflix.app.ui.components.MenuBack() }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstActorFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val hasActors = state.people.isNotEmpty()
    // Captured at composition: arriving by a tab sweep keeps focus on the tab until Down or OK (NavSweep is
    // cleared by the handoff, so reading it live here races and either steals focus or defers it forever).
    val openedBySweep = remember { tv.jetflix.app.ui.components.NavSweep.active }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!openedBySweep) runCatching { fieldFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        Column(Modifier.fillMaxSize().padding(top = 96.dp)) {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = JetflixType.body.copy(color = JetflixColors.TextPrimary),
                cursorBrush = SolidColor(JetflixColors.Red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchNow() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .focusProperties { up = menuBack.focus; if (hasActors) down = firstActorFocus }
                    .focusRequester(fieldFocus)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .background(JetflixColors.SurfaceLight, RoundedCornerShape(4.dp))
                    .border(
                        width = if (fieldFocused) 2.dp else 1.dp,
                        color = if (fieldFocused) JetflixColors.FocusBorder else JetflixColors.TextMuted,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            when {
                state.results.isNotEmpty() || state.people.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (state.people.isNotEmpty()) {
                            item(key = "actors", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column {
                                    Text("Actors", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
                                    // The focused chip grows (1.08x) past its slot; the horizontal padding keeps its
                                    // outline from clipping at the row's left and right edges (the posters below get
                                    // the same room from the grid's own content padding).
                                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                                        state.people.forEachIndexed { idx, p -> item(key = p.Id) {
                                          Box(if (idx == 0) Modifier.focusRequester(firstActorFocus) else Modifier) {
                                            tv.jetflix.app.ui.details.CastChip(
                                                person = tv.jetflix.app.data.BaseItemPerson(Name = p.Name, Id = p.Id, PrimaryImageTag = p.ImageTags["Primary"]),
                                                imageUrl = repo.personImageUrl(tv.jetflix.app.data.BaseItemPerson(Name = p.Name, Id = p.Id, PrimaryImageTag = p.ImageTags["Primary"])),
                                                onClick = { onActorClick(p.Id, p.Name.orEmpty()) },
                                            )
                                          }
                                        } }
                                    }
                                }
                            }
                        }
                        items(state.results, key = { it.Id }) { item ->
                            PosterCard(
                                item = item,
                                imageUrl = repo.posterUrl(item),
                                onClick = { onItemClick(item) },
                                width = 160.dp,
                            )
                        }
                    }
                }
                state.query.isNotBlank() && !state.searching && state.searchFailed -> {
                    Text(
                        "Search failed. Check the server and try again.",
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                        modifier = Modifier.padding(48.dp),
                    )
                }
                state.query.isNotBlank() && !state.searching -> {
                    Text(
                        "No matches for \"${state.query}\"",
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                        modifier = Modifier.padding(48.dp),
                    )
                }
                state.recommendations.isNotEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        state.recommendations.forEach { (title, items) ->
                            tv.jetflix.app.ui.components.MediaRow(
                                title = title,
                                items = items,
                                wide = false,
                                imageUrlFor = { repo.posterUrl(it) },
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "Search your Jellyfin library",
                        style = JetflixType.body,
                        color = JetflixColors.TextMuted,
                        modifier = Modifier.padding(48.dp),
                    )
                }
            }
        }
        TopNavBar(
            solid = true,
            selectedFocus = menuBack.focus,
            downFocus = fieldFocus,
            onNavFocusChange = { menuBack.navHasFocus = it },
            selected = JetflixTab.SEARCH,
            userName = userName,
            onTabSelected = onTabSelected,
            onUserClick = onUserClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
