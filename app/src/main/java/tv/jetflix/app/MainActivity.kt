package tv.jetflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
import tv.jetflix.app.ui.theme.JetflixTheme

private sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data class Library(val tab: JetflixTab) : Screen()
    data object Search : Screen()
    data object Settings : Screen()
    data class Details(val itemId: String, val focusEpisodeId: String? = null) : Screen()
    data class Player(val item: BaseItemDto) : Screen()
}

class MainActivity : ComponentActivity() {
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

    fun push(screen: Screen) { backStack = backStack + screen }
    fun pop() { if (backStack.size > 1) backStack = backStack.dropLast(1) }

    BackHandler(enabled = session != null && backStack.size > 1) { pop() }
    // From Shows/Movies/My List/Search/Settings, Back goes to Home first; Back on Home exits.
    BackHandler(enabled = session != null && backStack.size == 1 && current != Screen.Home) {
        backStack = listOf(Screen.Home)
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
                onItemClick = { push(Screen.Details(it.seriesOrSelfId())) },
                onSignOut = onSignOut,
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

/** Episodes open their series details page; everything else opens itself. */
private fun BaseItemDto.seriesOrSelfId(): String = if (isEpisode && SeriesId != null) SeriesId!! else Id

private fun <VM : androidx.lifecycle.ViewModel> viewModelFactory(create: () -> VM) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }
