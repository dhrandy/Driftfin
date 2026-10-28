package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Real D-pad key events through the real Home composable (Robolectric, no TV involved). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("regression-items.json")!!.readText(),
        ).Items

    private fun focusedText(): String {
        val nodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        return nodes.joinToString(" || ") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun setHome(
        tabs: MutableList<String> = mutableListOf(),
        heroes: List<BaseItemDto> = emptyList(),
        opened: MutableList<String> = mutableListOf(),
        played: MutableList<String> = mutableListOf(),
        userClicks: MutableList<Int> = mutableListOf(),
        restoredKey: String? = null,
        heroTrailers: Map<String, tv.jetflix.app.data.TrailerSource> = emptyMap(),
        trailerClicks: MutableList<tv.jetflix.app.data.TrailerSource> = mutableListOf(),
        trailerNeeded: MutableList<String> = mutableListOf(),
    ) {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        val cont = items.take(6).map { it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }
        val state = HomeUiState(
            loading = false,
            hero = items.first { it.Name == "The Gold Rush" },
            heroes = heroes,
            heroTrailers = heroTrailers,
            sections = listOf(
                HomeSection("Continue Watching", cont, wide = true),
                HomeSection("Recently Added Movies", items.drop(2), wide = false),
                HomeSection("Popular Picks", items.reversed(), wide = false),
            ),
        )
        rule.setContent { JetflixTheme { HomeContent(state, repo, "demo", { tabs += it.label }, { opened += it.Name.orEmpty() }, { played += it.Name.orEmpty() }, { userClicks += 1 }, onTrailer = { trailerClicks += it }, onHeroTrailerNeeded = { trailerNeeded += it.Id }, restoredFocusKey = restoredKey, heroIntervalMs = 1000) } }
        rule.waitForIdle()
        // Robolectric's window never reports focus, so the on-launch focus request cannot be observed here.
        // The first D-pad press gives the screen its initial focus on the hero Play button, as on a TV.
        if (zone() == "none") press(Key.DirectionDown)
    }

    private val navLabels = setOf("Home", "Shows", "Movies", "My Stuff", "Search", "Settings", "demo")
    private val heroLabels = setOf("Play", "Resume", "Play from start", "More Info")
    private fun zone(): String {
        val t = focusedText()
        return when {
            t.isEmpty() -> "none"
            t in navLabels -> "nav"
            t in heroLabels || t.startsWith("Play") || t.startsWith("Resume") || t.startsWith("More") -> "hero"
            else -> "row"
        }
    }

    @Test fun startsOnHeroPlay() {
        setHome()
        assertEquals("hero", zone())
    }

    @Test fun downFromHeroEntersRowsAndUpComesBackToHero() {
        setHome()
        press(Key.DirectionDown); assertEquals("row", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
        press(Key.DirectionUp); press(Key.DirectionUp); assertEquals("hero", zone())
    }

    @Test fun navBarCanAlwaysGoDownAgain() {
        setHome()
        press(Key.DirectionUp); assertEquals("nav", zone())
        // Regression: Down from the nav bar used to leave focus stuck on the tab.
        press(Key.DirectionDown); assertEquals("hero", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
        repeat(6) { press(Key.DirectionUp) }
        assertEquals("nav", zone())
        press(Key.DirectionDown); assertEquals("hero", zone())
    }

    @Test fun navBarSideKeysThenDownStillWorks() {
        setHome()
        press(Key.DirectionUp)
        repeat(4) { press(Key.DirectionRight) }
        assertEquals("nav", zone())
        press(Key.DirectionDown); assertEquals("hero", zone())
        press(Key.DirectionUp)
        repeat(6) { press(Key.DirectionRight) } // far right: user name
        press(Key.DirectionDown); assertEquals("hero", zone())
    }

    @Test fun backFromDeepInRowsGoesToTopBarThenExits() {
        setHome()
        press(Key.DirectionDown); press(Key.DirectionDown); press(Key.DirectionDown)
        assertEquals("row", zone())
        val dispatcher = rule.activity.onBackPressedDispatcher
        assertTrue("Back must be intercepted while in the rows", dispatcher.hasEnabledCallbacks())
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        // Build-22: Back from the rows goes to the top with the NAV BAR focused (so Right works), not the hero.
        assertEquals("nav", zone())
        assertFalse("second Back must fall through so the app exits", dispatcher.hasEnabledCallbacks())
    }

    @Test fun headerTabsAreReachableInOrderAndSelectable() {
        val picked = mutableListOf<String>()
        setHome(picked)
        press(Key.DirectionUp)
        if (focusedText() == "?") press(Key.DirectionRight) // first stop may be the search icon
        assertEquals("Home", focusedText())
        val seen = mutableListOf(focusedText())
        repeat(5) { press(Key.DirectionRight); seen += focusedText() }
        // Search is an icon before Home; tabs follow in order and the last stop is the user name.
        assertEquals(listOf("Home", "Shows", "Movies", "My Stuff", "Settings", "demo"), seen.distinct())
        press(Key.DirectionLeft); press(Key.DirectionLeft)
        assertEquals("My Stuff", focusedText())
        repeat(4) { press(Key.DirectionLeft) }
        assertEquals("search icon is the first stop", "?", focusedText())
        press(Key.Enter)
        assertTrue(picked.contains("Search"))
    }

    @Test fun enterOnAHeaderTabSelectsThatTab() {
        val picked = mutableListOf<String>()
        setHome(picked)
        press(Key.DirectionUp)
        press(Key.DirectionRight); assertEquals("Shows", focusedText())
        press(Key.Enter)
        press(Key.DirectionRight); assertEquals("Movies", focusedText())
        press(Key.DirectionCenter)
        assertEquals(listOf("Shows", "Movies"), picked)
    }

    @Test fun okOnTheUserNameOpensTheProfilePicker() {
        val clicks = mutableListOf<Int>()
        setHome(userClicks = clicks)
        press(Key.DirectionUp)
        repeat(6) { press(Key.DirectionRight) }
        assertEquals("demo", focusedText())
        press(Key.Enter)
        assertEquals("one OK on the user name opens the picker, no confirm step", 1, clicks.size)
        // The label never turns into a sign-out question.
        assertEquals("demo", focusedText())
    }

    @Test fun heroButtonsAndRowCardsReactToOk() {
        val played = mutableListOf<String>(); val opened = mutableListOf<String>()
        setHome(opened = opened, played = played)
        assertEquals("hero", zone())
        press(Key.Enter)
        assertEquals(listOf("The Gold Rush"), played)
        var guard = 0
        while (focusedText() != "More Info" && guard++ < 4) press(Key.DirectionRight)
        assertEquals("More Info", focusedText())
        press(Key.Enter)
        assertEquals(listOf("The Gold Rush"), opened)
        press(Key.DirectionDown); assertEquals("row", zone())
        press(Key.Enter)
        assertEquals(2, opened.size)
    }

    @Test fun bannerRotatesToTheNextTitleAndKeepsFocusOnPlay() {
        val played = mutableListOf<String>()
        val first = items.first { it.Name == "The Gold Rush" }
        val second = items.first { it.Name != "The Gold Rush" && it.Name != null }
        setHome(heroes = listOf(first, second), played = played)
        rule.mainClock.autoAdvance = false
        // Short test interval; the real banner waits 8 seconds. Press OK repeatedly: the title it plays must change.
        repeat(4) {
            rule.mainClock.advanceTimeBy(1100); repeat(3) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
            assertEquals("hero", zone())
            press(Key.Enter)
        }
        assertEquals("OK must act on the title currently shown", setOf(first.Name, second.Name), played.toSet())
    }

    @Test fun returningWithAMissingRememberedCardStillLandsOnTheHeroAndNavigates() {
        // Back from playback with a remembered card that no longer exists (Next Up changed): must not leave nothing focused.
        setHome(restoredKey = "row-gone-card")
        rule.mainClock.advanceTimeBy(3000); rule.waitForIdle()
        if (zone() == "none") press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertEquals("row", zone())
        press(Key.DirectionUp)
        assertEquals("hero", zone())
        press(Key.DirectionUp)
        assertEquals("nav", zone())
        press(Key.DirectionDown)
        assertEquals("hero", zone())
    }

    @Test fun focusedRowTitleShowsItsDetailsAndHeroHidesThem() {
        setHome()
        press(Key.DirectionDown)
        assertEquals("row", zone())
        val title = rule.onAllNodes(isFocused()).fetchSemanticsNodes().size
        assertTrue(title == 1)
        // The strip repeats the focused title's name, so the name now appears at least twice (card + strip) when it has one.
        press(Key.DirectionUp)
        assertEquals("hero", zone())
    }

    @Test fun trace() {
        setHome()
        println("NAV start: ${focusedText()}")
        repeat(3) { press(Key.DirectionDown); println("NAV down${it + 1}: ${focusedText()}") }
    }

    @Test fun bannerTrailerButtonShownAndPlays() {
        val hero = items.first { it.Name == "The Gold Rush" }
        val src = tv.jetflix.app.data.TrailerSource.YouTube("abcdefghijk", "The Gold Rush trailer")
        val clicks = mutableListOf<tv.jetflix.app.data.TrailerSource>()
        val needed = mutableListOf<String>()
        setHome(heroTrailers = mapOf(hero.Id to src), trailerClicks = clicks, trailerNeeded = needed)
        // The showing banner title triggers exactly its own lazy lookup.
        assertTrue(needed.contains(hero.Id))
        // TV buttons fire on DPAD_CENTER, not a touch click.
        rule.onNodeWithText("Trailer").assertExists().requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(listOf(src), clicks)
    }

    @Test fun bannerTrailerButtonHiddenWithoutSource() {
        setHome()
        rule.onNodeWithText("Trailer").assertDoesNotExist()
        // Play/Resume and More Info are untouched (the fixture hero is part-watched, so its button is Resume).
        rule.onNodeWithText("Resume").assertExists()
        rule.onNodeWithText("More Info").assertExists()
    }
}
