package tv.jetflix.phone.ui

import androidx.compose.foundation.layout.size
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.Sort

/** The sort and watched filter are remembered between sessions, per kind (movies or shows), like the TV app. */
private class BrowsePrefs(context: Context, private val kind: String) {
    private val p = context.getSharedPreferences("driftfin_phone_browse", Context.MODE_PRIVATE)
    private val user get() = tv.jetflix.phone.data.SessionManager.session.value?.userId.orEmpty()
    var library: String?
        get() = p.getString("$user:$kind.library", null)
        set(v) { if (v == null) p.edit().remove("$user:$kind.library").apply() else p.edit().putString("$user:$kind.library", v).apply() }
    var sort: Sort
        get() = Sort.values().firstOrNull { it.name == p.getString("$kind.sort", null) } ?: Sort.Added
        set(v) { p.edit().putString("$kind.sort", v.name).apply() }
    var genres: Set<String>
        get() = p.getString("$kind.genre", null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        set(v) { p.edit().putString("$kind.genre", v.joinToString(",")).apply() }
    var unwatched: Boolean
        get() = p.getBoolean("$kind.unwatched", false)
        set(v) { p.edit().putBoolean("$kind.unwatched", v).apply() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(type: String, title: String, onOpen: (BaseItemDto) -> Unit, initialLibraryId: String? = null) {
    val prefs = remember { BrowsePrefs(Graph.appContext(), type) }
    var sort by remember { mutableStateOf(prefs.sort) }
    var unwatched by remember { mutableStateOf(prefs.unwatched) }
    var pickedGenres by remember { mutableStateOf(prefs.genres) }
    var libraryId by remember { mutableStateOf(initialLibraryId ?: prefs.library) }
    var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var libraryMenu by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var genreMenu by remember { mutableStateOf(false) }
    // The Library picker only offers libraries that fit this tab: shows never appear under Movies (reported 10/29).
    var librariesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(type) {
        // type can be "Movie,Series" (a library page that holds both): match on either kind.
        libraries = Content.libraries().filter { lib -> type.split(",").any { t -> tv.jetflix.phone.data.matchesKind(lib, t) } }
        // A stored pick from before this split (say Shows under Movies) is dropped, never queried.
        if (libraryId != null && libraries.none { it.Id == libraryId }) { libraryId = null; prefs.library = null }
        librariesLoaded = true
    }
    // Genre choices follow the picked library: a Kids library only offers its own genres.
    // True from the start unless a stored pick still needs its library check; keying on this
    // derived flag (not librariesLoaded) keeps the no-pick case to a single fetch.
    val libraryChecked by remember { derivedStateOf { libraryId == null || librariesLoaded } }
    LaunchedEffect(type, libraryId, libraryChecked) {
        if (!libraryChecked) return@LaunchedEffect
        genres = Content.genres(type, libraryId)
        val kept = pickedGenres.intersect(genres.toSet())
        if (kept != pickedGenres) { pickedGenres = kept; prefs.genres = kept }
    }
    var items by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val grid: LazyGridState = rememberLazyGridState()
    LaunchedEffect(type, sort, unwatched, pickedGenres, libraryId, libraryChecked) {
        // A stored pick waits for its library check so a stale cross-kind id is never queried.
        if (!libraryChecked) return@LaunchedEffect
        items = Content.browse(type, sort, unwatched, pickedGenres, libraryId)
    }
    // The frosted bar blurs the cover nearest the top of the page.
    LaunchedEffect(grid, items) {
        snapshotFlow { grid.firstVisibleItemIndex }.collect { i ->
            val list = items.orEmpty()
            TopArt.url.value = list.getOrNull((i - 1).coerceAtLeast(0) * 3)?.let { Graph.repo.backdropUrl(it, 960) }
        }
    }
    // Snapshot the rows for this composition so the grid's interval content reads one
    // fixed list instead of state that can change mid-measure (LazyGrid IndexOutOfBounds race).
    val rowsLoaded = items != null
    val rows = items.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState().value.gridColumns), state = grid,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = TopBarSpace, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("browseControls"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { genreMenu = !genreMenu }, modifier = Modifier.padding(end = 0.dp)) { Text(if (pickedGenres.isEmpty()) "Genres" else pickedGenres.joinToString(", "), fontSize = 14.sp) }
                    DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false }, modifier = Modifier.testTag("genreMenu")) {
                        DropdownMenuItem(text = { Text((if (pickedGenres.isEmpty()) "✓ " else "") + "All genres") }, onClick = { pickedGenres = emptySet(); prefs.genres = emptySet(); genreMenu = false })
                        genres.forEach { g -> DropdownMenuItem(text = { Text((if (g in pickedGenres) "✓ " else "") + g) }, onClick = {
                            val next = if (g in pickedGenres) pickedGenres - g else pickedGenres + g
                            pickedGenres = next; prefs.genres = next
                        }) }
                    }
                }
                if (libraries.size > 1) Box {
                    OutlinedButton(onClick = { libraryMenu = true }, modifier = Modifier.padding(0.dp)) { Text("Library: " + (libraries.firstOrNull { it.Id == libraryId }?.Name ?: "All"), fontSize = 14.sp) }
                    DropdownMenu(expanded = libraryMenu, onDismissRequest = { libraryMenu = false }) {
                        DropdownMenuItem(text = { Text((if (libraryId == null) "✓ " else "") + "All") }, onClick = { libraryId = null; prefs.library = null; libraryMenu = false })
                        libraries.forEach { l -> DropdownMenuItem(text = { Text((if (l.Id == libraryId) "✓ " else "") + l.Name.orEmpty()) }, onClick = { libraryId = l.Id; prefs.library = l.Id; libraryMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(0.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) { androidx.compose.material3.Icon(SortIcon, contentDescription = "Sort and filter", modifier = androidx.compose.ui.Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Sort.values().forEach { s ->
                            DropdownMenuItem(text = { Text((if (s == sort) "✓ " else "") + s.label) }, onClick = { sort = s; prefs.sort = s; menu = false })
                        }
                        DropdownMenuItem(text = { Text((if (unwatched) "✓ " else "") + "Unwatched only") }, onClick = { unwatched = !unwatched; prefs.unwatched = unwatched; menu = false })
                    }
                }
                }
            }
        }
        if (rowsLoaded && rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Nothing here.", color = DriftfinColors.TextSecondary) }
        items(rows, key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { onOpen(it) } }
    }
}
