package tv.jetflix.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
) {
    val autoplay by AppSettings.autoplayNext.collectAsState()
    val myList by AppSettings.showMyList.collectAsState()
    val nextUp by AppSettings.showNextUp.collectAsState()
    SettingsContent(
        autoplay, myList, nextUp, userName, serverUrl,
        { AppSettings.setAutoplayNext(!autoplay) },
        { AppSettings.setShowMyList(!myList) },
        { AppSettings.setShowNextUp(!nextUp) },
        onTabSelected, onSignOut,
        posterSize = AppSettings.posterSize.collectAsState().value,
        onCyclePosterSize = { AppSettings.setPosterSize(AppSettings.posterSize.value.next()) },
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
    posterSize: tv.jetflix.app.data.PosterSize = tv.jetflix.app.data.PosterSize.Medium,
    onCyclePosterSize: () -> Unit = {},
) {
    val menuBack = tv.jetflix.app.ui.components.rememberMenuBack()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 110.dp).width(760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
            SettingRow("Autoplay next episode", "Starts the next episode after a 5 second countdown.", autoplay, onToggleAutoplay, Modifier.focusRequester(first))
            SettingRow("Show My List on Home", "My List uses your Jellyfin favorites for this user.", showMyList, onToggleMyList)
            SettingRow("Poster size", "Small, Medium or Large cards on Home and in libraries. Press OK to change.", true, onCyclePosterSize, valueText = posterSize.label)
            SettingRow("Show Next Up on Home", "Jellyfin's next unwatched episode of series you follow.", showNextUp, onToggleNextUp)
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "Signed in as ${userName ?: "-"}${if (serverUrl != null) " on $serverUrl" else ""}",
                style = JetflixType.caption,
                color = JetflixColors.TextSecondary,
            )
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.colors(
                    containerColor = JetflixColors.SurfaceLight.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    focusedContainerColor = JetflixColors.Red,
                    focusedContentColor = Color.White,
                ),
            ) { Text("Switch profile / sign out") }
        }
        TopNavBar(
            solid = true,
            selectedFocus = menuBack.focus,
            onNavFocusChange = { menuBack.navHasFocus = it },
            selected = JetflixTab.SETTINGS,
            userName = userName,
            onTabSelected = onTabSelected,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SettingRow(title: String, detail: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, valueText: String? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = JetflixColors.Surface,
            contentColor = Color.White,
            focusedContainerColor = JetflixColors.SurfaceLight,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = JetflixType.body)
                Text(detail, style = JetflixType.caption, color = JetflixColors.TextSecondary)
            }
            Text(valueText ?: if (on) "On" else "Off", style = JetflixType.body, color = if (on) JetflixColors.Red else JetflixColors.TextMuted)
        }
    }
}
