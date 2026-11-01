package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * User report on build-47 (TV): "when I select the continueing watching on something and back up,
 * it's scrolling up to the the banner, it should go to the last place" - and "not happening on all".
 * Reproduces the Back-from-details return paths with the same dispose/restore mechanics the app uses
 * (SaveableStateProvider keyed by the current screen, as MainActivity does) and real D-pad events.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvBackFocusReproTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun cwEp(i: Int) = BaseItemDto(
        Id = "e$i", Name = "CW Episode $i", Type = "Episode",
        SeriesId = "s1", SeriesName = "Demo Show", SeriesPrimaryImageTag = "t",
        ParentIndexNumber = 1, IndexNumber = i,
        BackdropImageTags = listOf("b"),
        UserData = UserItemDataDto(Played = false, PlayedPercentage = 30.0, PlaybackPositionTicks = 1_000_000L),
    )
    private val hero = BaseItemDto(Id = "h1", Name = "Hero Movie", Type = "Movie", ImageTags = mapOf("Primary" to "t"), BackdropImageTags = listOf("b"))

    private lateinit var repo: JellyfinRepository

    @Before fun signInDummy() {
        SessionManager.signIn(Session("http://127.0.0.1:9", "u1", "Tester", "tok", "dev"))
        repo = JellyfinRepository()
    }

    private fun stateWith(cw: List<BaseItemDto>) = HomeUiState(
        loading = false, hero = hero, heroes = listOf(hero),
        sections = listOf(HomeSection("Continue Watching", cw, wide = true)),
    )

    private val screen = mutableStateOf("home")
    private val currentState = mutableStateOf(stateWith((1..6).map { cwEp(it) }))

    private fun compose() {
        rule.setContent {
            JetflixTheme {
                val holder = rememberSaveableStateHolder()
                holder.SaveableStateProvider(screen.value) {
                    if (screen.value == "home") {
                        HomeContent(
                            state = currentState.value, repo = repo, userName = "Tester",
                            onTabSelected = {}, onItemClick = { screen.value = "details" },
                            onPlay = {}, onUserClick = {},
                            heroIntervalMs = 3_600_000L,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().focusable())
                    }
                }
            }
        }
        idle()
        println("AFTER COMPOSE focused: " + focusedDump())
    }

    private fun idle() { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun key(k: Key) { rule.onAllNodes(isRoot())[0].performKeyInput { pressKey(k) }; idle() }

    private fun focusedDump(): String =
        rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" | ") { n ->
            n.config.filter { it.key.name == "ContentDescription" || it.key.name == "Text" }
                .joinToString(",") { "${it.key.name}=${it.value}" }.ifEmpty { "(no cd/text)" }
        }

    private fun focusCwCard(index: Int) {
        key(Key.DirectionDown) // first press enters Home at the banner Play
        key(Key.DirectionDown) // banner -> first CW card
        println("AFTER 2xDOWN focused: " + focusedDump())
        repeat(index) { key(Key.DirectionRight) }
        println("FOCUS before details: ${focusedDump()}")
        key(Key.DirectionCenter) // open details
        println("AFTER CLICK screen=${screen.value} focused: ${focusedDump()}")
    }

    /** Control path: content unchanged across the details round trip - the same card must regain focus. */
    @Test fun backToUnchangedCwRestoresFocusedCard() {
        compose()
        focusCwCard(2)
        screen.value = "home" // Back pops to Home
        idle()
        println("AFTER BACK focused: ${focusedDump()}")
        rule.waitUntil(5_000) { idle(); focusedHas("Demo Show") }
    }

    /** Reported path: Continue Watching changed while on details (progress sync) - the focused item is gone. */
    @Test fun backToChangedCwDoesNotJumpToBanner() {
        compose()
        focusCwCard(2)
        // Progress sync while away: the watched episode left the row, the rest shifted up.
        currentState.value = stateWith(listOf(1, 2, 4, 5, 6).map { cwEp(it) })
        screen.value = "home"
        idle()
        println("AFTER BACK (item removed) focused: ${focusedDump()}")
        key(Key.DirectionDown) // the user's next remote press
        println("AFTER NEXT KEY focused: ${focusedDump()}")
        // Bug signature: focus lands on the banner Play and the list animates to the top.
        // Correct behavior: focus stays/lands in the CW row (the "last place"), never the banner.
        val bannerFocused = rule.onAllNodes(isFocused()).fetchSemanticsNodes().any { n ->
            n.config.map { it.value.toString() }.any { it.contains("Play") }
        }
        org.junit.Assert.assertFalse("Back from details must not land on the banner", bannerFocused)
    }

    private fun movie(prefix: String, i: Int) = BaseItemDto(
        Id = prefix + i, Name = "Movie $prefix$i", Type = "Movie",
        ImageTags = mapOf("Primary" to "t"), BackdropImageTags = listOf("b"),
    )
    private fun cwRow() = HomeSection("Continue Watching", (1..6).map { cwEp(it) }, wide = true)
    private fun movieRow(prefix: String) = HomeSection("Movies", (1..6).map { movie(prefix, it) }, wide = false)
    private fun stateOf(sections: List<HomeSection>) = HomeUiState(
        loading = false, hero = hero, heroes = listOf(hero), sections = sections,
    )
    /** True when the focus owner is the card with [sub] in its text/cd, or any ancestor of the
     *  owner carries it - tv Cards focus an inner node, so isFocused alone misses the wrapper. */
    private fun focusedHas(sub: String): Boolean {
        for (node in rule.onAllNodes(isFocused()).fetchSemanticsNodes()) {
            var cur: androidx.compose.ui.semantics.SemanticsNode? = node
            while (cur != null) {
                if (cur!!.config.any { it.value.toString().contains(sub) }) return true
                cur = try { cur!!.parent } catch (_: Exception) { null }
            }
        }
        return false
    }
    private fun focusCard(downs: Int, rights: Int) {
        repeat(downs) { key(Key.DirectionDown) } // 1: banner Play, then one row per press
        repeat(rights) { key(Key.DirectionRight) }
        key(Key.DirectionCenter)
    }

    /** Non-CW control: an unchanged library row must also restore its exact card after Back. */
    @Test fun backToUnchangedLibraryRowRestoresFocusedCard() {
        currentState.value = stateOf(listOf(cwRow(), movieRow("m")))
        compose()
        focusCard(3, 2) // banner -> CW -> Movies, third card
        screen.value = "home"
        idle()
        println("AFTER BACK (library unchanged) focused: " + focusedDump())
        rule.waitUntil(5_000) { idle(); focusedHas("Movie m3") }
    }

    /** The remembered card left a NON-first row: fallback must land on the same row, not the banner. */
    @Test fun backToRemovedLibraryItemLandsOnSameRow() {
        currentState.value = stateOf(listOf(cwRow(), movieRow("m")))
        compose()
        focusCard(3, 2)
        currentState.value = stateOf(listOf(cwRow(), HomeSection("Movies", listOf(1, 2, 4, 5, 6).map { movie("m", it) }, wide = false)))
        screen.value = "home"
        idle()
        println("AFTER BACK (library item removed) focused: " + focusedDump())
        rule.waitUntil(5_000) { idle(); focusedHas("Movie m") }
        org.junit.Assert.assertFalse("row fallback must not land on the banner", focusedHas("Play"))
    }

    /** The whole remembered row is gone: designed fallback is top + banner, never a focusless screen. */
    @Test fun backToRemovedEntireRowLandsOnBanner() {
        compose()
        focusCwCard(2)
        currentState.value = stateOf(listOf(movieRow("m"))) // the CW row disappeared entirely
        screen.value = "home"
        idle()
        println("AFTER BACK (row removed) focused: " + focusedDump())
        rule.waitUntil(5_000) { idle(); focusedHas("Play") }
    }

    /** Jellyfin allows duplicate library names: restore/fallback must hit the SECOND "Movies" row. */
    @Test fun backToDuplicateTitleRowLandsOnSecondRow() {
        currentState.value = stateOf(listOf(cwRow(), movieRow("m"), movieRow("k")))
        compose()
        focusCard(4, 2) // banner -> CW -> Movies -> Movies#1, third card (k3)
        currentState.value = stateOf(listOf(cwRow(), movieRow("m"), HomeSection("Movies", listOf(1, 2, 4, 5, 6).map { movie("k", it) }, wide = false)))
        screen.value = "home"
        idle()
        println("AFTER BACK (duplicate title) focused: " + focusedDump())
        rule.waitUntil(5_000) { idle(); focusedHas("Movie k") }
        org.junit.Assert.assertFalse("duplicate-title fallback must not land on the first same-named row", focusedHas("Movie m"))
        org.junit.Assert.assertFalse("duplicate-title fallback must not land on the banner", focusedHas("Play"))
    }

    /** Horizontal restore: a card scrolled right inside the row must be re-found after Back. */
    @Test fun backToFarRightCwCardRestoresIt() {
        compose()
        focusCwCard(4)
        screen.value = "home"
        idle()
        println("AFTER BACK (far right) focused: ${focusedDump()}")
        rule.waitUntil(5_000) { idle(); focusedHas("Demo Show") }
    }
}
