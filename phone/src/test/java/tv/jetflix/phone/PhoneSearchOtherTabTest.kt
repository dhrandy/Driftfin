package tv.jetflix.phone

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owner report (10/31-11/1, phone build-6 confirmed installed): from an INITIALLY EMPTY Search -
 * "I didn't even type anything" - selecting OTHER tabs "just releases and stays on the search";
 * "it's reloading the search". No keyboard/config event is injected: these tests do exactly what
 * the owner described - open empty Search, tap another bottom tab, require the app goes and stays.
 *
 * v5 fixes two harness confounds found with the verifier's measured v3 run:
 *  - The What's New AlertDialog shows on EVERY fresh install (last_seen_build defaults to 0),
 *    adding a second Compose root to every test. It is dismissed here via setLastSeenBuild.
 *  - openEmptySearch's old await could be satisfied by Home's hero (also sortBy=CommunityRating,
 *    limit=50); the Search pair (limit=40) could still be in flight when later taps landed. The
 *    Search-pair witness is now scoped to limit=40 and awaits BOTH fallbacks.
 * Every click re-fetches its node and every navigation asserts the selected tab flips, so a
 * failure distinguishes "click never navigated" from "witness absent".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSearchOtherTabTest {
    val compose = createEmptyComposeRule()
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(compose).around(NonIdleProbe { compose.mainClock })
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    private val slowSuggestions = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        // Experiment variant: let Compose's own idling drive the clock (waitUntil), same timeout.
        try { compose.waitUntil(timeoutMs) { condition() }; return } catch (e: Throwable) { requested += "WAITUNTIL threw: " + e.javaClass.simpleName + " " + (e.message ?: "").take(160) }
        // Failure-only thread dump (v5-sandbox addition): where every non-idle thread is stuck.
        val threads = try {
            Thread.getAllStackTraces().entries.filter { (t, _) -> t.name.contains("main", true) || t.name.contains("Test worker") || t.name.contains("OkHttp") || t.name.contains("DefaultDispatcher") || t.name.contains("Robolectric", true) }
                .joinToString("\n") { (t, st) -> "THREAD " + t.name + " state=" + t.state + "\n  " + st.take(25).joinToString("\n  ") }
        } catch (e: Exception) { "thread dump failed: $e" }
        requested += "THREADDUMP:\n" + threads
        // Failure-only diagnostic (adopted from the verifier's local v3 dump): request log + all roots.
        val tree = try {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            val n = roots.fetchSemanticsNodes().size
            val t = (0 until n).joinToString("\n--- ROOT %d ---\n") { i -> roots[i].printToString() }
            if (t.length > 12000) t.take(12000) + "\n...[truncated]" else t
        } catch (e: Exception) { "tree dump failed: $e" }
        assertTrue("condition not met within ${timeoutMs}ms\nREQUESTED:\n" + requested.joinToString("\n") + "\nSEMANTICS:\n" + tree, condition())
    }

    /** Home's hero queries sortBy=CommunityRating with limit=50; Search's recommended fallback uses limit=40. */
    private fun searchRecommendedFetches() = requested.count { it.contains("sortBy=CommunityRating") && it.contains("limit=40") }
    private fun suggestionsRequests() = requested.count { it.contains("Items/Suggestions") }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += path
                if (path.contains("/Images/")) {
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "Copper Skies", "2005")))
                }
                if (path.startsWith("/Persons")) {
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}""")
                }
                if (path.contains("Items/Suggestions") && slowSuggestions.get()) {
                    return MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("""{"Items":[],"TotalRecordCount":0}""")
                        .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                }
                val body = when {
                    path.contains("Items/Suggestions") -> """{"Items":[],"TotalRecordCount":0}"""
                    path.contains("includeItemTypes=Series") && path.contains("limit=40") -> """{"Items":[{"Id":"s1","Name":"The Silent Meridian","Type":"Series","ProductionYear":2007,"ImageTags":{"Primary":"p7"}}],"TotalRecordCount":1}"""
                    path.contains("/Items") -> """{"Items":[{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"}}],"TotalRecordCount":1}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
        // Fresh installs default last_seen_build to 0, which pops the What's New AlertDialog (a second
        // Compose root) over every test. Dismiss it permanently for this run.
        tv.jetflix.phone.data.PhoneSettings.setLastSeenBuild(BuildConfig.VERSION_CODE)
    }

    // Every test starts MainActivity (Home refresh loop and all). Destroy it so its Compose root and loops do not outlive the test in the shared JVM.
    private var mainController: org.robolectric.android.controller.ActivityController<MainActivity>? = null
    private fun launchMain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        mainController = controller // tracked before setup(), so a partial setup is still cleaned up
        controller.setup()
    }

    @After fun tearDown() {
        try { mainController?.destroy() } finally { mainController = null; server.shutdown() }
    }

    /** Opens the Search tab and waits until BOTH recommended fetches (movie + series fallback pair)
     *  have fired, so later taps never land while the first Search composition is still settling. */
    private fun openEmptySearch() {
        launchMain()
        awaitReal(30_000) { compose.onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search").performClick()
        awaitReal(30_000) { searchRecommendedFetches() >= 2 }
        compose.waitForIdle()
        assertSelectedTab("Search")
    }

    private fun assertSelectedTab(label: String) {
        awaitReal(30_000) {
            compose.onAllNodes(isSelected().and(hasText(label)), useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settleAndAssertNoSearchReload(pairBefore: Int) {
        repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50) }
        assertTrue(
            "Search must not reload after leaving it (no new recommended pair)\nREQUESTED:\n" + requested.joinToString("\n"),
            searchRecommendedFetches() == pairBefore,
        )
        assertTrue(
            "Search field must be gone after leaving Search",
            compose.onAllNodesWithText("Search movies, shows and actors").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test fun myStuffTabFromEmptySearchNavigatesAndStays() {
        openEmptySearch()
        val pairBefore = searchRecommendedFetches()
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        settleAndAssertNoSearchReload(pairBefore)
    }

    @Test fun settingsTabFromEmptySearchNavigatesAndStays() {
        openEmptySearch()
        val pairBefore = searchRecommendedFetches()
        compose.onNodeWithText("Settings").performClick()
        assertSelectedTab("Settings")
        awaitReal(30_000) { compose.onAllNodesWithText("App updates").fetchSemanticsNodes().isNotEmpty() }
        settleAndAssertNoSearchReload(pairBefore)
    }

    @Test fun homeTabFromEmptySearchNavigatesAndStays() {
        openEmptySearch()
        val pairBefore = searchRecommendedFetches()
        compose.onNodeWithText("Home").performClick()
        assertSelectedTab("Home")
        awaitReal(30_000) { compose.onAllNodesWithText("DRIFTFIN").fetchSemanticsNodes().isNotEmpty() }
        settleAndAssertNoSearchReload(pairBefore)
    }

    @Test fun focusedEmptySearchFieldOtherTabStillNavigates() {
        // The on-device sequence that opens the keyboard: open Search, tap the BOX (no typing -
        // the owner reported typing nothing), then pick another tab. Robolectric has no IME
        // window, so this covers focus/nav/state only, not keyboard geometry.
        openEmptySearch()
        compose.onNodeWithText("Search movies, shows and actors").performClick()
        val pairBefore = searchRecommendedFetches()
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        settleAndAssertNoSearchReload(pairBefore)
    }

    @Test fun otherTabTapWhileRecommendedFetchInFlight() {
        // The owner's "sometimes" window: leave Search WHILE its recommended fetch is still running.
        launchMain()
        awaitReal(30_000) { compose.onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search").performClick()
        awaitReal(30_000) { compose.onAllNodesWithText("Search movies, shows and actors").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        val pairAtArrival = searchRecommendedFetches()
        repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50) }
        assertTrue(
            "Search must not reload after leaving it mid-fetch\nREQUESTED:\n" + requested.joinToString("\n"),
            searchRecommendedFetches() <= pairAtArrival,
        )
    }

    @Test fun leavingSearchCancelsInFlightRecommendedFetch() {
        // Owner's lead is the recommendations. The exit path must CANCEL them: suggestions is
        // delayed 5s so the fetch is genuinely in flight when the tap leaves Search. The Series
        // suggestions call and the rating fallback must NEVER fire afterwards.
        slowSuggestions.set(true)
        launchMain()
        awaitReal(30_000) { compose.onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        val suggestionsBaseline = suggestionsRequests()
        compose.onNodeWithText("Search").performClick()
        awaitReal(30_000) { suggestionsRequests() > suggestionsBaseline }
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        val ratingBaseline = searchRecommendedFetches()
        val end = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(100) }
        assertTrue(
            "leaving Search mid-fetch must cancel it: the Series suggestions call never started\nREQUESTED:\n" + requested.joinToString("\n"),
            suggestionsRequests() == suggestionsBaseline + 1,
        )
        assertTrue(
            "leaving Search mid-fetch must cancel it: the rating fallback never fired\nREQUESTED:\n" + requested.joinToString("\n"),
            searchRecommendedFetches() == ratingBaseline,
        )
    }

    @Test fun rapidAlternatingTabTapsSettleOnLastTap() {
        // The owner's "stuck, then releases": alternating tabs, each click re-fetched and each
        // landing asserted. The mid-sequence Search tap's reload is by-design; the LAST tap must win.
        openEmptySearch()
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search").performClick() // by-design reload mid-sequence
        assertSelectedTab("Search")
        awaitReal(30_000) { searchRecommendedFetches() >= 4 } // second pair
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        val pairAtFinalArrival = searchRecommendedFetches()
        repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50) }
        assertTrue(
            "after the last tap wins, no further Search reload may fire\nREQUESTED:\n" + requested.joinToString("\n"),
            searchRecommendedFetches() <= pairAtFinalArrival,
        )
    }
}
