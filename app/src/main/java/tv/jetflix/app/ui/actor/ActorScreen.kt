package tv.jetflix.app.ui.actor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import tv.jetflix.app.ui.components.ArtImage
import tv.jetflix.app.ui.components.PosterCard
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.MediaRow
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

data class ActorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val name: String = "",
    /** The person's own Jellyfin biography; null when the server has none. */
    val bio: String? = null,
    val photoUrl: String? = null,
    /** Movies on this server that list the person, as the signed-in user sees them. */
    val movies: List<BaseItemDto> = emptyList(),
)

fun cleanBio(raw: String?): String? = raw?.replace(Regex("[ \\t]+\n"), "\n")?.trim()?.takeIf { it.isNotEmpty() }

class ActorViewModel(val repo: JellyfinRepository) : ViewModel() {
    /** Set by navigation: opens a movie's details page. */
    var onOpenMovie: (BaseItemDto) -> Unit = {}
    fun retry(personId: String, fallbackName: String) { loadedFor = null; load(personId, fallbackName) }
    private val _state = MutableStateFlow(ActorUiState())
    val state = _state.asStateFlow()
    private var loadedFor: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private fun sameSession(s: tv.jetflix.app.data.Session): Boolean =
        SessionManager.session.value.let { it != null && it.serverUrl == s.serverUrl && it.userId == s.userId && it.token == s.token }

    fun load(personId: String, fallbackName: String) {
        if (loadedFor == personId) return
        loadedFor = personId
        val session = SessionManager.session.value
        loadJob?.cancel()
        if (session == null) { _state.value = ActorUiState(loading = false, name = fallbackName, error = "Not signed in"); return }
        _state.value = ActorUiState(loading = true, name = fallbackName)
        val api = repo.apiFor(session.serverUrl)
        loadJob = viewModelScope.launch {
            try {
                val personDef = async { try { api.getItem(personId, session.userId).body() } catch (_: Exception) { null } }
                val resp = api.getItems(userId = session.userId, includeItemTypes = "Movie", recursive = true,
                    sortBy = "ProductionYear,SortName", sortOrder = "Descending", personIds = personId)
                if (!resp.isSuccessful) throw IllegalStateException("credits ${resp.code()}")
                val person = personDef.await()
                // A profile or server switch while this was loading: drop the answer, it is not for the current user.
                if (!sameSession(session)) { loadedFor = null; return@launch }
                _state.value = ActorUiState(
                    loading = false,
                    name = person?.Name?.takeIf { it.isNotBlank() } ?: fallbackName,
                    bio = cleanBio(person?.Overview),
                    photoUrl = person?.ImageTags?.get("Primary")?.let { repo.imageUrl(personId, "Primary", 400, it) },
                    movies = resp.body()?.Items.orEmpty(),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                loadedFor = null
                if (!sameSession(session)) return@launch
                _state.value = ActorUiState(loading = false, name = fallbackName, error = "Couldn't load this actor's movies")
            }
        }
    }
}

/** The actor page: photo, name and bio on the left, the movies on this server on the right. */
@Composable
fun ActorScreen(viewModel: ActorViewModel, personId: String, name: String) {
    LaunchedEffect(personId) { viewModel.load(personId, name) }
    val state by viewModel.state.collectAsState()
    ActorContent(state = state, imageUrlFor = { viewModel.repo.posterUrl(it) }, onOpenMovie = viewModel.onOpenMovie, onRetry = { viewModel.retry(personId, name) })
}

@Composable
fun ActorContent(state: ActorUiState, imageUrlFor: (BaseItemDto) -> String?, onOpenMovie: (BaseItemDto) -> Unit, onRetry: () -> Unit = {}) {
    val firstFocus = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().background(JetflixColors.Background).padding(start = 48.dp, end = 32.dp, top = 40.dp, bottom = 24.dp)) {
        Column(Modifier.width(300.dp).padding(end = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonPhoto(url = state.photoUrl, name = state.name, size = 150.dp)
            Text(state.name, style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            state.bio?.let {
                Text(it, style = JetflixType.caption, color = JetflixColors.TextSecondary, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.loading -> Text("Loading...", style = JetflixType.body, color = JetflixColors.TextSecondary)
                state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.error, style = JetflixType.body, color = JetflixColors.TextSecondary)
                    androidx.tv.material3.Button(onClick = onRetry, modifier = Modifier.focusRequester(firstFocus)) { Text("Try again") }
                    LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
                }
                state.movies.isEmpty() -> Text("No movies with this actor on your server.", style = JetflixType.body, color = JetflixColors.TextSecondary)
                else -> Column {
                    LaunchedEffect(state.movies) { repeat(10) { try { firstFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
                    Text(if (state.movies.size == 1) "1 movie on your server" else "${state.movies.size} movies on your server", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(state.movies, key = { it.Id }) { m ->
                            Column(Modifier.width(140.dp)) {
                                PosterCard(item = m, imageUrl = imageUrlFor(m), onClick = { onOpenMovie(m) }, width = 140.dp,
                                    modifier = if (m.Id == state.movies.first().Id) Modifier.focusRequester(firstFocus) else Modifier)
                                Text(m.displayTitle(), style = JetflixType.caption, color = JetflixColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                m.ProductionYear?.let { Text("$it", style = JetflixType.caption, color = JetflixColors.TextMuted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Round photo, or the person's first letter on a grey disc when the server has no photo. */
@Composable
fun PersonPhoto(url: String?, name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    if (url != null) {
        ArtImage(url = url, contentDescription = name, modifier = modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(CircleShape).background(JetflixColors.SurfaceLight), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "?", style = JetflixType.sectionTitle, color = JetflixColors.TextSecondary)
        }
    }
}
