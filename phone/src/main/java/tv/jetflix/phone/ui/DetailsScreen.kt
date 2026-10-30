package tv.jetflix.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.fromStart
import tv.jetflix.phone.data.ticksToHms

/** One title. A series lists its seasons and episodes; an episode also shows its show and season;
 *  a movie or an episode plays straight from here. Opens at the top every time. */
@Composable
fun DetailsScreen(id: String, onBack: () -> Unit, onPlay: (String) -> Unit, onOpenPerson: (tv.jetflix.phone.data.BaseItemPerson) -> Unit = {}, onTrailer: (tv.jetflix.phone.data.TrailerSource) -> Unit = {}, onOpenItem: (String) -> Unit = {}) {
    var item by remember(id) { mutableStateOf<BaseItemDto?>(null) }
    var episodes by remember(id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var seasons by remember(id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var selectedSeason by remember(id) { mutableStateOf<String?>(null) }
    var series by remember(id) { mutableStateOf<BaseItemDto?>(null) }
    var seriesTrailer by remember(id) { mutableStateOf<tv.jetflix.phone.data.TrailerSource?>(null) }
    var favorite by remember(id) { mutableStateOf(false) }
    var trailer by remember(id) { mutableStateOf<tv.jetflix.phone.data.TrailerSource?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    LaunchedEffect(id) {
        val loaded = Content.item(id)
        item = loaded; failed = loaded == null
        favorite = loaded?.UserData?.IsFavorite == true
        if (loaded?.isSeries == true) {
            seasons = Content.seasons(id)
            episodes = Content.episodes(id)
            selectedSeason = seasons.firstOrNull()?.Id
        }
        if (loaded?.Type == "Episode" && !loaded.SeriesId.isNullOrBlank()) {
            // An episode page still carries its show: series block, the show's star and trailer,
            // and the rest of this season below.
            val s = Content.item(loaded.SeriesId)
            series = s
            if (s != null) {
                favorite = s.UserData?.IsFavorite == true
                val local = if ((s.LocalTrailerCount ?: 0) > 0) Content.localTrailers(s.Id) else emptyList()
                seriesTrailer = tv.jetflix.phone.data.Trailers.pick(s, local)
                seasons = Content.seasons(s.Id)
                episodes = Content.episodes(s.Id)
                selectedSeason = loaded.SeasonId ?: seasons.firstOrNull()?.Id
            }
        }
        trailer = loaded?.let { m ->
            // Jellyfin's trailer files first, then the YouTube links on the item; a failed lookup just hides the button.
            val local = if ((m.LocalTrailerCount ?: 0) > 0) Content.localTrailers(m.Id) else emptyList()
            tv.jetflix.phone.data.Trailers.pick(m, local)
        }
    }
    val it = item
    if (it == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Column {
                BackIconButton(onBack)
                Text(if (failed) "Could not load this title." else "Loading...", color = DriftfinColors.TextSecondary, modifier = Modifier.padding(top = 16.dp))
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
                Art(Graph.repo.backdropUrl(it, 1000), Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x66000000), DriftfinColors.Background))))
                BackIconButton(onBack, Modifier.statusBarsPadding().padding(12.dp).align(androidx.compose.ui.Alignment.TopStart))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TitleOrLogo(it, 26.sp)
                val duration = if (it.isSeries) it.ChildCount?.let { c -> if (c == 1) "1 Season" else "$c Seasons" } else it.runtimeLabel()
                // Air dates (TV parity): series adds "Premiered <date>" after the year; episodes show the bare date after the runtime; movies unchanged. Calendar date, no timezone shift; missing or bad dates leave no gap.
                val airDate = it.airDateLabel()
                val meta = when {
                    it.isMovie -> listOfNotNull(it.ProductionYear?.toString(), it.OfficialRating, duration, it.Genres.take(3).joinToString(" \u00b7 ").ifBlank { null })
                    it.isSeries -> listOfNotNull(it.ProductionYear?.toString(), airDate?.let { d -> "Premiered $d" }, it.OfficialRating, duration, it.Genres.take(3).joinToString(" \u00b7 ").ifBlank { null })
                    else -> listOfNotNull(if (airDate == null) it.ProductionYear?.toString() else null, it.OfficialRating, duration, airDate, it.Genres.take(3).joinToString(" \u00b7 ").ifBlank { null })
                }
                val formats = listOfNotNull(if (tv.jetflix.phone.data.Resolution4K.badge(it)) "4K" else null) + tv.jetflix.phone.data.HdrFormats.of(it).toList()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((meta + formats).joinToString(" \u00b7 "), color = DriftfinColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                    it.CommunityRating?.let { rating ->
                        androidx.compose.material3.Icon(Icons.Filled.Star, contentDescription = null, tint = DriftfinColors.Red, modifier = Modifier.size(14.dp))
                        Text("%.1f".format(rating), color = DriftfinColors.TextSecondary, fontSize = 13.sp)
                    }
                }
                // Episode identity right above the buttons: "S2:E1 · Title" for the exact item Play/Resume opens.
                if (it.isEpisode) {
                    val identity = listOfNotNull(it.episodeLabel(), it.Name?.takeIf { n -> n.isNotBlank() }).joinToString("  \u00b7  ")
                    if (identity.isNotBlank()) Text(identity, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                val resume = it.UserData?.PlaybackPositionTicks ?: 0
                val watchIds by tv.jetflix.phone.data.Watchlist.ids.collectAsState()
                // The star covers movies and shows (Marquee's rule); on an episode page it stars the show.
                val starTarget = if (it.Type == "Movie" || it.Type == "Series") it else if (it.Type == "Episode") series else null
                // The heart follows the same rule as the star: on an episode page it favorites the whole show,
                // and stays hidden until the show is known so a tap can never favorite the episode itself.
                val favTarget = starTarget
                val onList = starTarget?.let { t -> watchIds.contains(t.Id) } == true
                val shownTrailer = trailer ?: seriesTrailer
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (!it.isSeries) {
                        Button(onClick = { onPlay(it.Id) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (resume > 0) "\u25b6  Resume ${resume.ticksToHms()}" else "\u25b6  Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    // Same look as the TV app: star first (outline = not on the list, gold fill = on it), then the heart.
                    if (starTarget != null) {
                        IconToggle(
                            icon = if (onList) Icons.Filled.Star else StarOutlineIcon,
                            tint = if (onList) Color(0xFFE5C778) else Color.White,
                            label = if (onList) "In watchlist. Tap to remove" else "Add to watchlist",
                        ) { tv.jetflix.phone.data.Watchlist.toggle(starTarget.Id) }
                    }
                    if (favTarget != null) {
                        IconToggle(
                            icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            tint = Color.White,
                            label = if (favorite) "In favorites. Tap to remove" else "Add to favorites",
                        ) { scope.launch { if (Content.setFavorite(favTarget.Id, !favorite)) favorite = !favorite } }
                    }
                    shownTrailer?.let { t ->
                        IconToggle(icon = TrailerIcon, tint = Color.White, label = "Watch trailer") { onTrailer(t) }
                    }
                }
                it.Overview?.let { text -> Text(text, fontSize = 15.sp, color = DriftfinColors.TextSecondary, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        val show = series
        if (it.Type == "Episode" && show != null) item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text("Show", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().clickable { onOpenItem(show.Id) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Art(Graph.repo.posterUrl(show, 200), Modifier.width(56.dp).aspectRatio(2f / 3f).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                    Column {
                        Text(show.displayTitle(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Open the show page", color = DriftfinColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        // Cast: the item's own people first. Episodes without any fall back to the show's cast
        // (TV parity: some servers only populate People on the series).
        val ownCast = it.People.filter { p -> p.Id != null && (p.Type == "Actor" || p.Type == "GuestStar") }
        val cast = (if (ownCast.isNotEmpty()) ownCast else if (it.Type == "Episode") show?.People.orEmpty().filter { p -> p.Id != null && (p.Type == "Actor" || p.Type == "GuestStar") } else emptyList()).take(20)
        if (cast.isNotEmpty()) item {
            Column(Modifier.padding(top = 4.dp)) {
                Text("Cast", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(cast, key = { p -> p.Id.orEmpty() }) { p ->
                        Column(Modifier.width(84.dp).clickable { onOpenPerson(p) }, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Art(Graph.repo.personImageUrl(p), Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape))
                            Text(p.Name.orEmpty(), fontSize = 12.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            p.Role?.takeIf { r -> r.isNotBlank() }?.let { r -> Text(r, fontSize = 11.sp, maxLines = 1, color = DriftfinColors.TextSecondary) }
                        }
                    }
                }
            }
        }
        if (it.isSeries || (it.Type == "Episode" && show != null)) {
            if (seasons.size > 1) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { season ->
                        androidx.compose.material3.FilterChip(
                            selected = season.Id == selectedSeason, onClick = { selectedSeason = season.Id },
                            label = { Text(season.Name ?: "Season ${season.IndexNumber ?: ""}", fontSize = 14.sp) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = DriftfinColors.Red, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            val shownEpisodes = if (seasons.size > 1 && selectedSeason != null) episodes.filter { e -> e.SeasonId == selectedSeason }.ifEmpty { episodes } else episodes
            items(shownEpisodes, key = { e -> "ep-" + e.Id }) { e -> EpisodeRow(e, current = e.Id == it.Id, onPlay = onPlay) }
        }
        item { Box(Modifier.padding(24.dp)) }
    }
}

/** One episode in a season list. The episode this page is about is marked "this episode". */
@Composable
private fun EpisodeRow(e: BaseItemDto, current: Boolean = false, onPlay: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPlay(e.Id) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Art(Graph.repo.thumbUrl(e, 360), Modifier.height(72.dp).aspectRatio(16f / 9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(e.episodeLabel(), e.Name).joinToString("  ") + if (current) "  \u00b7  this episode" else "",
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (current) DriftfinColors.Red else Color.White,
            )
            val epMeta = listOfNotNull(e.runtimeLabel(), e.airDateLabel()).joinToString("  \u00b7  ")
            if (epMeta.isNotEmpty()) Text(epMeta, color = DriftfinColors.TextSecondary, fontSize = 13.sp)
            e.Overview?.let { o -> Text(o, color = DriftfinColors.TextSecondary, fontSize = 13.sp, maxLines = 3) }
        }
        if (e.UserData?.Played == true) {
            androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = "Watched", tint = DriftfinColors.Red, modifier = Modifier.size(18.dp).align(androidx.compose.ui.Alignment.CenterVertically))
        }
    }
}


/** A 48dp round icon button, same surface colors as the TV app's detail buttons. The label is for screen readers. */
@Composable
private fun IconToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilledIconButton(
        onClick = onClick, modifier = Modifier.size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = DriftfinColors.SurfaceLight.copy(alpha = 0.7f), contentColor = Color.White),
    ) { androidx.compose.material3.Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp)) }
}

/** Hollow star for "not on the Watchlist yet" (the core outlined star renders solid). Same shape as the TV app. */
val StarOutlineIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(
                "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24z" +
                "M12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"
            ),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}

/** Round 48dp back arrow, same surface as the heart and star. The label is for screen readers. */
@Composable
fun BackIconButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FilledIconButton(
        onClick = onBack, modifier = modifier.size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x99141414), contentColor = Color.White),
    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp)) }
}
/** Clapperboard, for "Watch trailer". Artwork chosen from the icon options (option 1). */
val TrailerIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Trailer", 24.dp, 24.dp, 24f, 24f).apply {
        // Tilted slate; the four stripe cutouts come from the even-odd fill.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(2.734f, 6.609f); lineTo(21.211f, 2.003f); lineTo(22.028f, 5.281f); lineTo(3.552f, 9.888f); close()
                moveTo(3.321f, 5.418f); lineTo(4.891f, 5.027f); lineTo(7.308f, 9.996f); lineTo(5.737f, 10.388f); close()
                moveTo(7.940f, 4.267f); lineTo(9.510f, 3.875f); lineTo(11.927f, 8.845f); lineTo(10.356f, 9.236f); close()
                moveTo(12.559f, 3.115f); lineTo(14.129f, 2.723f); lineTo(16.546f, 7.693f); lineTo(14.975f, 8.085f); close()
                moveTo(17.178f, 1.963f); lineTo(18.748f, 1.572f); lineTo(21.165f, 6.541f); lineTo(19.594f, 6.933f); close()
            },
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        // Board body (rounded rect, separate path so the slate's even-odd fill does not punch through).
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(4.397f, 9.888f)
                horizontalLineTo(19.603f)
                arcTo(2.112f, 2.112f, 0f, false, true, 21.715f, 12.0f)
                verticalLineTo(19.181f)
                arcTo(2.112f, 2.112f, 0f, false, true, 19.603f, 21.293f)
                horizontalLineTo(4.397f)
                arcTo(2.112f, 2.112f, 0f, false, true, 2.285f, 19.181f)
                verticalLineTo(12.0f)
                arcTo(2.112f, 2.112f, 0f, false, true, 4.397f, 9.888f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        // Hinge dot.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(4.608f, 8.198f)
                arcTo(1.056f, 1.056f, 0f, true, false, 2.496f, 8.198f)
                arcTo(1.056f, 1.056f, 0f, true, true, 4.608f, 8.198f)
                close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}
