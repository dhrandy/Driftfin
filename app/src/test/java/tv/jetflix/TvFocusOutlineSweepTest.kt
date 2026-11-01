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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** The build-50 white-outline-on-focus rule beyond Details: home hero Play, a top-nav tab,
 *  a library chip, and the What's new OK button all show the WHITE OUTLINE on a DARK fill when
 *  focused - actual pixels (the v1 constant-only check passed while focused Play was a white block). */
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

    @Test fun homeHeroPlayFocusedShowsWhiteOutlineOnDarkFill() {
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
        assertFocusOutline("Home hero Play", play)
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
