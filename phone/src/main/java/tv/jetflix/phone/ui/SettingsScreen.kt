package tv.jetflix.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.jetflix.phone.BuildConfig
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.update.AndroidUpdate
import tv.jetflix.phone.update.UpdateFlow
import tv.jetflix.phone.update.UpdateUi
import tv.jetflix.phone.update.Updater

/** Settings. Check for updates comes first, as on the TV app. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    val flow = remember {
        UpdateFlow(
            updater = Updater(), installedBuild = BuildConfig.VERSION_CODE,
            installed = { AndroidUpdate.installed(context) }, readApk = { AndroidUpdate.readApk(context, it) },
            install = { AndroidUpdate.install(context, it) }, cacheFile = AndroidUpdate.cacheFile(context),
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("App updates", fontSize = 16.sp, color = DriftfinColors.TextSecondary)
        Text("Installed: build ${BuildConfig.VERSION_CODE}", fontSize = 14.sp)
        val busy = ui is UpdateUi.Checking || ui is UpdateUi.Downloading
        Button(
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DriftfinColors.Red),
            onClick = {
                scope.launch {
                    ui = UpdateUi.Checking
                    ui = withContext(Dispatchers.IO) { flow.check() }
                }
            },
        ) { Text("Check for updates", fontSize = 16.sp) }
        when (val s = ui) {
            UpdateUi.Idle -> {}
            UpdateUi.Checking -> Text("Checking...")
            is UpdateUi.UpToDate -> Text("You have the latest build (${s.build}).")
            is UpdateUi.Available -> {
                Text("Build ${s.info.build} is available.")
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = { scope.launch { ui = UpdateUi.Downloading; ui = withContext(Dispatchers.IO) { flow.downloadAndInstall(s.info) } } },
                ) { Text("Download and install", fontSize = 16.sp) }
            }
            UpdateUi.Downloading -> Text("Downloading and checking the update...")
            is UpdateUi.ReadyToInstall -> Text("Build ${s.build} is ready. Confirm the install in the Android screen.")
            is UpdateUi.Error -> Text(s.message, color = androidx.compose.ui.graphics.Color(0xFFFF6E40))
        }
        val autoplay by tv.jetflix.phone.data.PhoneSettings.autoplayNext.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Autoplay next episode", fontSize = 16.sp)
                Text("Starts the next episode after a 5 second countdown.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = autoplay, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setAutoplayNext(it) })
        }
        val logos by tv.jetflix.phone.data.PhoneSettings.titleLogos.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Title logos", fontSize = 16.sp)
                Text("Show a title's logo on Home and Details when the server has one.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = logos, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setTitleLogos(it) })
        }
        val posterSize by tv.jetflix.phone.data.PhoneSettings.posterSize.collectAsState()
        OutlinedButton(
            onClick = { tv.jetflix.phone.data.PhoneSettings.setPosterSize(posterSize.next()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Poster size: ${posterSize.label}", fontSize = 16.sp) }
        val badges by tv.jetflix.phone.data.PhoneSettings.formatBadges.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Format badges", fontSize = 16.sp)
                Text("Show the 4K badge on covers.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = badges, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setFormatBadges(it) })
        }
        val heroRatings by tv.jetflix.phone.data.PhoneSettings.heroRatings.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hero ratings", fontSize = 16.sp)
                Text("Show the content rating on the Home hero.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = heroRatings, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setHeroRatings(it) })
        }
        val nextUp by tv.jetflix.phone.data.PhoneSettings.showNextUp.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next Up row", fontSize = 16.sp)
                Text("Show the Next Up row on Home.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = nextUp, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setShowNextUp(it) })
        }
        val favorites by tv.jetflix.phone.data.PhoneSettings.showFavorites.collectAsState()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Favorites row", fontSize = 16.sp)
                Text("Show the Favorites row on Home.", fontSize = 14.sp, color = DriftfinColors.TextSecondary)
            }
            androidx.compose.material3.Switch(checked = favorites, onCheckedChange = { tv.jetflix.phone.data.PhoneSettings.setShowFavorites(it) })
        }
        Text("Account", fontSize = 16.sp, color = DriftfinColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
        Text("Signed in as ${SessionManager.session.value?.userName.orEmpty()}", fontSize = 14.sp)
        var switchNote by remember { mutableStateOf<String?>(null) }
        var switching by remember { mutableStateOf(false) }
        val me = SessionManager.session.collectAsState().value
        val others = remember(me) { tv.jetflix.phone.data.ProfileSwitch.saved() }
        if (others.size > 1) {
            Text("Who's watching?", fontSize = 16.sp, color = DriftfinColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
            others.forEach { p ->
                val current = p.userId == me?.userId
                OutlinedButton(
                    enabled = !switching && !current,
                    onClick = {
                        switching = true; switchNote = "Switching..."
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { tv.jetflix.phone.data.ProfileSwitch.select(p) }
                            switching = false
                            switchNote = when (r) {
                                is tv.jetflix.phone.data.ProfileSwitch.Outcome.Expired -> "Your saved sign-in for ${r.name} expired. Sign out and sign in again."
                                is tv.jetflix.phone.data.ProfileSwitch.Outcome.Unreachable -> "Could not reach the server to switch to ${r.name}. Check the connection and try again."
                                else -> null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (current) "${p.userName} (signed in)" else p.userName, fontSize = 16.sp) }
            }
            switchNote?.let { Text(it, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFFFF6E40)) }
        }
        OutlinedButton(onClick = { Graph.signOut() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Sign out", fontSize = 16.sp) }
    }
}
