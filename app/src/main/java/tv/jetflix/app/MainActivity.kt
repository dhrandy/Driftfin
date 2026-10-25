package tv.jetflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.TokenStore
import tv.jetflix.app.ui.details.DetailsScreen
import tv.jetflix.app.ui.details.DetailsViewModel
import tv.jetflix.app.ui.home.HomeScreen
import tv.jetflix.app.ui.home.HomeViewModel
import tv.jetflix.app.ui.library.LibraryScreen
import tv.jetflix.app.ui.library.LibraryViewModel
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.login.LoginScreen
import tv.jetflix.app.ui.login.LoginViewModel
import tv.jetflix.app.ui.player.PlayerScreen
import tv.jetflix.app.ui.search.SearchScreen
import tv.jetflix.app.ui.search.SearchViewModel
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import tv.jetflix.app.ui.theme.JetflixTheme

private sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data class Library(val tab: JetflixTab) : Screen()
    data class Collection(val id: String, val title: String) : Screen()
    data object Search : Screen()
    data object Settings : Screen()
    data class Details(val itemId: String, val focusEpisodeId: String? = null) : Screen()
    data class Player(val item: BaseItemDto) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT ->
                    tv.jetflix.app.ui.components.ButtonSounds.onKey(select = false)
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (event.repeatCount == 0) tv.jetflix.app.ui.components.ButtonSounds.onKey(select = true)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JetflixTheme {
                JetflixRoot()
            }
        }
    }
}

