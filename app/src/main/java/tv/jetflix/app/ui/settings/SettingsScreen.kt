package tv.jetflix.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType
import kotlin.math.cos
import kotlin.math.sin

/** Settings groups in the left rail, in remote order. Focusing a group shows its pane on the right. */
enum class SettingsCategory(val label: String, val subtitle: String) {
    Updates("Updates", "Keep Driftfin current."),
    Playback("Playback", "What happens while you watch."),
    HomeDiscovery("Home & discovery", "What appears on Home and Search."),
    Appearance("Appearance", "How posters, logos and badges look across the app."),
    MyStuff("My Stuff", "Your watchlist."),
    Sound("Sound", "Remote control sounds."),
    Performance("Performance", "Measurement switches."),
    Account("Account", "Who is signed in on this TV."),
}

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
    /** Updates pane: replays the installed build's What's new card. */
    onWhatsNewClick: () -> Unit = {},
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
            tv.jetflix.app.update.UpdateNoticeState.available.value?.let { tv.jetflix.app.update.UpdateUi.Available(it) }
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
            canInstall = { tv.jetflix.app.update.AndroidUpdate.installPermissionProblem(context) },
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
        pauseInfo = AppSettings.pauseInfo.collectAsState().value,
        showWatchlist = watchlist,
        onToggleWatchlist = { AppSettings.setShowWatchlist(!watchlist) },
        onTogglePauseCast = { AppSettings.setPauseCast(!AppSettings.pauseCast.value) },
        onTogglePauseInfo = { AppSettings.setPauseInfo(!AppSettings.pauseInfo.value) },
        skipIntro = AppSettings.skipIntro.collectAsState().value,
        onToggleSkipIntro = { AppSettings.setSkipIntro(!AppSettings.skipIntro.value) },
        update = update,
        onWhatsNewClick = onWhatsNewClick,
        updateNotices = AppSettings.updateNotices.collectAsState().value,
        onToggleUpdateNotices = { AppSettings.setUpdateNotices(!AppSettings.updateNotices.value) },
        onUpdateClick = {
            val cur = update
            if (cur !is tv.jetflix.app.update.UpdateUi.Checking && cur !is tv.jetflix.app.update.UpdateUi.Downloading) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (cur is tv.jetflix.app.update.UpdateUi.Available) {
                        update = tv.jetflix.app.update.UpdateUi.Downloading
                        update = flow.downloadAndInstall(cur.info)
                    } else if (cur is tv.jetflix.app.update.UpdateUi.ReadyToInstall) {
                        // The APK is already downloaded and verified: reopen the installer, never re-download.
                        update = flow.installAgain(cur)
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
        buildLabel = "Driftfin TV build ${tv.jetflix.app.BuildConfig.VERSION_CODE}",
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
    pauseInfo: Boolean = true,
    onTogglePauseInfo: () -> Unit = {},
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
    /** Tests open a specific pane; the screen opens on Updates. */
    initialCategory: SettingsCategory = SettingsCategory.Updates,
    /** Shown at the bottom of the pane; empty hides it (tests pass nothing). */
    buildLabel: String = "",
    /** Updates pane: replays the installed build's What's new card. */
    onWhatsNewClick: () -> Unit = {},
    /** Updates pane: hides/shows the update popup + red dot (checks still run on startup). */
    updateNotices: Boolean = true,
    onToggleUpdateNotices: () -> Unit = {},
) {
    val menuBack = tv.jetflix.app.ui.components.rememberMenuBack()
    // Rail and pane focus anchors: Left from any pane row returns to the rail, Right from the rail
    // enters the pane's first control. Focusing a control never changes it; only OK does.
    val paneFirst = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    var category by remember { mutableStateOf(initialCategory) }
    // Captured at composition: arriving by a tab sweep keeps focus on the tab until Down or OK (NavSweep is
    // cleared by the handoff, so reading it live here races and either steals focus or defers it forever).
    val openedBySweep = remember { tv.jetflix.app.ui.components.NavSweep.active }
    LaunchedEffect(Unit) { if (!openedBySweep) try { paneFirst.requestFocus() } catch (_: Exception) { } }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        val (uValue, uDetail) = updateTexts(update)
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 92.dp, bottom = 24.dp)) {
            Text("Settings", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Category rail: moving onto a group swaps the pane, like the top menu switching tabs.
                Column(
                    Modifier.width(252.dp).fillMaxHeight()
                        .background(Color(0xFF191919), RoundedCornerShape(12.dp)).padding(8.dp)
                        // Some TVs overscan or render taller text; the rail scrolls so the last
                        // group (Account) is never cut off, and focus auto-reveals it.
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsCategory.values().forEachIndexed { index, cat ->
                        RailItem(
                            cat = cat,
                            selected = cat == category,
                            onPreview = { category = cat },
                            onEnter = { category = cat; try { paneFirst.requestFocus() } catch (_: Exception) { } },
                            modifier = Modifier
                                .then(if (cat == category) Modifier.focusRequester(railFocus) else Modifier)
                                .focusProperties {
                                    right = paneFirst
                                    if (index == 0) up = menuBack.focus
                                },
                        )
                    }
                }
                // The selected group's pane.
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(category.label, style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
                    Text(category.subtitle, style = JetflixType.caption, color = JetflixColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // First control of each pane also carries Up-to-menu and the pane entry anchor.
                        fun firstRow(last: Boolean = false): Modifier = Modifier
                            .focusProperties {
                                up = menuBack.focus; left = railFocus
                                if (last) down = androidx.compose.ui.focus.FocusRequester.Cancel
                            }
                            .focusRequester(paneFirst)
                        fun nextRow(last: Boolean = false): Modifier = Modifier.focusProperties {
                            left = railFocus
                            // Down on the last row must stay put; only Left returns to the rail (TV46 report).
                            if (last) down = androidx.compose.ui.focus.FocusRequester.Cancel
                        }
                        when (category) {
                            SettingsCategory.Updates -> {
                                SettingRow("Check for updates", uDetail, true, onUpdateClick, firstRow(), valueText = uValue)
                                SettingRow("What's new in this build", "Replays the change list shown after this update installed.", false, onWhatsNewClick, nextRow(), valueText = "Replay")
                                SettingRow("Update notifications", "Shows the startup popup and the red dot by your name when a newer build exists. The startup check always runs and you can check here any time.", updateNotices, onToggleUpdateNotices, nextRow(last = true))
                            }
                            SettingsCategory.Playback -> {
                                SettingRow("Autoplay next episode", "Starts the next episode after a 5 second countdown.", autoplay, onToggleAutoplay, firstRow())
                                SettingRow("Cast while paused", "Shows the cast along the bottom when you pause. Press Down to pick an actor and OK to see their movies on your server.", pauseCast, onTogglePauseCast, nextRow())
                                SettingRow("Episode and movie info while paused", "Shows the story summary and the Up next line when you pause. The title and player controls always stay.", pauseInfo, onTogglePauseInfo, nextRow())
                                SettingRow("Skip Intro button", "Shows a Skip Intro button in the player when your Jellyfin server has marked an intro. It only skips when you press OK.", skipIntro, onToggleSkipIntro, nextRow(last = true))
                            }
                            SettingsCategory.HomeDiscovery -> {
                                SettingRow("Show My List on Home", "My List uses your Jellyfin favorites for this user.", showMyList, onToggleMyList, firstRow())
                                SettingRow("Show Next Up on Home", "Jellyfin's next unwatched episode of series you follow.", showNextUp, onToggleNextUp, nextRow())
                                SettingRow("Exclude animated movies from Home banner", "Hide movies tagged Animation in the Home banner only. Library rows are unchanged.", excludeHomeAnimation, onToggleExcludeHomeAnimation, nextRow())
                                SettingRow("Age rating and score on Home", "Shows the age rating and community score under the title on the Home banner.", heroRatings, onToggleHeroRatings, nextRow())
                                SettingRow("Recommended rows on Search", "Shows recommended movies and shows on the Search page before you type.", searchRecommendations, onToggleSearchRecommendations, nextRow(last = true))
                            }
                            SettingsCategory.Appearance -> {
                                SettingRow("Poster size", "Small, Medium or Large cards on Home and in libraries. Press OK to change.", true, onCyclePosterSize, firstRow(), valueText = posterSize.label)
                                SettingRow("Continue Watching art", "Scene images (the wide look) or posters for the Continue Watching row. Press OK to change.", true, onToggleContinueWatchingPosters, nextRow(), valueText = if (continueWatchingPosters) "Posters" else "Scene images")
                                SettingRow("Title logos", "Shows a show's own title lettering on Home and Details when Jellyfin has it. Falls back to plain text.", titleLogos, onToggleTitleLogos, nextRow())
                                SettingRow("HDR and Dolby Vision tags", "Shows HDR, HDR10, HDR10+ or Dolby Vision on a movie's Details page when the file says it has them. It shows what the file contains, not what your TV is outputting.", formatBadges, onToggleFormatBadges, nextRow())
                                SettingRow("Startup strip on launch", "Shows the logo and poster strip while Home loads the first time. Off goes straight to the ordinary empty Home.", startupWall, onToggleStartupWall, nextRow(last = true))
                            }
                            SettingsCategory.MyStuff -> {
                                SettingRow("Watchlist", "Star movies and shows to watch later. Adds the star button on their pages and the Watchlist section under My Stuff.", showWatchlist, onToggleWatchlist, firstRow(last = true))
                            }
                            SettingsCategory.Sound -> {
                                SettingRow("Button sound", "A quiet sound when you move and when you select. Press OK to try the next one.", true, onCycleButtonSound, firstRow(last = true), valueText = buttonSound.label)
                            }
                            SettingsCategory.Performance -> {
                                SettingRow("Live blur", "On is the normal look. Off swaps the live blur for a pre-softened picture, to see whether blur is what slows scrolling. Takes effect when you open the next screen.", liveBlur, onToggleLiveBlur, firstRow(last = true))
                            }
                            SettingsCategory.Account -> {
                                Row(
                                    Modifier.fillMaxWidth().background(JetflixColors.Surface, RoundedCornerShape(6.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Signed in as ${userName ?: "-"}", style = JetflixType.body, color = JetflixColors.TextPrimary)
                                        Text(
                                            if (serverUrl != null) "Server connected: $serverUrl" else "No server connected",
                                            style = JetflixType.caption, color = JetflixColors.TextSecondary,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = onSignOut,
                                        modifier = firstRow(last = true),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                                        colors = ButtonDefaults.colors(
                                            containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                            contentColor = Color.White,
                                            focusedContainerColor = JetflixColors.Red,
                                            focusedContentColor = Color.White,
                                        ),
                                    ) { Text("Switch profile") }
                                    Button(
                                        onClick = onForgetProfile,
                                        modifier = Modifier.focusProperties { down = androidx.compose.ui.focus.FocusRequester.Cancel },
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                                        colors = ButtonDefaults.colors(
                                            containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                                            contentColor = Color.White,
                                            focusedContainerColor = JetflixColors.Red,
                                            focusedContentColor = Color.White,
                                        ),
                                    ) { Text("Sign out and forget this profile") }
                                }
                            }
                        }
                    }
                    if (buildLabel.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(buildLabel, style = JetflixType.caption, color = JetflixColors.TextMuted)
                    }
                }
            }
        }
        TopNavBar(
            solid = true,
            selectedFocus = menuBack.focus,
            downFocus = paneFirst,
            onNavFocusChange = { menuBack.navHasFocus = it },
            selected = JetflixTab.SETTINGS,
            userName = userName,
            onTabSelected = onTabSelected,
            onUserClick = onUserClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** One rail entry: icon plus label. Previews its pane on focus, enters the pane on OK. */
@Composable
private fun RailItem(
    cat: SettingsCategory,
    selected: Boolean,
    onPreview: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onEnter,
        modifier = modifier.fillMaxWidth().onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onPreview()
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) JetflixColors.Surface else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = JetflixColors.SurfaceLight,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(3.dp).height(20.dp)
                    .background(if (selected) JetflixColors.Red else Color.Transparent, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
            CategoryIcon(cat, tint = if (selected || focused) JetflixColors.Red else JetflixColors.TextMuted)
            Spacer(Modifier.width(12.dp))
            Text(cat.label, style = JetflixType.body, color = if (selected || focused) Color.White else JetflixColors.TextSecondary)
        }
    }
}

/** Simple line icons for the rail, drawn in the app's accent or muted gray. */
@Composable
private fun CategoryIcon(cat: SettingsCategory, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val w = s * 0.11f
        when (cat) {
            SettingsCategory.Updates -> {
                drawArc(tint, 30f, 300f, false, topLeft = Offset(s * 0.14f, s * 0.14f), size = Size(s * 0.72f, s * 0.72f), style = Stroke(w))
                val head = Path().apply {
                    moveTo(s * 0.90f, s * 0.28f); lineTo(s * 0.70f, s * 0.26f); lineTo(s * 0.84f, s * 0.50f); close()
                }
                drawPath(head, tint)
            }
            SettingsCategory.Playback -> {
                val p = Path().apply {
                    moveTo(s * 0.32f, s * 0.16f); lineTo(s * 0.32f, s * 0.84f); lineTo(s * 0.82f, s * 0.50f); close()
                }
                drawPath(p, tint)
            }
            SettingsCategory.HomeDiscovery -> {
                val roof = Path().apply {
                    moveTo(s * 0.10f, s * 0.52f); lineTo(s * 0.50f, s * 0.14f); lineTo(s * 0.90f, s * 0.52f); close()
                }
                drawPath(roof, tint)
                drawRect(tint, topLeft = Offset(s * 0.24f, s * 0.52f), size = Size(s * 0.52f, s * 0.36f))
            }
            SettingsCategory.Appearance -> {
                drawCircle(tint, radius = s * 0.38f, style = Stroke(w))
                drawCircle(tint, radius = s * 0.15f)
            }
            SettingsCategory.MyStuff -> {
                val cx = s / 2f
                val cy = s / 2f
                val star = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) s * 0.46f else s * 0.19f
                    val a = Math.toRadians((i * 36 - 90).toDouble())
                    val x = (cx + r * cos(a)).toFloat()
                    val y = (cy + r * sin(a)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, tint)
            }
            SettingsCategory.Sound -> {
                drawRect(tint, topLeft = Offset(s * 0.10f, s * 0.36f), size = Size(s * 0.18f, s * 0.28f))
                val horn = Path().apply {
                    moveTo(s * 0.28f, s * 0.32f); lineTo(s * 0.52f, s * 0.16f); lineTo(s * 0.52f, s * 0.84f); lineTo(s * 0.28f, s * 0.68f); close()
                }
                drawPath(horn, tint)
                drawArc(tint, -55f, 110f, false, topLeft = Offset(s * 0.44f, s * 0.24f), size = Size(s * 0.46f, s * 0.52f), style = Stroke(w))
            }
            SettingsCategory.Performance -> {
                drawArc(tint, 150f, 240f, false, topLeft = Offset(s * 0.12f, s * 0.16f), size = Size(s * 0.76f, s * 0.76f), style = Stroke(w))
                drawLine(tint, Offset(s * 0.50f, s * 0.56f), Offset(s * 0.70f, s * 0.30f), strokeWidth = w)
            }
            SettingsCategory.Account -> {
                drawCircle(tint, radius = s * 0.16f, center = Offset(s * 0.5f, s * 0.30f))
                drawArc(tint, 180f, 180f, true, topLeft = Offset(s * 0.16f, s * 0.52f), size = Size(s * 0.68f, s * 0.68f))
            }
        }
    }
}

/** TV toggle: red track when on, neutral track when off, white knob. */
@Composable
private fun SettingsSwitch(on: Boolean) {
    Box(
        Modifier.width(44.dp).height(24.dp)
            .background(if (on) JetflixColors.Red else Color(0xFF444444), RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        Box(
            Modifier.size(18.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
internal fun SettingRow(title: String, detail: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, valueText: String? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
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
            if (valueText != null) {
                Text(valueText, style = JetflixType.body, color = JetflixColors.TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text("›", style = JetflixType.body, color = JetflixColors.TextMuted)
            } else {
                SettingsSwitch(on)
            }
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
        is tv.jetflix.app.update.UpdateUi.ReadyToInstall -> "Installer open" to "Build ${u.build} was verified and passed to Android's installer. Confirm there to finish, or press OK to reopen it."
        is tv.jetflix.app.update.UpdateUi.Error -> "Retry" to u.message
    }
}
