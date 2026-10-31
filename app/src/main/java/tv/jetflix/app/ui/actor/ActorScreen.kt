package tv.jetflix.app.ui.actor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.unit.sp
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
    /** "Born Jun 1, 1926 · Died Aug 4, 1962" from the server's own dates; null when it has neither. */
    val dates: String? = null,
    val photoUrl: String? = null,
    /** Movies and series on this server that list the person, as the signed-in user sees them. */
    val titles: List<BaseItemDto> = emptyList(),
)

fun cleanBio(raw: String?): String? = raw?.replace(Regex("[ \\t]+\n"), "\n")?.trim()?.takeIf { it.isNotEmpty() }

class ActorViewModel(val repo: JellyfinRepository) : ViewModel() {
    /** Set by navigation: opens a title's details page (movies and series both land on Details). */
    var onOpenTitle: (BaseItemDto) -> Unit = {}
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
                val resp = api.getItems(userId = session.userId, includeItemTypes = "Movie,Series", recursive = true,
                    sortBy = "ProductionYear,SortName", sortOrder = "Descending", personIds = personId)
                if (!resp.isSuccessful) throw IllegalStateException("credits ${resp.code()}")
                val person = personDef.await()
                // A profile or server switch while this was loading: drop the answer, it is not for the current user.
                if (!sameSession(session)) { loadedFor = null; return@launch }
                _state.value = ActorUiState(
                    loading = false,
                    name = person?.Name?.takeIf { it.isNotBlank() } ?: fallbackName,
                    bio = cleanBio(person?.Overview),
                    dates = tv.jetflix.app.data.lifeDatesLabel(person?.PremiereDate, person?.EndDate),
                    photoUrl = person?.ImageTags?.get("Primary")?.let { repo.imageUrl(personId, "Primary", 400, it) },
                    // Top-level titles only: episodes of a matching series would duplicate the series itself.
                    titles = resp.body()?.Items.orEmpty().filter { it.Type == "Movie" || it.Type == "Series" },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                loadedFor = null
                if (!sameSession(session)) return@launch
                _state.value = ActorUiState(loading = false, name = fallbackName, error = "Couldn't load this actor's titles")
            }
        }
    }
}

/** The actor page: photo, name and bio on the left, their movies and shows on this server on the right. */
@Composable
fun ActorScreen(viewModel: ActorViewModel, personId: String, name: String) {
    LaunchedEffect(personId) { viewModel.load(personId, name) }
    val state by viewModel.state.collectAsState()
    ActorContent(state = state, imageUrlFor = { viewModel.repo.posterUrl(it) }, onOpenTitle = viewModel.onOpenTitle, onRetry = { viewModel.retry(personId, name) })
}

@Composable
fun ActorContent(state: ActorUiState, imageUrlFor: (BaseItemDto) -> String?, onOpenTitle: (BaseItemDto) -> Unit, onRetry: () -> Unit = {}) {
    val firstFocus = remember { FocusRequester() }
    var bioOpen by remember { mutableStateOf(false) }
    var bioFocused by remember { mutableStateOf(false) }
    val bioFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(JetflixColors.Background).padding(start = 48.dp, end = 32.dp, top = 40.dp, bottom = 24.dp)) {
        Column(Modifier.width(380.dp).padding(end = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonPhoto(url = state.photoUrl, name = state.name, size = 150.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(state.name, style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            state.dates?.let { Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted) }
            state.bio?.let { bio ->
                // Left from the filmography lands here; OK opens the full biography, Back returns to this spot.
                Column(
                    Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .onFocusChanged { bioFocused = it.isFocused }
                        .focusRequester(bioFocus)
                        .clickable { bioOpen = true }
                        .background(if (bioFocused) JetflixColors.SurfaceLight else Color.Transparent)
                        .padding(8.dp),
                ) {
                    Text(bio, style = JetflixType.body.copy(fontSize = 16.sp, lineHeight = 24.sp), color = JetflixColors.TextSecondary, maxLines = 14, overflow = TextOverflow.Ellipsis)
                    if (bioFocused) Text("OK: read the full biography", style = JetflixType.caption, color = JetflixColors.TextMuted)
                }
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
                state.titles.isEmpty() -> Text("No movies or shows with this actor on your server.", style = JetflixType.body, color = JetflixColors.TextSecondary)
                else -> Column {
                    LaunchedEffect(state.titles) { repeat(10) { try { firstFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
                    Text(titlesOnServerLine(state.titles), style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp, top = 20.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(state.titles, key = { it.Id }) { m ->
                            Column(Modifier.width(140.dp)) {
                                PosterCard(item = m, imageUrl = imageUrlFor(m), onClick = { onOpenTitle(m) }, width = 140.dp,
                                    modifier = if (m.Id == state.titles.first().Id) Modifier.focusRequester(firstFocus) else Modifier)
                                Text(m.displayTitle(), style = JetflixType.caption, color = JetflixColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp))
                                m.ProductionYear?.let { Text("$it", style = JetflixType.caption, color = JetflixColors.TextMuted) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (bioOpen && state.bio != null) {
        FullBioOverlay(name = state.name, bio = state.bio, onClose = {
            bioOpen = false
            try { bioFocus.requestFocus() } catch (_: Exception) {}
        })
    }
    }
}

/** The full biography in a scrollable panel: DPAD up/down scrolls, Back returns to the bio on the actor page. */
@Composable
private fun FullBioOverlay(name: String, bio: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val scroll = rememberScrollState()
    val overlayFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight(0.85f)
                .width(760.dp)
                .background(JetflixColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .onPreviewKeyEvent { ev ->
                    if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        when (ev.key) {
                            androidx.compose.ui.input.key.Key.DirectionDown -> { scope.launch { scroll.scrollBy(160f) }; true }
                            androidx.compose.ui.input.key.Key.DirectionUp -> { scope.launch { scroll.scrollBy(-160f) }; true }
                            else -> false
                        }
                    } else false
                }
                .focusRequester(overlayFocus)
                .focusable()
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(name, style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
            Column(Modifier.verticalScroll(scroll)) {
                Text(bio, style = JetflixType.body.copy(fontSize = 18.sp, lineHeight = 28.sp), color = JetflixColors.TextSecondary)
            }
        }
    }
    LaunchedEffect(Unit) { try { overlayFocus.requestFocus() } catch (_: Exception) {} }
}

/** "3 movies and 1 show on your server" - the grid holds both, so say both. */
fun titlesOnServerLine(titles: List<BaseItemDto>): String {
    val movies = titles.count { it.Type != "Series" }
    val shows = titles.size - movies
    val parts = buildList {
        if (movies > 0) add(if (movies == 1) "1 movie" else "$movies movies")
        if (shows > 0) add(if (shows == 1) "1 show" else "$shows shows")
    }
    return parts.joinToString(" and ") + " on your server"
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
