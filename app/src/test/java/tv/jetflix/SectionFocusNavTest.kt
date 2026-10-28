package tv.jetflix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.Watchlist
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.library.LibraryPrefsStore
import tv.jetflix.app.ui.library.LibraryViewModel
import tv.jetflix.app.ui.library.MySection
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Randy on build 25: sections switch on hover "just like the top navigation" and "make sure the
 * navigation works right on those". Matrix: across all three on focus alone, Down into the Sort
 * row and the grid, Up back to the section row, empty sections, and no accidental item opening.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SectionFocusNavTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val favs = (0 until 12).map { i -> base[i % base.size].copy(Id = "fv$i", Name = "Fav %02d".format(i), Type = "Movie") }
    private val starred = (0 until 12).map { i -> base[i % base.size].copy(Id = "wl$i", Name = "Watch %02d".format(i), Type = "Movie") }
    private val boxes = (0 until 6).map { i -> base[i % base.size].copy(Id = "bx$i", Name = "Set %02d".format(i), Type = "BoxSet") }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun focusedText(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    @Test fun hoverAcrossAllThreeThenDownIntoControlsAndItemsAndBackUp() {
        var state by mutableStateOf(LibraryUiState(loading = false, section = MySection.Favorites, items = favs))
        val clicks = mutableListOf<String>()
        rule.setContent {
            JetflixTheme {
                LibraryContent(state, repo, JetflixTab.MY_LIST, "demo", {}, { clicks += it.Id }, {},
                    onSection = { sec ->
                        state = state.copy(section = sec, items = when (sec) {
                            MySection.Favorites -> favs; MySection.Watchlist -> starred; MySection.Collections -> boxes
                        })
                    })
            }
        }
        rule.waitForIdle()
        val tabs = setOf("Home", "Shows", "Movies", "My Stuff", "Settings", "demo")
        repeat(8) { if (focusedText() !in tabs) press(Key.DirectionUp) }
        press(Key.DirectionDown) // nav -> selected section button (Favorites)
        assertTrue("section row, was: ${focusedText()}", focusedText().contains("Favorites"))

        press(Key.DirectionRight) // hover -> Watchlist, no OK
        rule.waitForIdle()
        assertTrue("hover must select Watchlist, was: ${focusedText()}", focusedText().contains("Watchlist"))
        assertTrue("Watchlist items must be showing", state.section == MySection.Watchlist)

        press(Key.DirectionRight) // hover -> Collections
        rule.waitForIdle()
        assertTrue("hover must select Collections, was: ${focusedText()}", focusedText().contains("Collections"))

        press(Key.DirectionRight) // edge: no wrap back to Favorites (falls into the grid, same as before hover)
        rule.waitForIdle()
        assertTrue("Right at the last section must not wrap to Favorites, was: ${focusedText()}", !focusedText().contains("Favorites"))
        assertTrue("the section stays Collections, was: ${state.section}", state.section == MySection.Collections)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }; rule.waitForIdle() // back up toward the rows
        repeat(4) { if (!focusedText().contains("Collections")) press(Key.DirectionUp) }
        assertTrue("must be able to get back to Collections, was: ${focusedText()}", focusedText().contains("Collections"))

        press(Key.DirectionDown) // into the Sort row
        rule.waitForIdle()
        assertTrue("Down must reach the Sort row, was: ${focusedText()}", focusedText().startsWith("Sort:"))

        press(Key.DirectionDown) // into the grid
        rule.waitForIdle()
        assertTrue("Down must reach a card, was: ${focusedText()}", focusedText().contains("Set 00"))

        press(Key.DirectionUp) // grid -> Sort row
        rule.waitForIdle()
        assertTrue("Up must return to the Sort row, was: ${focusedText()}", focusedText().startsWith("Sort:"))
        press(Key.DirectionUp) // Sort row -> section row
        rule.waitForIdle()
        assertTrue("Up must return to the section row, was: ${focusedText()}", focusedText().contains("Collections"))

        assertTrue("hovering sections must never open an item, opened: $clicks", clicks.isEmpty())
    }

    @Test fun emptySectionStillNavigatesDownToControlsAndBackUp() {
        var state by mutableStateOf(LibraryUiState(loading = false, section = MySection.Watchlist, items = starred))
        rule.setContent {
            JetflixTheme {
                LibraryContent(state, repo, JetflixTab.MY_LIST, "demo", {}, {}, {},
                    onSection = { sec -> state = state.copy(section = sec, items = if (sec == MySection.Collections) emptyList() else starred) })
            }
        }
        rule.waitForIdle()
        val tabs = setOf("Home", "Shows", "Movies", "My Stuff", "Settings", "demo")
        repeat(8) { if (focusedText() !in tabs) press(Key.DirectionUp) }
        press(Key.DirectionDown)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle() // hover -> Collections (empty)
        assertTrue("hover must select Collections, was: ${focusedText()}", focusedText().contains("Collections"))
        press(Key.DirectionDown) // empty grid: Down must still land on the Sort row, not die
        rule.waitForIdle()
        assertTrue("Down on an empty section must reach the Sort row, was: ${focusedText()}", focusedText().startsWith("Sort:"))
        press(Key.DirectionUp)
        rule.waitForIdle()
        assertTrue("Up must return to Collections, was: ${focusedText()}", focusedText().contains("Collections"))
    }
}

/** Rapid hover sweeps: only the latest section load may write state - no stale or error overwrites. */
@RunWith(RobolectricTestRunner::class)
class SectionRapidSwitchTest {
    private lateinit var server: MockWebServer
    private val repo = JellyfinRepository()

    private class MemStore : LibraryPrefsStore {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        Watchlist.resetForTest()
        Watchlist.init(RuntimeEnvironment.getApplication())
        SessionManager.signIn(Session("http://placeholder", "u1", "n", "t", "d"))
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path!!
                    return when {
                        path.startsWith("/Items/Filters") -> MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}""")
                        path.contains("BoxSet") -> MockResponse().setBody("""{"Items":[{"Id":"c1","Name":"Collection One","Type":"BoxSet"}],"TotalRecordCount":1}""")
                        else -> MockResponse().setBody("""{"Items":[{"Id":"f1","Name":"Favorite One","Type":"Movie"}],"TotalRecordCount":1}""")
                    }
                }
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "n", "t", "d"))
    }

    @After fun tearDown() { server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain(); Watchlist.resetForTest() }

    private fun kotlinx.coroutines.test.TestScope.await(done: () -> Boolean) {
        var tries = 0
        while (!done() && tries++ < 300) { advanceUntilIdle(); Thread.sleep(25) }
    }

    @Test fun rapidSweepAcrossSectionsLandsOnTheLastOneCleanly() = runTest {
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)   // empty watchlist here: no fetch
        vm.setSection(JetflixTab.MY_LIST, MySection.Collections) // fetch BoxSets
        vm.setSection(JetflixTab.MY_LIST, MySection.Favorites)   // cancelled by...
        vm.setSection(JetflixTab.MY_LIST, MySection.Collections) // ...this final one
        await { vm.state.value.items.isNotEmpty() || vm.state.value.error != null }
        assertEquals(MySection.Collections, vm.state.value.section)
        assertEquals(null, vm.state.value.error)
        assertEquals(listOf("c1"), vm.state.value.items.map { it.Id })
    }
}
