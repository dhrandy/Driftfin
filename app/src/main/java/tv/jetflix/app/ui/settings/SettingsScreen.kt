package tv.jetflix.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

@Composable
fun SettingsScreen(
    userName: String?,
    serverUrl: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onSignOut: () -> Unit,
    onForgetProfile: () -> Unit = {},
    onUserClick: () -> Unit = {},
    /** Tests inject a flow pointed at a local server; the app builds the real one. */
    updateFlow: tv.jetflix.app.update.UpdateFlow? = null,
) {
    val autoplay by AppSettings.autoplayNext.collectAsState()
    val myList by AppSettings.showMyList.collectAsState()
    val watchlist by AppSettings.showWatchlist.collectAsState()
    val nextUp by AppSettings.showNextUp.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Opens on "Download" when the startup notice already found the build, so installing is one OK
    // press; without a known update the row still checks first and downloads on the second press.
    var update by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<tv.jetflix.app.update.UpdateUi>(
            tv.jetflix.app.update.UpdateNoticeState.available?.let { tv.jetflix.app.update.UpdateUi.Available(it) }
                ?: tv.jetflix.app.update.UpdateUi.Idle,
        )
    }
    val flow = remember {
        updateFlow ?: tv.jetflix.app.update.UpdateFlow(
            updater = tv.jetflix.app.update.Updater(),
            installedBuild = tv.jetflix.app.BuildConfig.VERSION_CODE,
            installed = { tv.jetflix.app.update.AndroidUpdate.installed(context) },
            readApk = { tv.jetflix.app.update.AndroidUpdate.readApk(context, it) },
            install = { tv.jetflix.app.update.AndroidUpdate.install(context, it) },
            cacheFile = tv.jetflix.app.update.AndroidUpdate.cacheFile(context),
        )
    }
    // Opened with no known update (the startup notice ran before it existed): look once so the row reads Download.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (update is tv.jetflix.app.update.UpdateUi.Idle) {
            update = tv.jetflix.app.update.UpdateUi.Checking
            update = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { flow.refreshOnOpen(tv.jetflix.app.update.UpdateUi.Idle) }
        }
    }
    SettingsContent(
        autoplay, myList, nextUp, userName, serverUrl,
        { AppSettings.setAutoplayNext(!autoplay) },
        { AppSettings.setShowMyList(!myList) },
        { AppSettings.setShowNextUp(!nextUp) },
        onTabSelected, onSignOut,
        excludeHomeAnimation = AppSettings.excludeHomeAnimation.collectAsState().value,
        onToggleExcludeHomeAnimation = { AppSettings.setExcludeHomeAnimation(!AppSettings.excludeHomeAnimation.value) },
        onForgetProfile = onForgetProfile,
        onUserClick = onUserClick,
        posterSize = AppSettings.posterSize.collectAsState().value,
        onCyclePosterSize = { AppSettings.setPosterSize(AppSettings.posterSize.value.next()) },
        continueWatchingPosters = AppSettings.continueWatchingPosters.collectAsState().value,
        onToggleContinueWatchingPosters = { AppSettings.setContinueWatchingPosters(!AppSettings.continueWatchingPosters.value) },
        pauseCast = AppSettings.pauseCast.collectAsState().value,
        showWatchlist = watchlist,
        onToggleWatchlist = { AppSettings.setShowWatchlist(!watchlist) },
        onTogglePauseCast = { AppSettings.setPauseCast(!AppSettings.pauseCast.value) },
        skipIntro = AppSettings.skipIntro.collectAsState().value,
        onToggleSkipIntro = { AppSettings.setSkipIntro(!AppSettings.skipIntro.value) },
        update = update,
        onUpdateClick = {
            val cur = update
            if (cur !is tv.jetflix.app.update.UpdateUi.Checking && cur !is tv.jetflix.app.update.UpdateUi.Downloading) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (cur is tv.jetflix.app.update.UpdateUi.Available) {
                        update = tv.jetflix.app.update.UpdateUi.Downloading
                        update = flow.downloadAndInstall(cur.info)
                    } else {
                        update = tv.jetflix.app.update.UpdateUi.Checking
                        update = flow.check()
                    }
                }
            }
        },
        titleLogos = AppSettings.titleLogos.collectAsState().value,
        onToggleTitleLogos = { AppSettings.setTitleLogos(!AppSettings.titleLogos.value) },
        formatBadges = AppSettings.formatBadges.collectAsState().value,
        onToggleFormatBadges = { AppSettings.setFormatBadges(!AppSettings.formatBadges.value) },
        startupWall = AppSettings.startupWall.collectAsState().value,
        onToggleStartupWall = { AppSettings.setStartupWall(!AppSettings.startupWall.value) },
        heroRatings = AppSettings.heroRatings.collectAsState().value,
        onToggleHeroRatings = { AppSettings.setHeroRatings(!AppSettings.heroRatings.value) },
        searchRecommendations = AppSettings.searchRecommendations.collectAsState().value,
        onToggleSearchRecommendations = { AppSettings.setSearchRecommendations(!AppSettings.searchRecommendations.value) },
        liveBlur = AppSettings.liveBlur.collectAsState().value,
        onToggleLiveBlur = { AppSettings.setLiveBlur(!AppSettings.liveBlur.value) },
        buttonSound = AppSettings.buttonSound.collectAsState().value,
        onCycleButtonSound = {
            val next = AppSettings.buttonSound.value.next()
            AppSettings.setButtonSound(next)
            tv.jetflix.app.ui.components.ButtonSounds.preview(next)
        },
    )
}