@Composable
fun JetflixRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as JetflixApp
    val session by SessionManager.session.collectAsState()

    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val current = backStack.last()
    androidx.compose.runtime.SideEffect { tv.jetflix.app.ui.components.ButtonSounds.onPlayer = current is Screen.Player }

    fun push(screen: Screen) { backStack = backStack + screen }
    fun pop() { if (backStack.size > 1) backStack = backStack.dropLast(1) }

    BackHandler(enabled = session != null && backStack.size > 1) { pop() }
    // From Shows/Movies/My List/Search/Settings, Back goes to Home first; Back on Home exits.
    BackHandler(enabled = session != null && backStack.size == 1 && current != Screen.Home) {
        backStack = listOf(Screen.Home)
    }
    // Back on Home asks before leaving the app. Cancel or Back stays; Exit leaves.
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = session != null && backStack.size == 1 && current == Screen.Home && !confirmExit) { confirmExit = true }
    if (confirmExit) {
        val activity = context as? android.app.Activity
        ExitDialog(onCancel = { confirmExit = false }, onExit = { confirmExit = false; activity?.finish() })
    }

    if (session == null) {
        val vm: LoginViewModel = viewModel(
            factory = viewModelFactory { LoginViewModel(app.repo, app.tokenStore) },
        )
        LoginScreen(viewModel = vm, onLoggedIn = { backStack = listOf(Screen.Home) })
        return
    }

    val userName = session?.userName
    val onSignOut = {
        app.tokenStore.clear()
        SessionManager.signOut()
    }
    val onTabSelected: (JetflixTab) -> Unit = { tab ->
        backStack = listOf(
            when (tab) {
                JetflixTab.HOME -> Screen.Home
                JetflixTab.SHOWS -> Screen.Library(JetflixTab.SHOWS)
                JetflixTab.MOVIES -> Screen.Library(JetflixTab.MOVIES)
                JetflixTab.MY_LIST -> Screen.Library(JetflixTab.MY_LIST)
                JetflixTab.SEARCH -> Screen.Search
                JetflixTab.SETTINGS -> Screen.Settings
            },
        )
    }

    // In-app screensaver: after 5 idle minutes on any screen or while the player is paused (never while playing) rotate unwatched movie backdrops fullscreen.
    tv.jetflix.app.ui.components.IdleScreensaverHost(
        blocked = (current is Screen.Player && !tv.jetflix.app.ui.components.PlaybackPause.paused.collectAsState().value) || confirmExit,
        loadSlides = {
            val sess = session
            if (sess == null) emptyList() else {
                val api = app.repo.apiFor(sess.serverUrl)
                val pool = api.getItems(userId = sess.userId, includeItemTypes = "Movie", recursive = true, sortBy = "Random", limit = 40, filters = "IsUnplayed", fields = "Overview").body()?.Items ?: emptyList()
                tv.jetflix.app.data.BannerPicks.randomUnwatchedList(pool) { app.repo.backdropUrl(it, 1920) != null }.mapNotNull { m -> app.repo.backdropUrl(m, 1920)?.let { tv.jetflix.app.ui.components.ScreensaverSlide(it, m.displayTitle(), m.Overview) } }
            }
        },
    ) {
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(key = current.toString()) {
    when (val screen = current) {
        Screen.Login -> Unit
        Screen.Home -> {
            val vm: HomeViewModel = viewModel(key = "home-${session?.userId}", factory = viewModelFactory { HomeViewModel(app.repo) })
            LaunchedEffect(Unit) { vm.load() }
            HomeScreen(
                viewModel = vm,
                userName = userName,
                onTabSelected = onTabSelected,
                onItemClick = { push(Screen.Details(it.seriesOrSelfId(), if (it.isEpisode) it.Id else null)) },
                onPlay = { push(Screen.Player(it)) },
                onSignOut = onSignOut,
            )
        }
        is Screen.Library -> {
            val vm: LibraryViewModel = viewModel(key = "library-${session?.userId}-${screen.tab}", factory = viewModelFactory { LibraryViewModel(app.repo) })
            LaunchedEffect(screen.tab) { vm.load(screen.tab) }
            LibraryScreen(
                viewModel = vm,
                tab = screen.tab,
                userName = userName,
                onTabSelected = onTabSelected,
                onItemClick = { if (it.Type == "BoxSet") push(Screen.Collection(it.Id, it.Name.orEmpty())) else push(Screen.Details(it.seriesOrSelfId())) },
                onSignOut = onSignOut,
                onPlay = { push(Screen.Player(it)) },
            )
        }
        is Screen.Collection -> {
            val vm: LibraryViewModel = viewModel(key = "collection-${session?.userId}-${screen.id}", factory = viewModelFactory { LibraryViewModel(app.repo) })
            LaunchedEffect(screen.id) { vm.loadCollection(screen.id) }
            LibraryScreen(
                viewModel = vm,
                tab = JetflixTab.MY_LIST,
                userName = userName,
                onTabSelected = onTabSelected,
                onItemClick = { if (it.Type == "BoxSet") push(Screen.Collection(it.Id, it.Name.orEmpty())) else push(Screen.Details(it.seriesOrSelfId())) },
                onSignOut = onSignOut,
                onPlay = { push(Screen.Player(it)) },
                collectionTitle = screen.title,
            )
        }
        Screen.Settings -> {
            tv.jetflix.app.ui.settings.SettingsScreen(
                userName = userName,
                serverUrl = session?.serverUrl,
                onTabSelected = onTabSelected,
                onSignOut = onSignOut,
            )
        }
        Screen.Search -> {
            val vm: SearchViewModel = viewModel(key = "search-${session?.userId}", factory = viewModelFactory { SearchViewModel(app.repo) })
            SearchScreen(
                viewModel = vm,
                userName = userName,
                onTabSelected = onTabSelected,
                onItemClick = { push(Screen.Details(it.seriesOrSelfId())) },
                onSignOut = onSignOut,
            )
        }
        is Screen.Details -> {
            val vm: DetailsViewModel = viewModel(key = "details-${session?.userId}-${screen.itemId}-${screen.focusEpisodeId}", factory = viewModelFactory { DetailsViewModel(app.repo) })
            LaunchedEffect(screen.itemId, screen.focusEpisodeId) { vm.load(screen.itemId, screen.focusEpisodeId) }
            DetailsScreen(
                viewModel = vm,
                onPlay = { push(Screen.Player(it)) },
                onItemClick = { push(Screen.Details(it.seriesOrSelfId())) },
                onBack = { pop() },
            )
        }
        is Screen.Player -> {
            PlayerScreen(
                item = screen.item,
                repo = app.repo,
                onFinished = { pop() },
                onPlayNext = { next -> backStack = backStack.dropLast(1) + Screen.Player(next) },
            )
        }
    }
    }
    }
}

/** Episodes open their series details page; everything else opens itself. */
private fun BaseItemDto.seriesOrSelfId(): String = if (isEpisode && SeriesId != null) SeriesId!! else Id

private fun <VM : androidx.lifecycle.ViewModel> viewModelFactory(create: () -> VM) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }


/** "Exit Driftfin?" with Cancel focused first, so a stray OK press never leaves the app. */
@Composable
fun ExitDialog(onCancel: () -> Unit, onExit: () -> Unit) {
    val cancelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .background(androidx.compose.ui.graphics.Color(0xFF222222), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
        ) {
            androidx.tv.material3.Text("Exit Driftfin?", style = tv.jetflix.app.ui.theme.JetflixType.sectionTitle, color = androidx.compose.ui.graphics.Color.White)
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.tv.material3.Button(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { androidx.tv.material3.Text("Cancel") }
                androidx.tv.material3.Button(onClick = onExit) { androidx.tv.material3.Text("Exit") }
            }
        }
    }
    LaunchedEffect(Unit) { repeat(10) { try { cancelFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
}
