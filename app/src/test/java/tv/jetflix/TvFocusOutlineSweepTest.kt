package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.WhatsNewCard

/** The build-50 white-outline-on-focus rule beyond Details: a non-selected top-nav tab, a library
 *  chip, and the What's new OK button all show the WHITE OUTLINE on a DARK fill when focused -
 *  actual pixels (the v1 constant-only check passed while focused Play was a white block).
 *  On top of that rule: the PRIMARY white actions (hero Play, details Play/Resume)
 *  and the selected top-nav tab KEEP the white pill with dark text while focused; the outline ring
 *  carries the focus cue there. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvFocusOutlineSweepTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val items = base.take(12).mapIndexed { i, it -> it.copy(Id = "fos$i", Name = "Title $i") }
    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }

    private fun pixelAt(cx: Int, cy: Int): Int {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp.getPixel(cx, cy)
    }

    /** Focused visuals scale ~1.1x past the semantics bounds, so the ring can sit just outside OR
     *  just inside the edge: scan a strip crossing each edge and require a white pixel in it, then
     *  require the fill zone between ring and glyphs to be DARK. */
    private fun assertFocusOutline(tag: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val cx = ((b.left + b.right) / 2).toInt()
        val cy = ((b.top + b.bottom) / 2).toInt()
        // tv-material scales the focused VISUAL ~1.1x past the semantics bounds, so the ring
        // lands ~5% of the button's own width/height OUTSIDE each edge: 18px out on a 360px-wide
        // hero button, far beyond any fixed band (Run10: hero Play left strip brightest r86 with
        // the ring plainly in the capture). Reach per edge from the actual bounds, inward +4.
        val outX = ((b.right - b.left) * 0.06f).toInt() + 4
        val outY = ((b.bottom - b.top) * 0.06f).toInt() + 4
        val strips = listOf(
            "left" to ((b.left - outX).toInt()..(b.left + 4).toInt()).map { it to cy },
            "right" to ((b.right - 4).toInt()..(b.right + outX).toInt()).map { it to cy },
            "top" to ((b.top - outY).toInt()..(b.top + 4).toInt()).map { cx to it },
            "bottom" to ((b.bottom - 4).toInt()..(b.bottom + outY).toInt()).map { cx to it },
        )
        for ((name, pts) in strips) {
            val best = pts.maxOf { pt -> android.graphics.Color.red(pixelAt(pt.first, pt.second)) }
            assertTrue("$tag focused outline is white at $name edge (brightest strip pixel r=$best)", best > 200)
        }
        for (frac in listOf(0.10f, 0.90f)) {
            val px = pixelAt((b.left + (b.right - b.left) * frac).toInt(), cy)
            val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
            assertTrue("$tag focused body is dark at ${(frac * 100).toInt()}% width (was rgb($r,$g,$bl))", r < 120 && g < 120 && bl < 120)
        }
    }


    /** The primary Play/Resume buttons and the selected top-nav tab must KEEP the
     *  white pill with dark text while focused; the white outline ring
     *  outside the pill is the focus cue. Fill bright at both flanks, dark glyphs in the text band. */
    private fun assertWhitePill(tag: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val cy = ((b.top + b.bottom) / 2).toInt()
        for (frac in listOf(0.10f, 0.90f)) {
            // A long label's dark glyphs can reach the 10%/90% column on the center row, so scan
            // the whole column inside the pill: the white fill must show somewhere along it.
            val x = (b.left + (b.right - b.left) * frac).toInt()
            val y0 = (b.top + (b.bottom - b.top) * 0.12f).toInt()
            val y1 = (b.bottom - (b.bottom - b.top) * 0.12f).toInt()
            val best = (y0..y1 step 2).maxOf { y -> android.graphics.Color.red(pixelAt(x, y)) }
            assertTrue("$tag keeps the white fill when focused at ${(frac * 100).toInt()}% width (brightest column pixel r=$best)", best > 200)
        }
        val dark = (0..40).map { pixelAt((b.left + (b.right - b.left) * (0.3f + it * 0.01f)).toInt(), cy) }
            .count { android.graphics.Color.red(it) < 120 && android.graphics.Color.green(it) < 120 && android.graphics.Color.blue(it) < 120 }
        assertTrue("$tag keeps dark text when focused ($dark dark pixels across the text band)", dark > 0)
    }

    /** Decisive render for CI inspection (uploaded with the test reports), written BEFORE the
     *  assertions so the capture exists even when a check fails. */
    private fun writeQaCapture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/$name").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 8) for (y in 0 until bmp.height step 8) colors.add(bmp.getPixel(x, y))
        assertTrue("$name is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    /** The hero Play button is a primary action: it keeps the white pill + dark text when focused;
     *  the outline ring is the focus cue. */
    @Test fun homeHeroPlayFocusedKeepsWhitePill() {
        rule.setContent {
            JetflixTheme {
                HomeContent(
                    HomeUiState(loading = false, hero = items.first().copy(UserData = null), heroes = emptyList(),
                        sections = listOf(HomeSection("Continue Watching", items.drop(1).take(8), wide = true))),
                    repo, "demo", {}, {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val play = rule.onNodeWithText("Play")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        writeQaCapture("home-hero-play-focus.png")
        assertWhitePill("Home hero Play", play)
    }

    /** The details-page primary button must stay the white pill + dark text when focused.
     *  Secondary actions
     *  (Play from start, trailer, watched, watchlist) keep the outlined dark focus look. */
    @Test fun detailsResumeFocusedKeepsWhitePill() {
        val series = items.first().copy(Id = "serp", Type = "Series", Name = "Marshals")
        val resumeEp = items[1].copy(Id = "ep1", Type = "Episode", Name = "Piya Wiconi", SeriesId = "serp",
            SeriesName = "Marshals", ParentIndexNumber = 1, IndexNumber = 3,
            UserData = tv.jetflix.app.data.UserItemDataDto(PlaybackPositionTicks = 123L * 10_000_000))
        rule.setContent {
            JetflixTheme {
                tv.jetflix.app.ui.details.DetailsContent(
                    tv.jetflix.app.ui.details.DetailsUiState(loading = false, item = series, playTarget = resumeEp),
                    repo, {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val play = rule.onNodeWithText("Resume S1:E3")
        play.requestFocus(); rule.waitForIdle()
        play.assertIsFocused()
        writeQaCapture("details-resume-focus.png")
        assertWhitePill("Details Resume S1:E3", play)
    }

    /** Season chips (user correction after build 52: "the season selection ... should stay white
     *  on the one that you are on and enlarge, circle the other"): the SELECTED season is the
     *  enlarged white pill - also while the OTHER chip holds focus - and the focused other chip
     *  shows the white outline ring on a dark fill. */
    @Test fun detailsSeasonChipSelectedStaysWhiteEnlargedOtherCircled() {
        val series = items.first().copy(Id = "serp", Type = "Series", Name = "Marshals")
        val season1 = items[2].copy(Id = "s1", Type = "Season", Name = "Season 1", SeriesId = "serp", SeriesName = "Marshals")
        val season2 = items[3].copy(Id = "s2", Type = "Season", Name = "Season 2", SeriesId = "serp", SeriesName = "Marshals")
        val resumeEp = items[1].copy(Id = "ep1", Type = "Episode", Name = "Piya Wiconi", SeriesId = "serp",
            SeriesName = "Marshals", ParentIndexNumber = 1, IndexNumber = 3,
            UserData = tv.jetflix.app.data.UserItemDataDto(PlaybackPositionTicks = 123L * 10_000_000))
        rule.setContent {
            JetflixTheme {
                tv.jetflix.app.ui.details.DetailsContent(
                    tv.jetflix.app.ui.details.DetailsUiState(
                        loading = false, item = series, playTarget = resumeEp,
                        seasons = listOf(season1, season2), selectedSeasonId = "s1",
                        episodes = listOf(resumeEp),
                    ),
                    repo, {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        // The chips row sits below the header/buttons; scroll the page list until it composes.
        // The outer list is the FIRST scroll-to-node container, the seasons row is the inner one.
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Season 2"))
        rule.waitForIdle()

        // Selected chip, NOT focused: the enlarged (1.1x) visual draws its white fill ~5% past
        // the layout bounds; an unscaled chip draws nothing there. Sample just outside the left edge.
        val sel = rule.onNodeWithText("Season 1")
        val sb = sel.fetchSemanticsNode().boundsInRoot
        val scy = ((sb.top + sb.bottom) / 2).toInt()
        val spillX = (sb.left - (sb.right - sb.left) * 0.03f).toInt()
        val spill = pixelAt(spillX, scy)
        val sr = android.graphics.Color.red(spill); val sg = android.graphics.Color.green(spill); val sbl = android.graphics.Color.blue(spill)
        assertTrue(
            "Selected season chip is enlarged: white fill spills past its left layout edge (was rgb($sr,$sg,$sbl) at x=$spillX)",
            sr > 200 && sg > 200 && sbl > 200,
        )
        assertWhitePill("Selected Season 1 chip", sel)

        // Focus the OTHER chip: the white circle on a dark fill there; the selected one stays white.
        val other = rule.onNodeWithText("Season 2")
        other.requestFocus(); rule.waitForIdle()
        other.assertIsFocused()
        writeQaCapture("details-season-chip-focus.png")
        assertFocusOutline("Season 2 chip", other)
        assertWhitePill("Selected Season 1 chip", sel)
    }

    @Test fun topNavTabFocusedShowsWhiteOutlineOnDarkFill() {
        rule.setContent {
            JetflixTheme {
                HomeContent(
                    HomeUiState(loading = false, hero = items.first(), heroes = emptyList(),
                        sections = listOf(HomeSection("Continue Watching", items.drop(1).take(8), wide = true))),
                    repo, "demo", {}, {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val tab = rule.onNodeWithText("Movies")
        tab.requestFocus(); rule.waitForIdle()
        tab.assertIsFocused()
        writeQaCapture("topnav-movies-tab-focus.png")
        assertFocusOutline("Top-nav Movies tab", tab)
    }

    /** The selected page's tab must STAY the white pill when it holds focus - the white outline
     *  ring outside it is the focus cue. Non-selected focused tabs keep the dark fill (see the
     *  Movies-tab case above). */
    @Test fun topNavSelectedTabFocusedKeepsWhitePill() {
        rule.setContent {
            JetflixTheme {
                HomeContent(
                    HomeUiState(loading = false, hero = items.first(), heroes = emptyList(),
                        sections = listOf(HomeSection("Continue Watching", items.drop(1).take(8), wide = true))),
                    repo, "demo", {}, {}, {}, {},
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val tab = rule.onNodeWithText("Home")
        tab.requestFocus(); rule.waitForIdle()
        tab.assertIsFocused()
        writeQaCapture("topnav-selected-home-tab-focus.png")
        val b = tab.fetchSemanticsNode().boundsInRoot
        val cy = ((b.top + b.bottom) / 2).toInt()
        for (frac in listOf(0.10f, 0.90f)) {
            val px = pixelAt((b.left + (b.right - b.left) * frac).toInt(), cy)
            val r = android.graphics.Color.red(px); val g = android.graphics.Color.green(px); val bl = android.graphics.Color.blue(px)
            assertTrue("Selected Home tab keeps the white pill when focused at ${(frac * 100).toInt()}% width (was rgb($r,$g,$bl))", r > 200 && g > 200 && bl > 200)
        }
        val dark = (0..40).map { pixelAt((b.left + (b.right - b.left) * (0.3f + it * 0.01f)).toInt(), cy) }
            .count { android.graphics.Color.red(it) < 120 && android.graphics.Color.green(it) < 120 && android.graphics.Color.blue(it) < 120 }
        assertTrue("Selected Home tab keeps dark text when focused ($dark dark pixels across the text band)", dark > 0)
    }

    @Test fun libraryChipFocusedShowsWhiteOutlineOnDarkFill() {
        rule.setContent {
            JetflixTheme {
                LibraryContent(LibraryUiState(loading = false, items = items), repo, JetflixTab.MOVIES, "demo", {}, {}, {})
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val chip = rule.onNodeWithText("Filter")
        chip.requestFocus(); rule.waitForIdle()
        chip.assertIsFocused()
        writeQaCapture("library-filter-chip-focus.png")
        assertFocusOutline("Library Filter chip", chip)
    }

    @Test fun whatsNewOkFocusedShowsWhiteOutlineOnDarkFill() {
        val notes = listOf(
            "First build note for the outline sweep.",
            "Second build note, kept short on purpose.",
        )
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101418))) {
                    WhatsNewCard(notes, {}, ScrollState(0), Modifier.align(Alignment.Center))
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val ok = rule.onNodeWithText("OK")
        ok.requestFocus(); rule.waitForIdle()
        ok.assertIsFocused()
        writeQaCapture("whatsnew-ok-focus.png")
        assertFocusOutline("WhatsNew OK", ok)
    }
}