@Composable
fun SettingsContent(
    autoplay: Boolean,
    showMyList: Boolean,
    showNextUp: Boolean,
    userName: String?,
    serverUrl: String?,
    onToggleAutoplay: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleNextUp: () -> Unit,
    onTabSelected: (JetflixTab) -> Unit,
    onSignOut: () -> Unit,
    onUserClick: () -> Unit = {},
    posterSize: tv.jetflix.app.data.PosterSize = tv.jetflix.app.data.PosterSize.Medium,
    onCyclePosterSize: () -> Unit = {},
    buttonSound: tv.jetflix.app.ui.components.ButtonSound = tv.jetflix.app.ui.components.ButtonSound.Off,
    onCycleButtonSound: () -> Unit = {},
    liveBlur: Boolean = true,
    onToggleLiveBlur: () -> Unit = {},
    continueWatchingPosters: Boolean = false,
    onToggleContinueWatchingPosters: () -> Unit = {},
    pauseCast: Boolean = true,
    onTogglePauseCast: () -> Unit = {},
    skipIntro: Boolean = true,
    onToggleSkipIntro: () -> Unit = {},
    onForgetProfile: () -> Unit = {},
    update: tv.jetflix.app.update.UpdateUi = tv.jetflix.app.update.UpdateUi.Idle,
    onUpdateClick: () -> Unit = {},
    titleLogos: Boolean = true,
    onToggleTitleLogos: () -> Unit = {},
    formatBadges: Boolean = true,
    onToggleFormatBadges: () -> Unit = {},
    startupWall: Boolean = true,
    onToggleStartupWall: () -> Unit = {},
    showWatchlist: Boolean = true,
    onToggleWatchlist: () -> Unit = {},
    heroRatings: Boolean = true,
    onToggleHeroRatings: () -> Unit = {},
    excludeHomeAnimation: Boolean = false,
    onToggleExcludeHomeAnimation: () -> Unit = {},
    searchRecommendations: Boolean = true,
    onToggleSearchRecommendations: () -> Unit = {},
) {
    val menuBack = tv.jetflix.app.ui.components.rememberMenuBack()
    val first = remember { FocusRequester() }
    // Captured at composition: arriving by a tab sweep keeps focus on the tab until Down or OK (NavSweep is
    // cleared by the handoff, so reading it live here races and either steals focus or defers it forever).
    val openedBySweep = remember { tv.jetflix.app.ui.components.NavSweep.active }
    LaunchedEffect(Unit) { if (!openedBySweep) try { first.requestFocus() } catch (_: Exception) { } }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        val (uValue, uDetail) = updateTexts(update)
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 92.dp, bottom = 24.dp)) {
            Text("Settings", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            // Two columns on a TV screen: playback and Home behavior on the left, the look of the app on the
            // right. Down walks one column, Left/Right moves between them; the account actions stay pinned below.
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Updates", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Check for updates", uDetail, true, onUpdateClick, Modifier.focusProperties { up = menuBack.focus }.focusRequester(first), valueText = uValue)
                    Text("Playback", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Autoplay next episode", "Starts the next episode after a 5 second countdown.", autoplay, onToggleAutoplay)
                    SettingRow("Cast while paused", "Shows the cast along the bottom when you pause. Press Down to pick an actor and OK to see their movies on your server.", pauseCast, onTogglePauseCast)
                    SettingRow("Skip Intro button", "Shows a Skip Intro button in the player when your Jellyfin server has marked an intro. It only skips when you press OK.", skipIntro, onToggleSkipIntro)
                    Text("Home and discovery", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Show My List on Home", "My List uses your Jellyfin favorites for this user.", showMyList, onToggleMyList)
                    SettingRow("Show Next Up on Home", "Jellyfin's next unwatched episode of series you follow.", showNextUp, onToggleNextUp)
                    SettingRow("Exclude animated movies from Home banner", "Hide movies tagged Animation in the Home banner only. Library rows are unchanged.", excludeHomeAnimation, onToggleExcludeHomeAnimation)
                    SettingRow("Age rating and score on Home", "Shows the age rating and community score under the title on the Home banner.", heroRatings, onToggleHeroRatings)
                    SettingRow("Recommended rows on Search", "Shows recommended movies and shows on the Search page before you type.", searchRecommendations, onToggleSearchRecommendations)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Appearance", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Poster size", "Small, Medium or Large cards on Home and in libraries. Press OK to change.", true, onCyclePosterSize, Modifier.focusProperties { up = menuBack.focus }, valueText = posterSize.label)
                    SettingRow("Continue Watching art", "Scene images (the wide look) or posters for the Continue Watching row. Press OK to change.", true, onToggleContinueWatchingPosters, valueText = if (continueWatchingPosters) "Posters" else "Scene images")
                    SettingRow("Title logos", "Shows a show's own title lettering on Home and Details when Jellyfin has it. Falls back to plain text.", titleLogos, onToggleTitleLogos)
                    SettingRow("HDR and Dolby Vision tags", "Shows HDR, HDR10, HDR10+ or Dolby Vision on a movie's Details page when the file says it has them. It shows what the file contains, not what your TV is outputting.", formatBadges, onToggleFormatBadges)
                    SettingRow("Poster wall on startup", "Shows the Driftfin poster wall while Home loads the first time. Off goes straight to the ordinary empty Home.", startupWall, onToggleStartupWall)
                    Text("My Stuff", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Watchlist", "Star movies and shows to watch later. Adds the star button on their pages and the Watchlist section under My Stuff.", showWatchlist, onToggleWatchlist)
                    Text("Sound", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Button sound", "A quiet sound when you move and when you select. Press OK to try the next one.", true, onCycleButtonSound, valueText = buttonSound.label)
                    Text("Performance", style = JetflixType.body, color = JetflixColors.TextSecondary)
                    SettingRow("Live blur", "On is the normal look. Off swaps the live blur for a pre-softened picture, to see whether blur is what slows scrolling. Takes effect when you open the next screen.", liveBlur, onToggleLiveBlur)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Account", style = JetflixType.body, color = JetflixColors.TextSecondary)
            Text(
                "Signed in as ${userName ?: "-"}${if (serverUrl != null) " on $serverUrl" else ""}",
                style = JetflixType.caption,
                color = JetflixColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            // Account actions side by side: the pinned footer stays one row tall so both columns keep their room.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSignOut,
                    shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        focusedContainerColor = JetflixColors.Red,
                        focusedContentColor = Color.White,
                    ),
                ) { Text("Switch profile") }
                Button(
                    onClick = onForgetProfile,
                    shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        focusedContainerColor = JetflixColors.Red,
                        focusedContentColor = Color.White,
                    ),
                ) { Text("Sign out and forget this profile") }
            }
        }
        TopNavBar(
            solid = true,
            selectedFocus = menuBack.focus,
            downFocus = first,
            onNavFocusChange = { menuBack.navHasFocus = it },
            selected = JetflixTab.SETTINGS,
            userName = userName,
            onTabSelected = onTabSelected,
            onUserClick = onUserClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun SettingRow(title: String, detail: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, valueText: String? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        colors = ButtonDefaults.colors(
            containerColor = JetflixColors.Surface,
            contentColor = Color.White,
            focusedContainerColor = JetflixColors.SurfaceLight,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 20.dp)) {
                Text(title, style = JetflixType.body)
                Text(detail, style = JetflixType.caption, color = JetflixColors.TextSecondary)
            }
            Text(valueText ?: if (on) "On" else "Off", style = JetflixType.body, color = if (on) JetflixColors.Red else JetflixColors.TextMuted)
        }
    }
}

/** Row value and explanation for each update step. Updates only ever start when the user presses OK. */
fun updateTexts(u: tv.jetflix.app.update.UpdateUi): Pair<String, String> {
    val base = "Looks for a newer Driftfin release on GitHub. Nothing is downloaded until you press OK on an available update, and Android asks you to confirm the install."
    return when (u) {
        is tv.jetflix.app.update.UpdateUi.Idle -> "Check" to base
        is tv.jetflix.app.update.UpdateUi.Checking -> "Checking..." to base
        is tv.jetflix.app.update.UpdateUi.UpToDate -> "Up to date" to "You have the latest build (${u.build})."
        is tv.jetflix.app.update.UpdateUi.Available -> "Download" to "Build ${u.info.build} is available. Press OK to download it, verify it, and hand it to Android's installer."
        is tv.jetflix.app.update.UpdateUi.Downloading -> "Downloading..." to "Downloading and verifying the update."
        is tv.jetflix.app.update.UpdateUi.ReadyToInstall -> "Installer open" to "Build ${u.build} was verified and passed to Android's installer. Confirm there to finish."
        is tv.jetflix.app.update.UpdateUi.Error -> "Retry" to u.message
    }
}
