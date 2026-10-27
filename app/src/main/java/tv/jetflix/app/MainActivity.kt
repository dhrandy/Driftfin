package tv.jetflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import tv.jetflix.app.ui.components.onLeftEdge
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
    data class Actor(val personId: String, val name: String) : Screen()
    data class Player(val item: BaseItemDto) : Screen()
    data class Trailer(val videoId: String, val title: String) : Screen()
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

    override fun onDestroy() {
        tv.jetflix.app.ui.components.FrameStats.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JetflixTheme {
                androidx.compose.foundation.layout.Box {
                    JetflixRoot()
                    tv.jetflix.app.ui.components.FrameTestHost(Modifier.align(androidx.compose.ui.Alignment.BottomEnd))
                }
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
        LoginScreen(viewModel = vm, onLoggedIn = {
            if (vm.state.value.sessionNotSaved) {
                android.widget.Toast.makeText(context, "Signed in, but this device could not save the login securely. You will need to sign in again next time.", android.widget.Toast.LENGTH_LONG).show()
            }
            backStack = listOf(Screen.Home)
        })
        return
    }

    // Once per installed build: what changed in this update. OK dismisses and remembers the build.
    var showWhatsNew by remember { mutableStateOf(tv.jetflix.app.BuildConfig.VERSION_CODE > tv.jetflix.app.data.AppSettings.lastSeenBuild) }
    if (showWhatsNew) {
        tv.jetflix.app.update.WhatsNewDialog(tv.jetflix.app.update.WhatsNew.notes) {
            tv.jetflix.app.data.AppSettings.lastSeenBuild = tv.jetflix.app.BuildConfig.VERSION_CODE
            showWhatsNew = false
        }
    }

    val userName = session?.userName
    val onSignOut = {
        // Switch profile: leaves other saved profiles in place so they can be picked again without a password.
        app.tokenStore.clear()
        SessionManager.signOut()
    }
    val onForgetProfile = {
        session?.let { app.tokenStore.forget(it.serverUrl, it.userId) }
        SessionManager.signOut()
    }
    // OK on the user name in the top bar opens the profile picker (switch profile / sign out).
    var showProfiles by remember { mutableStateOf(false) }
    val onUserClick = { showProfiles = true }
    val onTabSelected: (JetflixTab) -> Unit = { tab ->
      if (tab == JetflixTab.SEARCH) {
        // Search sits on top of the page you came from, so Back returns there.
        if (current != Screen.Search) push(Screen.Search)
      } else {
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
    }

    // The profile picker, opened by OK on the user name in the top bar. Switching reuses the login flow's
    // saved-profile path: the server confirms the saved login first, an expired one is dropped and asked for again.
    if (showProfiles) {
        val sess = session
        if (sess == null) {
            showProfiles = false
        } else {
            val switchVm: LoginViewModel = viewModel(
                factory = viewModelFactory { LoginViewModel(app.repo, app.tokenStore) },
            )
            LaunchedEffect(sess.serverUrl) { switchVm.update(serverUrl = sess.serverUrl) }
            val switchState = switchVm.state.collectAsState().value
            tv.jetflix.app.ui.components.ProfilePickerDialog(
                profiles = switchState.savedProfiles.ifEmpty { listOf(sess.userName) },
                currentName = sess.userName,
                connecting = switchState.connecting,
                error = switchState.error,
                onSelect = { name ->
                    if (name.equals(sess.userName, ignoreCase = true)) {
                        showProfiles = false
                    } else if (!switchState.connecting) {
                        switchVm.selectProfile(name) {
                            showProfiles = false
                            backStack = listOf(Screen.Home)
                        }
                    }
                },
                onSignOut = { showProfiles = false; onSignOut() },
                onDismiss = { if (!switchState.connecting) showProfiles = false },
            )
        }
    }

    // In-app screensaver: after 5 idle minutes on any screen or while the player is paused (never while playing) rotate unwatched movie backdrops fullscreen.
    tv.jetflix.app.ui.components.IdleScreensaverHost(
        blocked = (current is Screen.Player && !tv.jetflix.app.ui.components.PlaybackPause.paused.collectAsState().value) || confirmExit || showProfiles,
        loadSlides = {
            val sess = session
            if (sess == null) emptyList() else {
                val api = app.repo.apiFor(sess.serverUrl)
                val pool = api.getItems(userId = sess.userId, includeItemTypes = "Movie", recursive = true, sortBy = "Random", limit = 40, filters = "IsUnplayed", fields = "Overview").body()?.Items ?: emptyList()
                tv.jetflix.app.data.BannerPicks.randomUnwatchedList(pool) { app.repo.backdropUrl(it, 1920) != null }.mapNotNull { m -> app.repo.backdropUrl(m, 1920)?.let { tv.jetflix.app.ui.components.ScreensaverSlide(it, m.displayTitle(), m.Overview) } }
            }
        },
    ) {
    // The player stays composed (paused, not re-created) while the actor page is open over it.
    val hostLayer = tv.jetflix.app.ui.player.PlayerHost.layer(
        top = when (current) { is Screen.Player -> "Player"; is Screen.Actor -> "Actor"; else -> "" },
        below = if (backStack.getOrNull(backStack.size - 2) is Screen.Player) "Player" else null,
    )
    val playerScreen: Screen.Player? = when (hostLayer) {
        tv.jetflix.app.ui.player.PlayerHost.Layer.Interactive -> current as Screen.Player
        tv.jetflix.app.ui.player.PlayerHost.Layer.CoveredByActor -> backStack[backStack.size - 2] as Screen.Player
        tv.jetflix.app.ui.player.PlayerHost.Layer.Absent -> null
    }
    playerScreen?.let { ps ->
        androidx.compose.runtime.key(ps.item.Id) {
            PlayerScreen(
                item = ps.item,
                repo = app.repo,
                // PlayerScreen only reports "finished" while the player itself is on top, so this pop is always the player.
                onFinished = { pop() },
                onPlayNext = { next -> if (current is Screen.Player) backStack = backStack.dropLast(1) + Screen.Player(next) },
                onOpenActor = { id, name -> push(Screen.Actor(id, name)) },
                interactive = hostLayer == tv.jetflix.app.ui.player.PlayerHost.Layer.Interactive,
            )
        }
    }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    // One more Left at the left edge of a main section opens Search. Nested pages (details, actor,
    // collections), the player, trailers, the sign-in form, Search itself and Settings keep Left to themselves.
    val leftOpensSearch = Modifier.onLeftEdge(
        enabled = current is Screen.Home || current is Screen.Library,
        onEdge = { onTabSelected(JetflixTab.SEARCH) },
    )
    androidx.compose.foundation.layout.Box(leftOpensSearch) {
    if (!showWhatsNew) {
        tv.jetflix.app.update.StartupUpdateNotice(
            currentBuild = tv.jetflix.app.BuildConfig.VERSION_CODE,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 84.dp),
        )
    }
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
                onUserClick = onUserClick,
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
                onUserClick = onUserClick,
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
                onUserClick = onUserClick,
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
                onForgetProfile = onForgetProfile,
                onUserClick = onUserClick,
            )
        }
        Screen.Search -> {
            val vm: SearchViewModel = viewModel(key = "search-${session?.userId}", factory = viewModelFactory { SearchViewModel(app.repo) })
            SearchScreen(
                viewModel = vm,
                userName = userName,
                onTabSelected = onTabSelected,
                onItemClick = { push(Screen.Details(it.seriesOrSelfId())) },
                onUserClick = onUserClick,
            )
        }
        is Screen.Details -> {
            val vm: DetailsViewModel = viewModel(key = "details-${session?.userId}-${screen.itemId}-${screen.focusEpisodeId}", factory = viewModelFactory { DetailsViewModel(app.repo) })
            LaunchedEffect(screen.itemId, screen.focusEpisodeId) { vm.load(screen.itemId, screen.focusEpisodeId) }
            DetailsScreen(
                viewModel = vm,
                onPlay = { vm.invalidate(); push(Screen.Player(it)) },
                onItemClick = { push(Screen.Details(it.seriesOrSelfId())) },
                onBack = { pop() },
                onActor = { id, name -> push(Screen.Actor(id, name)) },
                onTrailer = { t ->
                    when (t) {
                        is tv.jetflix.app.data.TrailerSource.Local -> { vm.invalidate(); push(Screen.Player(t.item)) }
                        is tv.jetflix.app.data.TrailerSource.YouTube -> push(Screen.Trailer(t.videoId, t.title))
                    }
                },
            )
        }
        is Screen.Actor -> {
            val vm: tv.jetflix.app.ui.actor.ActorViewModel = viewModel(key = "actor-${session?.serverUrl}-${session?.userId}-${screen.personId}", factory = viewModelFactory { tv.jetflix.app.ui.actor.ActorViewModel(app.repo) })
            vm.onOpenTitle = { push(Screen.Details(it.Id)) }
            tv.jetflix.app.ui.actor.ActorScreen(viewModel = vm, personId = screen.personId, name = screen.name)
        }
        is Screen.Trailer -> {
            tv.jetflix.app.ui.player.YouTubeTrailerScreen(videoId = screen.videoId, title = screen.title, onClose = { pop() })
        }
        is Screen.Player -> Unit // drawn above, so it stays alive under the actor page
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
