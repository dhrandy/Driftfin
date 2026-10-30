package tv.jetflix.phone

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

/** The 10/29 dim report: episode 1 ends, auto-next pops and re-pushes the player route in one
 *  navigation. The popped screen's dispose runs AFTER the new screen's setup (proven against the
 *  unguarded pattern: events were "add:2 -> clear:1" and the flag ended OFF), so the second episode
 *  played with no keep-awake and the display timeout dimmed the screen. These fake destinations use
 *  the same PlayerWindowTokens guard as PlayerScreen/LandscapeWhilePlaying. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhonePlayerAwakeRaceTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val events = CopyOnWriteArrayList<String>()

    private fun flagOn() = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Test fun keepScreenOnSurvivesEpisodeToEpisodeNavigation() {
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") { Box(Modifier.fillMaxSize()) }
                composable("player/{id}") { backStack ->
                    val pid = backStack.arguments?.getString("id").orEmpty()
                    DisposableEffect(pid) {
                        val token = Any()
                        PlayerWindowTokens.window.set(token)
                        rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); events += "add:$pid"
                        onDispose {
                            if (PlayerWindowTokens.window.compareAndSet(token, null)) {
                                rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); events += "clear:$pid"
                            } else events += "skipped-clear:$pid"
                        }
                    }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { nav.navigate("player/1") }
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("flag on during episode 1", flagOn())
        events.clear()
        rule.runOnUiThread { nav.navigate("player/2") { popUpTo("player/{id}") { inclusive = true } } }
        Thread.sleep(1500); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("KEEP_SCREEN_ON lost across auto-next navigation. Events: $events", flagOn())
        assertTrue("the popped episode's clear was skipped. Events: $events", events.contains("skipped-clear:1"))
        // Leaving the player entirely still restores normal screen timeout.
        rule.runOnUiThread { nav.popBackStack() }
        Thread.sleep(1000); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("flag cleared after leaving the player. Events: $events", !flagOn())
    }
}
