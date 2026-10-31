package tv.jetflix.phone.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.jetflix.phone.data.Content
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val Home = "home"
    const val Shows = "shows"
    const val Movies = "movies"
    const val Search = "search"
    const val MyStuff = "mystuff"
    const val Settings = "settings"
    const val Details = "details/{id}"
    const val Player = "player/{id}"
    const val Actor = "actor/{id}/{name}"
    fun actor(id: String, name: String) = "actor/$id/${android.net.Uri.encode(name)}"
    const val Collection = "collection/{id}/{title}"
    const val Library = "library/{id}/{name}"
    fun library(id: String, name: String) = "library/$id/${android.net.Uri.encode(name)}"
    fun collection(id: String, title: String) = "collection/$id/${android.net.Uri.encode(title)}"
    fun details(id: String) = "details/$id"
    fun player(id: String) = "player/$id"
    const val Trailer = "trailer/{videoId}/{title}"
    fun trailer(videoId: String, title: String) = "trailer/$videoId/${android.net.Uri.encode(title)}"
}

/** The artwork the frosted top bar blurs. Screens set it to the hero or the first cover on screen. */
object TopArt { val url = MutableStateFlow<String?>(null) }

/** Rewrites a Jellyfin image address to ask for a small copy. A blurred image never needs full resolution. */
fun blurSource(url: String, width: Int): String =
    if (url.contains("maxWidth=")) url.replace(Regex("maxWidth=\\d+"), "maxWidth=$width")
    else url + (if (url.contains("?")) "&" else "?") + "maxWidth=$width"

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.Home, "Home", Icons.Filled.Home),
    BottomTab(Routes.Search, "Search", Icons.Filled.Search),
    BottomTab(Routes.MyStuff, "My stuff", Icons.Filled.Star),
    BottomTab(Routes.Settings, "Settings", Icons.Filled.Settings),
)

private val topPageRoutes = setOf(Routes.Home, Routes.Shows, Routes.Movies)

@Composable
fun DriftfinShell() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBars = route != null && !route.startsWith("details") && !route.startsWith("player") && !route.startsWith("trailer")
    // Back from any other page goes Home first, then leaves the app.
    BackHandler(enabled = route != null && route != Routes.Home && !route.startsWith("details") && !route.startsWith("player") && !route.startsWith("trailer")) { goTo(nav, Routes.Home) }
    Scaffold(
        containerColor = DriftfinColors.Background,
        bottomBar = { if (showBars) BottomBar(route, nav) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            NavHost(nav, startDestination = Routes.Home) {
                composable(Routes.Home) { HomeScreen(onOpen = { nav.navigate(Routes.details(it.Id)) }, onOpenLibrary = { lib -> nav.navigate(Routes.library(lib.Id, lib.Name.orEmpty())) }, onTrailer = { t ->
                    when (t) {
                        is tv.jetflix.phone.data.TrailerSource.Local -> nav.navigate(Routes.player(t.item.Id))
                        is tv.jetflix.phone.data.TrailerSource.YouTube -> nav.navigate(Routes.trailer(t.videoId, t.title))
                    }
                }) }
                composable(Routes.Shows) { BrowseScreen("Series", "All shows", onOpen = { nav.navigate(Routes.details(it.Id)) }) }
                composable(Routes.Movies) { BrowseScreen("Movie", "All movies", onOpen = { nav.navigate(Routes.details(it.Id)) }) }
                composable(Routes.Search) { SearchScreen(onOpen = { nav.navigate(Routes.details(it.Id)) }, onOpenPerson = { p -> nav.navigate(Routes.actor(p.Id, p.Name.orEmpty())) }) }
                composable(Routes.MyStuff) { MyStuffScreen(onOpen = { nav.navigate(Routes.details(it.Id)) }, onOpenCollection = { nav.navigate(Routes.collection(it.Id, it.Name.orEmpty())) }) }
                composable(Routes.Collection) {
                    CollectionScreen(it.arguments?.getString("id").orEmpty(), it.arguments?.getString("title").orEmpty(), onBack = { nav.popBackStack() }, onOpen = { item -> nav.navigate(Routes.details(item.Id)) })
                }
                composable(Routes.Settings) { SettingsScreen() }
                composable(Routes.Details) {
                    DetailsScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onPlay = { id -> nav.navigate(Routes.player(id)) }, onOpenPerson = { p -> nav.navigate(Routes.actor(p.Id.orEmpty(), p.Name.orEmpty())) }, onOpenItem = { nid -> nav.navigate(Routes.details(nid)) }, onTrailer = { t ->
                        when (t) {
                            is tv.jetflix.phone.data.TrailerSource.Local -> nav.navigate(Routes.player(t.item.Id))
                            is tv.jetflix.phone.data.TrailerSource.YouTube -> nav.navigate(Routes.trailer(t.videoId, t.title))
                        }
                    })
                }
                composable(Routes.Library) { back ->
                    val libId = back.arguments?.getString("id").orEmpty()
                    val libName = back.arguments?.getString("name").orEmpty()
                    var libType by remember { mutableStateOf("Movie,Series") }
                    LaunchedEffect(libId) {
                        val view = Content.libraries().firstOrNull { it.Id == libId }
                        if (view != null) {
                            libType = when {
                                tv.jetflix.phone.data.matchesKind(view, "Movie") && !tv.jetflix.phone.data.matchesKind(view, "Series") -> "Movie"
                                tv.jetflix.phone.data.matchesKind(view, "Series") && !tv.jetflix.phone.data.matchesKind(view, "Movie") -> "Series"
                                else -> "Movie,Series"
                            }
                        }
                    }
                    BrowseScreen(libType, libName, onOpen = { nav.navigate(Routes.details(it.Id)) }, initialLibraryId = libId)
                }
                composable(Routes.Actor) {
                    ActorScreen(it.arguments?.getString("id").orEmpty(), it.arguments?.getString("name").orEmpty(), onBack = { nav.popBackStack() }, onOpen = { item -> nav.navigate(Routes.details(item.Id)) })
                }
                composable(Routes.Trailer) {
                    YouTubeTrailerScreen(it.arguments?.getString("videoId").orEmpty(), it.arguments?.getString("title").orEmpty(), onClose = { nav.popBackStack() })
                }
                composable(Routes.Player) { val pid = it.arguments?.getString("id").orEmpty()
                    PlayerScreen(pid, onExit = { nav.popBackStack() }, onPlayNext = { next -> nav.navigate(Routes.player(next)) { popUpTo(Routes.Player) { inclusive = true } } }, onOpenNext = { next -> nav.navigate(Routes.details(next)) { popUpTo(Routes.Player) { inclusive = true } } }) }
            }
            if (route in topPageRoutes) TopBar(route, nav, Modifier.align(Alignment.TopCenter))
        }
    }
}

