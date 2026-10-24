package tv.jetflix.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import tv.jetflix.app.ui.components.rememberFocusMemory
import tv.jetflix.app.ui.components.restoreFocus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.material3.Text
import kotlinx.coroutines.async
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

data class LibraryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<BaseItemDto> = emptyList(),
)

/** Grid of every Movie (or Series) across all libraries of that type. */
class LibraryViewModel(val repo: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state = _state.asStateFlow()

    fun load(tab: JetflixTab) {
        val session = SessionManager.session.value ?: return
        val api = repo.apiFor(session.serverUrl)
        val includeTypes = if (tab == JetflixTab.MOVIES) "Movie" else "Series"
        viewModelScope.launch {
            if (_state.value.items.isEmpty()) _state.value = LibraryUiState(loading = true)
            try {
                if (tab == JetflixTab.MY_LIST) {
                    val favs = api.getItems(
                        userId = session.userId,
                        includeItemTypes = "Movie,Series",
                        recursive = true,
                        sortBy = "SortName",
                        sortOrder = "Ascending",
                        filters = "IsFavorite",
                    ).body()?.Items ?: emptyList()
                    _state.value = LibraryUiState(loading = false, items = favs)
                    return@launch
                }
                val views = api.getUserViews(session.userId).body()?.Items ?: emptyList()
                val wanted = views.filter {
                    it.CollectionType == if (tab == JetflixTab.MOVIES) "movies" else "tvshows"
                }
                val merged = wanted.map { view ->
                    async {
                        api.getItems(
                            userId = session.userId,
                            parentId = view.Id,
                            includeItemTypes = includeTypes,
                            recursive = true,
                            sortBy = "SortName",
                            sortOrder = "Ascending",
                        ).body()?.Items ?: emptyList()
                    }
                }.flatMap { it.await() }.distinctBy { it.Id }
                _state.value = LibraryUiState(loading = false, items = merged)
            } catch (e: Exception) {
                _state.value = LibraryUiState(loading = false, error = "Couldn't load (${e.javaClass.simpleName})")
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    tab: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LibraryContent(state, viewModel.repo, tab, userName, onTabSelected, onItemClick, onSignOut)
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    repo: JellyfinRepository,
    tab: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onSignOut: () -> Unit,
) {
    val focusMemory = rememberFocusMemory()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val startedFresh = remember { !focusMemory.hasKey }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty() && startedFresh) {
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        if (state.items.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.items, key = { it.Id }) { item ->
                    PosterCard(
                        item = item,
                        imageUrl = repo.posterUrl(item),
                        modifier = if (startedFresh && item.Id == state.items.first().Id) {
                            Modifier.focusRequester(firstFocus).restoreFocus(focusMemory, item.Id)
                        } else {
                            Modifier.restoreFocus(focusMemory, item.Id)
                        },
                        onClick = { onItemClick(item) },
                        width = 160.dp,
                    )
                }
            }
        } else if (!state.loading) {
            Text(
                state.error ?: if (tab == JetflixTab.MY_LIST) "Your list is empty. Open a title and choose My List to add it." else "No ${tab.label.lowercase()} found.",
                style = JetflixType.body,
                color = JetflixColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp),
            )
        }
        TopNavBar(
            selected = tab,
            userName = userName,
            onTabSelected = onTabSelected,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
