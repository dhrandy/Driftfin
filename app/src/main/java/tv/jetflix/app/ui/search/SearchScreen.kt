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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class SearchViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search(query)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(_state.value.query) }
    }

    private suspend fun search(query: String) {
        val session = SessionManager.session.value ?: return
        if (query.isBlank()) return
        val api = repo.apiFor(session.serverUrl)
        _state.value = _state.value.copy(searching = true)
        try {
            val results = api.getItems(
                userId = session.userId,
                searchTerm = query,
                recursive = true,
                includeItemTypes = "Movie,Series",
                sortBy = "SortName",
                limit = 60,
            ).body()?.Items ?: emptyList()
            _state.value = _state.value.copy(searching = false, results = results)
        } catch (_: Exception) {
            _state.value = _state.value.copy(searching = false)
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
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
        onSignOut = onSignOut,
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
    onSignOut: () -> Unit,
) {
    val menuBack = tv.jetflix.app.ui.components.rememberMenuBack()
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

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
                state.results.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
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
                state.query.isNotBlank() && !state.searching -> {
                    Text(
                        "No matches for \"${state.query}\"",
                        style = JetflixType.body,
                        color = JetflixColors.TextSecondary,
                        modifier = Modifier.padding(48.dp),
                    )
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
            onNavFocusChange = { menuBack.navHasFocus = it },
            selected = JetflixTab.SEARCH,
            userName = userName,
            onTabSelected = onTabSelected,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
