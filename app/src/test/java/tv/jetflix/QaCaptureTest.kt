package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.PosterSize
import tv.jetflix.app.ui.actor.ActorContent
import tv.jetflix.app.ui.actor.ActorUiState
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * Pixel captures from the real screens under a real focus system (Robolectric native graphics, software
 * render - captureToImage hangs on Choreographer under Robolectric). Demo artwork is synthetic gradients.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class QaCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun art(url: String?): BitmapPainter? {
        url ?: return null
        val kind = Regex("/Images/(Primary|Backdrop)").find(url)?.groupValues?.get(1) ?: return null
        // Multiplied library items (lib0..) reuse the 12 demo fixtures round-robin.
        val lib = Regex("/Items/lib([0-9]+)/Images/").find(url)
        val id = if (lib != null) {
            val ids = base.map { it.Id }
            ids[lib.groupValues[1].toInt() % ids.size]
        } else {
            Regex("/Items/([0-9a-f]+)/Images/").find(url)?.groupValues?.get(1) ?: return null
        }
        val bytes = javaClass.classLoader!!.getResourceAsStream("art/${id}_${kind}.jpg")?.readBytes() ?: return null
        var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        // Mirror the app's size-aware URLs: a 48/320px blur source must actually arrive small, or the pre-shrunk blur trick never happens in captures.
        Regex("maxWidth=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { mw ->
            if (mw > 0 && bmp.width > mw) bmp = android.graphics.Bitmap.createScaledBitmap(bmp, mw, bmp.height * mw / bmp.width, true)
        }
        return BitmapPainter(bmp.asImageBitmap())
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    private fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }
    private fun focusedTop(): Float = rule.onAllNodes(isFocused()).fetchSemanticsNodes().first().boundsInRoot.top

    @Test fun actorPageFirstCardFocused() {
        val movies = base.take(10).mapIndexed { i, m -> m.copy(Name = "Movie ${i + 1}") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme { ActorContent(ActorUiState(loading = false, name = "Alex Example", bio = "A short synthetic biography used only for layout testing.", photoUrl = "http://demo.test/Items/ace010/Images/Primary", titles = movies), imageUrlFor = { "http://demo.test/Items/${it.Id}/Images/Primary" }, onOpenTitle = {}) }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        key(Key.DirectionDown) // first key establishes focus; lands on the first movie card
        val f = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
        println("QA_FOCUS " + f?.config + " @ " + f?.boundsInRoot)
        capture("actor_focused")
    }

    @Test fun actorPageLowerRowReturn() {
        val movies = base.take(10).mapIndexed { i, m -> m.copy(Name = "Movie ${i + 1}") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme { ActorContent(ActorUiState(loading = false, name = "Alex Example", bio = "A short synthetic biography used only for layout testing.", photoUrl = "http://demo.test/Items/ace010/Images/Primary", titles = movies), imageUrlFor = { "http://demo.test/Items/${it.Id}/Images/Primary" }, onOpenTitle = {}) }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        repeat(3) { key(Key.DirectionDown) } // a lower row
        repeat(3) { key(Key.DirectionUp) }   // and back: the Back-no-scroll-jump fix's scroll memory
        capture("actor_return")
    }

    @Test fun libraryScrolledAndReturnedFocused() {
        AppSettings.setPosterSize(PosterSize.Medium)
        val items = (0 until 63).map { i -> base[i % base.size].copy(Id = "lib$i", Name = "Title $i") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme { LibraryContent(LibraryUiState(loading = false, items = items), JellyfinRepository().apply { apiFor("http://demo.test") }, JetflixTab.MOVIES, "demo", {}, {}, {}) }
            }
        }
        rule.waitForIdle()
        repeat(12) { key(Key.DirectionDown) }
        capture("library_scrolled")
        // Walk focus up out of the grid onto the Sort/Filter controls, then one Down returns
        // focus to the first-row card at its settled offset - the decisive returned state.
        var ups = 0
        while (ups < 16) { key(Key.DirectionUp); ups++; if (focusedTop() < 200f) break }
        key(Key.DirectionDown)
        println("QA_RETURN_TOP " + focusedTop() + " after " + ups + " ups + 1 down")
        capture("library_returned")
    }






    @Test fun libraryForReadme() {
        AppSettings.setPosterSize(PosterSize.Medium)
        val items = base.sortedBy { (it.Name ?: "").removePrefix("The ") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme { LibraryContent(LibraryUiState(loading = false, items = items), JellyfinRepository().apply { apiFor("http://demo.test") }, JetflixTab.MOVIES, "demo", {}, {}, {}) }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        capture("library_readme")
    }

    @Test fun homeForReadme() {
        val titles = listOf("Continue Watching", "Next Up", "Recently Added Movies", "Favorites")
        val sections = titles.mapIndexed { i, t -> HomeSection(t, base.drop(i).take(8), wide = i < 2) }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme {
                    HomeContent(HomeUiState(loading = false, hero = base.first().copy(UserData = null), heroes = emptyList(), sections = sections),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {})
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        capture("home_readme")
    }

    @Test fun detailsModernLandscape() {
        // Genuine 16:9 landscape backdrop (Dune: Part Two TMDB still, see art/SOURCES.md): the modern-art
        // counterpart to the classic-poster fixture, for banner presentation checks at TV proportions.
        val movie = base.first { it.Name == "Dune: Part Two" }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                JetflixTheme {
                    tv.jetflix.app.ui.details.DetailsContent(
                        tv.jetflix.app.ui.details.DetailsUiState(loading = false, item = movie, playTarget = movie, similar = base.filter { it.Id != movie.Id }.take(8)),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, {}, { _, _ -> }, {}, {}, {},
                    )
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        capture("details_modern_landscape")
    }
}
