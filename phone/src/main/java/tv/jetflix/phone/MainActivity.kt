package tv.jetflix.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinColors
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.DriftfinShell
import tv.jetflix.phone.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriftfinTheme {
                val session by SessionManager.session.collectAsState()
                Box(Modifier.fillMaxSize().background(DriftfinColors.Background)) {
                    if (session == null) LoginScreen() else {
                        androidx.compose.runtime.key(session?.userId) { DriftfinShell() }
                        var showNew by remember { mutableStateOf(tv.jetflix.phone.update.WhatsNew.shouldShow(BuildConfig.VERSION_CODE, tv.jetflix.phone.data.PhoneSettings.lastSeenBuild.value)) }
                        if (showNew) tv.jetflix.phone.update.WhatsNewDialog(tv.jetflix.phone.update.WhatsNew.notes(this@MainActivity)) {
                            tv.jetflix.phone.data.PhoneSettings.setLastSeenBuild(BuildConfig.VERSION_CODE); showNew = false
                        }
                    }
                    var crash by remember { mutableStateOf(CrashNote.read(this@MainActivity)) }
                    crash?.let { text ->
                        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).background(Color(0xEE000000)).padding(12.dp)) {
                            Text("The app closed unexpectedly last time. This note stays on your phone and has no server or login details:", color = Color.White, fontSize = 14.sp)
                            Text(text.take(700), color = Color(0xFFFF6E40), fontSize = 11.sp)
                            TextButton(onClick = { CrashNote.clear(this@MainActivity); crash = null }) { Text("Dismiss", fontSize = 16.sp) }
                        }
                    }
                }
            }
        }
    }
}
