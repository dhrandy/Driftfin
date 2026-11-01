package tv.jetflix.phone

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the REAL private goTo() from Shell.kt (reflection) on a NavHost with the shell's route names and
 * start destination. Screens are stubs; mystuff holds a rememberSaveable counter so tab state preservation is observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneShellGoToTest {
    @get:Rule val compose = createComposeRule()
    private var nav: NavHostController? = null
    private val goToMethod = Class.forName("tv.jetflix.phone.ui.ShellKt")
        .getDeclaredMethod("goTo", NavHostController::class.java, String::class.java).apply { isAccessible = true }

    @Composable private fun Graph() {
        val n = rememberNavController(); nav = n
        NavHost(n, startDestination = "home") {
            composable("home") { Text("H") }
            composable("search") { Text("S") }
            composable("mystuff") { var c by rememberSaveable { mutableIntStateOf(0) }; counter = { c }; bump = { c += 5 }; Text("M$c") }
            composable("settings") { Text("T") }
            composable("actor") { Text("A") }
            composable("details") { Text("D") }
        }
    }
    private var counter: () -> Int = { -1 }
    private var bump: () -> Unit = {}

    private fun go(route: String) { compose.runOnUiThread { goToMethod.invoke(null, nav, route) }; compose.waitForIdle() }
    private fun route(): String { var r = ""; compose.runOnUiThread { r = nav!!.currentBackStackEntry?.destination?.route.orEmpty() }; return r }
    private fun stack(): String { var s = ""; compose.runOnUiThread { s = nav!!.currentBackStack.value.mapNotNull { it.destination.route }.filter { it != "" }.joinToString(">") }; return s }
    private fun topId(): String { var s = ""; compose.runOnUiThread { s = nav!!.currentBackStackEntry?.id.orEmpty() }; return s }
    private fun back(): Boolean { var ok = false; compose.runOnUiThread { ok = nav!!.popBackStack() }; compose.waitForIdle(); return ok }
    // Search opens actor pages with a plain nav.navigate (SearchScreen.onOpenPerson), not goTo.
    private fun pushActor() { compose.runOnUiThread { nav!!.navigate("actor") }; compose.waitForIdle() }
    // Home rows open details with a plain nav.navigate too (HomeScreen onOpen).
    private fun pushDetails() { compose.runOnUiThread { nav!!.navigate("details") }; compose.waitForIdle() }

    @Test fun everyTabCallLandsOnRequestedRoute() {
        compose.setContent { Graph() }; compose.waitForIdle()
        val seq = listOf("search","home","search","mystuff","search","mystuff","home","search","settings","search","mystuff","search","home","mystuff","home","settings","search","search","home")
        for ((i, r) in seq.withIndex()) { go(r); assertEquals("step $i goTo($r) stack=${stack()}", r, route()) }
    }

    @Test fun searchRetapCreatesFreshEntryAndKeepsTabsBelow() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); go("search"); val first = topId()
        go("search")
        assertEquals("search", route()); assertNotEquals("retap must re-create Search (empty)", first, topId())
        assertEquals("home>mystuff>search", stack().removePrefix(">"))
    }

    @Test fun mystuffStatePreservedAcrossSearchAndHome() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); compose.runOnUiThread { bump() }; compose.waitForIdle()
        go("search"); go("mystuff")
        assertEquals("mystuff state survives a Search round trip", 5, counter())
        go("home"); go("mystuff")
        assertEquals("mystuff state restored after Home round trip (saveState/restoreState)", 5, counter())
    }

    @Test fun backFromTabsAndSearch() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("search"); go("mystuff")
        assertEquals("home>mystuff", stack().removePrefix(">")); assertEquals(true, back()); assertEquals("home", route())
        go("mystuff"); go("search")
        assertEquals(true, back()); assertEquals("Back from Search above a tab returns to that tab", "mystuff", route())
    }

    @Test fun tabSwitchDropsSearchSubtreeAboveSearch() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("search"); pushActor()
        assertEquals("home>search>actor", stack())
        go("home")
        assertEquals("home", route()); assertEquals("home", stack())
        go("search"); pushActor()
        go("mystuff")
        assertEquals("mystuff", route()); assertEquals("home>mystuff", stack())
    }

    @Test fun actorBackReturnsToSearchUntouched() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("search"); pushActor()
        assertEquals(true, back()); assertEquals("search", route()); assertEquals("home>search", stack())
    }

    @Test fun realTabStateSurvivesSearchSubtreeDrop() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); compose.runOnUiThread { bump() }; compose.waitForIdle()
        go("search"); pushActor()
        go("mystuff")
        assertEquals("mystuff state restored after its Search subtree was dropped", 5, counter())
    }

    @Test fun homeAlwaysLandsHomeFromDirectlyPushedPages() {
        compose.setContent { Graph() }; compose.waitForIdle()
        pushDetails(); pushActor()
        assertEquals("home>details>actor", stack())
        go("home")
        assertEquals("home", route()); assertEquals("home", stack())
        pushDetails()
        go("home")
        assertEquals("home", route()); assertEquals("home", stack())
    }

    @Test fun homeRetapKeepsSameEntry() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("search"); go("home")
        val id = topId()
        go("home")
        assertEquals("home", route()); assertEquals("Home retap must keep the entry (no refetch)", id, topId())
    }

    @Test fun homeKeepsMyStuffSubtreeRestoration() {
        // Existing behavior, kept: with an actor page pushed above My Stuff, a Home round trip still
        // restores the My Stuff subtree (actor page included) and its saved state. The Home fix only
        // drops pushed pages when they sit directly on bare Home.
        compose.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); compose.runOnUiThread { bump() }; compose.waitForIdle()
        pushActor()
        go("home"); go("mystuff")
        assertEquals("mystuff state restored after a Home round trip with an actor page pushed", 5, counter())
        assertTrue("actor page above mystuff is restored, as before the fix", stack().contains("actor"))
    }

    @Test fun processRestoreKeepsTabsNavigable() {
        val tester = StateRestorationTester(compose)
        tester.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); compose.runOnUiThread { bump() }; compose.waitForIdle(); go("search")
        tester.emulateSavedInstanceStateRestore(); compose.waitForIdle()
        assertEquals("search", route())
        go("home"); assertEquals("after restore Home from Search lands on Home", "home", route())
        go("search"); go("mystuff"); assertEquals("mystuff", route())
        assertEquals("mystuff state survives process restore + Search/tab sequence", 5, counter())
    }

    @Test fun homeKeepsSettingsSubtreeRestoration() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("settings"); pushActor()
        assertEquals("home>settings>actor", stack())
        go("home"); assertEquals("home", route())
        go("settings")
        assertEquals("settings subtree (actor page included) restored after Home round trip", "home>settings>actor", stack())
    }

    @Test fun homeFromBareHomeActorAfterEarlierTabVisitDropsPagesButKeepsTabState() {
        compose.setContent { Graph() }; compose.waitForIdle()
        go("mystuff"); compose.runOnUiThread { bump() }; compose.waitForIdle()
        go("home")
        pushDetails(); pushActor()
        go("home"); assertEquals("home", route()); assertEquals("home", stack())
        go("mystuff"); assertEquals("real tab state kept across the bare-Home page drop", 5, counter())
    }
}
