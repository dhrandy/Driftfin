package tv.jetflix.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content

@Composable
fun SearchScreen(onOpen: (BaseItemDto) -> Unit, onOpenPerson: (BaseItemDto) -> Unit = {}) {
    var term by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var people by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var recMovies by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var recShows by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    LaunchedEffect(Unit) { recMovies = Content.recommended("Movie"); recShows = Content.recommended("Series") }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(term) {
        if (term.isBlank()) { results = emptyList(); people = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300) // wait for the typing to pause
        val t = term.trim()
        results = Content.search(t)
        people = Content.searchPeople(t)
        searching = false
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        OutlinedTextField(
            value = term, onValueChange = { term = it }, singleLine = true, textStyle = InputTextStyle,
            placeholder = { Text("Search movies, shows and actors") }, modifier = Modifier.fillMaxWidth(),
            // X at the far right empties the box; hidden while there is nothing to clear.
            trailingIcon = { if (term.isNotEmpty()) IconButton(onClick = { term = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = DriftfinColors.TextSecondary) } },
        )
        if (term.isNotBlank() && results.isEmpty() && people.isEmpty() && !searching) Text("No matches.", color = DriftfinColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        if (term.isBlank()) {
            androidx.compose.foundation.lazy.LazyColumn { item { SectionRow("Recommended movies", recMovies, onOpen = onOpen) }; item { SectionRow("Recommended shows", recShows, onOpen = onOpen) } }
        }
        if (term.isNotBlank() && people.isNotEmpty()) {
            Text("Actors", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people, key = { it.Id }) { p ->
                    Column(Modifier.width(84.dp).clickable { onOpenPerson(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ActorAvatar(p.ImageTags["Primary"]?.let { tag -> Graph.repo.imageUrl(p.Id, "Primary", 200, tag) }, p.Name.orEmpty(), Modifier.size(72.dp).clip(CircleShape))
                        Text(p.Name.orEmpty(), fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState().value.gridColumns), modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { items(results, key = { it.Id }) { PosterCard(it, Modifier.fillMaxWidth()) { onOpen(it) } } }
    }
}
