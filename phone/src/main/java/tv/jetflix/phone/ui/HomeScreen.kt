package tv.jetflix.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content

/** Space the frosted top bar takes, so page content starts below it. */
val TopBarSpace = 104.dp

private data class HomeData(
    val heroes: List<BaseItemDto> = emptyList(),
    val libraries: List<BaseItemDto> = emptyList(),
    val recommended: List<BaseItemDto> = emptyList(),
    val resume: List<BaseItemDto> = emptyList(),
    val nextUp: List<BaseItemDto> = emptyList(),
    val favorites: List<BaseItemDto> = emptyList(),
    val movies: List<BaseItemDto> = emptyList(),
    val episodes: List<BaseItemDto> = emptyList(),
    val loaded: Boolean = false,
)

@Composable
fun HomeScreen(onOpen: (BaseItemDto) -> Unit, onTrailer: (tv.jetflix.phone.data.TrailerSource) -> Unit = {}, onOpenLibrary: (BaseItemDto) -> Unit = {}) {
    var data by remember { mutableStateOf(HomeData()) }
    var heroIndex by remember { mutableStateOf(0) }
    var heroTrailer by remember { mutableStateOf<tv.jetflix.phone.data.TrailerSource?>(null) }
    val hero = data.heroes.getOrNull(heroIndex)
    LaunchedEffect(Unit) {
        val heroes = Content.heroCandidates()
        data = HomeData(
            heroes = heroes.shuffled().take(10),
            libraries = Content.libraries(),
            recommended = Content.recommendedAll(),
            resume = Content.resume(), nextUp = Content.nextUp(), favorites = Content.favorites().take(20),
            movies = Content.latest("Movie"), episodes = Content.latest("Episode"), loaded = true,
        )
        TopArt.url.value = data.heroes.firstOrNull()?.let { Graph.repo.backdropUrl(it, 960) }
    }
    // The banner cycles on swipe; the frosted bar and the trailer button follow the showing title.
    LaunchedEffect(hero?.Id) {
        TopArt.url.value = hero?.let { Graph.repo.backdropUrl(it, 960) }
        heroTrailer = hero?.let { h ->
            val local = if ((h.LocalTrailerCount ?: 0) > 0) Content.localTrailers(h.Id) else emptyList()
            tv.jetflix.phone.data.Trailers.pick(h, local)
        }
    }
    val showNextUp by tv.jetflix.phone.data.PhoneSettings.showNextUp.collectAsState()
    val showFavorites by tv.jetflix.phone.data.PhoneSettings.showFavorites.collectAsState()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(top = TopBarSpace)) {
        hero?.let { h -> item { Hero(h, data.heroes.size, trailer = heroTrailer, onCycle = { d -> heroIndex = (heroIndex + d).floorMod(data.heroes.size.coerceAtLeast(1)) }, onOpen = onOpen, onTrailer = onTrailer) } }
        // Every video library this account can see, with its own name, exactly as the server reports
        // it - a "Kids Movies" library, renamed "Films", or several of the same kind all appear as-is.
        if (data.libraries.isNotEmpty()) item { LibraryRow(data.libraries, onOpenLibrary) }
        item { SectionRow("Recommended", data.recommended, onOpen = onOpen) }
        item { SectionRow("Continue Watching", data.resume, titlePoster = true, onOpen = onOpen) }
        if (showNextUp) item { SectionRow("Next Up", data.nextUp, titlePoster = true, onOpen = onOpen) }
        if (showFavorites) item { SectionRow("Favorites", data.favorites, onOpen = onOpen) }
        item { SectionRow("Recently Added Movies", data.movies, onOpen = onOpen) }
        item { SectionRow("Recently Added Episodes", data.episodes, onOpen = onOpen) }
        if (data.loaded && data.heroes.isEmpty() && data.resume.isEmpty() && data.movies.isEmpty()) {
            item { Text("Nothing to show yet.", color = DriftfinColors.TextSecondary, modifier = Modifier.padding(24.dp)) }
        }
    }
}

private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m

/** Home's library shortcuts: one labeled tile per library, opening that library's own browse page. */
@Composable
private fun LibraryRow(libraries: List<BaseItemDto>, onOpenLibrary: (BaseItemDto) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text("Libraries", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(libraries.size) { i ->
                val lib = libraries[i]
                Box(
                    Modifier.width(150.dp).heightIn(min = 72.dp).clip(RoundedCornerShape(8.dp)).background(DriftfinColors.SurfaceLight)
                        .clickable { onOpenLibrary(lib) }.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(lib.Name ?: "Library", color = DriftfinColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun Hero(item: BaseItemDto, count: Int = 1, trailer: tv.jetflix.phone.data.TrailerSource? = null, onCycle: (Int) -> Unit = {}, onOpen: (BaseItemDto) -> Unit, onTrailer: (tv.jetflix.phone.data.TrailerSource) -> Unit = {}) {
    // Swipe left/right anywhere on the banner that isn't a button: cycles the pick. Taps and vertical scrolls are untouched.
    val swipe = if (count > 1) Modifier.pointerInput(count) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragEnd = { val threshold = 56.dp.toPx(); if (drag < -threshold) onCycle(1) else if (drag > threshold) onCycle(-1); drag = 0f },
            onDragCancel = { drag = 0f },
            onHorizontalDrag = { _, d -> drag += d },
        )
    } else Modifier
    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(14.dp)).background(DriftfinColors.Surface).then(swipe)) {
        Art(Graph.repo.posterUrl(item, 900), Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)), startY = 300f)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            TitleOrLogo(item, 24.sp)
            Text(listOfNotNull(item.ProductionYear?.toString(), item.OfficialRating.takeIf { tv.jetflix.phone.data.PhoneSettings.heroRatings.collectAsState().value }, item.Genres.take(2).joinToString(" · ").ifBlank { null }).joinToString(" · "),
                color = DriftfinColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            Row {
                Button(onClick = { onOpen(item) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("▶  Play", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Button(onClick = { onOpen(item) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0x66FFFFFF), contentColor = Color.White),
                    modifier = Modifier.padding(start = 10.dp).heightIn(min = 48.dp)) { Text("More info", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                trailer?.let { t ->
                    androidx.compose.material3.FilledIconButton(
                        onClick = { onTrailer(t) }, modifier = Modifier.padding(start = 10.dp).heightIn(min = 48.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x66FFFFFF), contentColor = Color.White),
                    ) { androidx.compose.material3.Icon(TrailerIcon, contentDescription = "Watch trailer", modifier = Modifier.padding(horizontal = 12.dp).heightIn(max = 24.dp)) }
                }
            }
        }
    }
}