private fun goTo(nav: NavHostController, route: String) {
    // Search starts empty on every visit: the last term and its results don't come back with the tab.
    if (route == Routes.Search) {
        nav.popBackStack(Routes.Search, inclusive = true)
        nav.navigate(Routes.Search) { launchSingleTop = true }
        return
    }
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(route: String?, nav: NavHostController) {
    NavigationBar(containerColor = Color(0xFF14141A)) {
        bottomTabs.forEach { tab ->
            // Shows and Movies live in the top bar, so Home stays lit on them too.
            val selected = route == tab.route || (tab.route == Routes.Home && route in topPageRoutes)
            NavigationBarItem(
                selected = selected,
                onClick = { goTo(nav, tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black, selectedTextColor = Color.White,
                    indicatorColor = Color.White, unselectedIconColor = DriftfinColors.TextSecondary, unselectedTextColor = DriftfinColors.TextSecondary,
                ),
            )
        }
    }
}

/**
 * Logo and the Shows / Movies pills over a blurred, darkened copy of the current artwork.
 * Same recipe as the TV bar: blur 28 dp under a 0xFF141414 scrim at 62%, with the small-copy fallback before Android 12.
 * With no artwork the bar is solid dark, so labels never sit on raw posters.
 */
@Composable
private fun TopBar(route: String?, nav: NavHostController, modifier: Modifier) {
    val art by TopArt.url.collectAsState()
    Box(modifier.fillMaxWidth().clipToBounds()) {
        val url = art
        if (url != null) {
            // Android 12+ blurs live from a 320 px copy. Older Android has no live blur, so it shows a 48 px copy scaled up with
            // smoothing, which looks blurred on any version (same fallback as the TV app).
            val live = Build.VERSION.SDK_INT >= 31
            Art(blurSource(url, if (live) 320 else 48), Modifier.matchParentSize().then(if (live) Modifier.blur(28.dp) else Modifier))
            Box(Modifier.matchParentSize().background(DriftfinColors.Background.copy(alpha = 0.62f)))
        } else {
            Box(Modifier.matchParentSize().background(DriftfinColors.Background))
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("DRIFTFIN", color = DriftfinColors.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp,
                modifier = Modifier.clickable { goTo(nav, Routes.Home) })
            Spacer(Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Pill("Shows", route == Routes.Shows) { goTo(nav, if (route == Routes.Shows) Routes.Home else Routes.Shows) }
                Pill("Movies", route == Routes.Movies) { goTo(nav, if (route == Routes.Movies) Routes.Home else Routes.Movies) }
            }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = 40.dp).clip(shape)
            .then(if (selected) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0x99FFFFFF), shape))
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}
