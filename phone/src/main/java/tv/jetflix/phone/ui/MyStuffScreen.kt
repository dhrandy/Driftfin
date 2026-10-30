package tv.jetflix.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content

enum class MySection(val label: String) { Favorites("Favorites"), Watchlist("Watchlist"), Collections("Collections") }

/** The My Stuff sort choice, remembered between sessions per section, like Browse. */
private class MyStuffPrefs(context: android.content.Context) {
    private val p = context.getSharedPreferences("driftfin_phone_browse", android.content.Context.MODE_PRIVATE)
    fun sort(section: MySection): tv.jetflix.phone.data.Sort =
        tv.jetflix.phone.data.Sort.values().firstOrNull { it.name == p.getString("mystuff." + section.name + ".sort", null) }
            ?: if (section == MySection.Watchlist) tv.jetflix.phone.data.Sort.Added else tv.jetflix.phone.data.Sort.Name
    fun setSort(section: MySection, s: tv.jetflix.phone.data.Sort) { p.edit().putString("mystuff." + section.name + ".sort", s.name).apply() }
}

/** Favorites (heart), Watchlist (Driftfin's own star) and Collections (box sets), in the TV app's order. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MyStuffScreen(onOpen: (BaseItemDto) -> Unit, onOpenCollection: (BaseItemDto) -> Unit = {}) {
    val prefs = remember { MyStuffPrefs(tv.jetflix.phone.Graph.appContext()) }
    var section by remember { mutableStateOf(MySection.Favorites) }
    var items by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var sort by remember(section) { mutableStateOf(prefs.sort(section)) }
    var sortMenu by remember { mutableStateOf(false) }
    val starred by tv.jetflix.phone.data.Watchlist.ids.collectAsState()
    LaunchedEffect(section, starred, sort) {
        if (section != MySection.Watchlist) items = null
        items = when (section) {
            MySection.Favorites -> Content.favorites(sort)
            MySection.Watchlist -> Content.watchlist(sort)
            MySection.Collections -> Content.collections()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState().value.gridColumns), modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("My stuff", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (section != MySection.Collections) androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.OutlinedButton(onClick = { sortMenu = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        androidx.compose.material3.Icon(SortIcon, contentDescription = "Sort", modifier = androidx.compose.ui.Modifier.size(20.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        tv.jetflix.phone.data.Sort.values().forEach { opt ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text((if (opt == sort) "✓ " else "") + opt.label) }, onClick = { sort = opt; prefs.setSort(section, opt); sortMenu = false })
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MySection.values().forEach { s -> FilterChip(selected = s == section, onClick = { section = s }, label = { Text(s.label, fontSize = 14.sp) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DriftfinColors.Red, selectedLabelColor = androidx.compose.ui.graphics.Color.White)) }
            }
        }
        if (items?.isEmpty() == true) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(when (section) {
                MySection.Favorites -> "Nothing starred yet. Tap the heart on a title."
                MySection.Watchlist -> "Your watchlist is empty. Tap Watchlist on a title to save it for later."
                MySection.Collections -> "No collections on this server."
            }, color = DriftfinColors.TextSecondary, fontSize = 14.sp)
        }
        items(items.orEmpty(), key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { if (it.Type == "BoxSet") onOpenCollection(it) else onOpen(it) } }
    }
}

/** The titles inside one collection. */
@Composable
fun CollectionScreen(id: String, title: String, onBack: () -> Unit, onOpen: (BaseItemDto) -> Unit) {
    var items by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    LaunchedEffect(id) { items = Content.collectionItems(id) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState().value.gridColumns), modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BackIconButton(onBack)
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }
        }
        items(items.orEmpty(), key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { onOpen(it) } }
    }
}

/** An actor's bio plus the movies and shows they appear in on this server, each in its own section. */
@Composable
fun ActorScreen(personId: String, name: String, onBack: () -> Unit, onOpen: (BaseItemDto) -> Unit) {
    var person by remember(personId) { mutableStateOf<BaseItemDto?>(null) }
    var items by remember(personId) { mutableStateOf<List<BaseItemDto>?>(null) }
    var bioOpen by remember(personId) { mutableStateOf(false) }
    LaunchedEffect(personId) { person = Content.item(personId); items = Content.byPerson(personId) }
    val movies = items.orEmpty().filter { it.Type == "Movie" }
    val shows = items.orEmpty().filter { it.Type == "Series" }
    LazyVerticalGrid(
        columns = GridCells.Fixed(tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState().value.gridColumns), modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BackIconButton(onBack)
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }
        }
        // Bio: first paragraph only, with More for the rest, so the filmography stays near the top (reported 10/29).
        val bio = person?.Overview?.replace(Regex("[ \t]+\n"), "\n")?.trim()?.takeIf { it.isNotEmpty() }
        val paragraphs = bio?.split(Regex("\\r?\\n\\s*\\r?\\n"))?.filter { it.isNotBlank() }.orEmpty()
        if (paragraphs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(if (bioOpen) paragraphs.joinToString("\n\n") else paragraphs.first(), color = DriftfinColors.TextSecondary, fontSize = 14.sp)
                    if (paragraphs.size > 1) {
                        Text(
                            if (bioOpen) "Show less" else "More",
                            color = DriftfinColors.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).clickable { bioOpen = !bioOpen }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (items?.isEmpty() == true) item(span = { GridItemSpan(maxLineSpan) }) { Text("Nothing else on this server.", color = DriftfinColors.TextSecondary, fontSize = 14.sp) }
        if (movies.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Movies", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(movies, key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { onOpen(it) } }
        }
        if (shows.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Shows", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(shows, key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { onOpen(it) } }
        }
    }
}
