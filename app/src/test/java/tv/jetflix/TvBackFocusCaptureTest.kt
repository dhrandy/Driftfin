package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
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
import java.io.File

/** Pixel proof for the Back-focus fix: rendered focus/scroll position after returning from details. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvBackFocusCaptureTest {
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
        rule.waitForIdle()
    }

    private fun idle() { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun key(k: Key) { rule.onAllNodes(isRoot())[0].performKeyInput { pressKey(k) }; idle() }

    private fun capture(name: String) {
        idle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    private fun focusedHas(text: String) = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().any { n ->
        var cur: androidx.compose.ui.semantics.SemanticsNode? = n
        while (cur != null) {
            val hay = cur.config.filter { it.key.name == "ContentDescription" || it.key.name == "Text" }.joinToString(" ") { it.value.toString() }
            if (hay.contains(text)) return@any true
            cur = cur.parent
        }
        false
    }

    @Test fun backToFarRightCwRendersFocusOnSameCard() {
        compose()
        key(Key.DirectionDown); key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) } // far-right card: S1:E5
        key(Key.DirectionCenter)              // open details
        screen.value = "home"                 // Back
        idle()
        rule.waitUntil(5_000) { idle(); focusedHas("CW Episode 5") }
        capture("back-focus-far-right-cw")
    }

    @Test fun backToChangedCwRendersListNotJumpedToBanner() {
        compose()
        key(Key.DirectionDown); key(Key.DirectionDown)
        repeat(2) { key(Key.DirectionRight) } // S1:E3
        key(Key.DirectionCenter)
        currentState.value = stateWith(listOf(1, 2, 4, 5, 6).map { cwEp(it) }) // e3 watched away
        screen.value = "home"
        idle()
        // The remembered card is gone: the same-row fallback (1.2s) lands on the row's first card.
        rule.waitUntil(5_000) { idle(); focusedHas("CW Episode 1") }
        idle()
        // Bounded visibility proof: the focused card is actually inside the viewport, not focused
        // off-screen. Fully inside horizontally, and the vertical strip showing the rows.
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // focusedHas just proved the focused subtree carries "CW Episode 1"; the focused node's own
        // bounds are the card's (the focusable is the card container).
        val focused = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes()
        focused.forEachIndexed { i, n -> println("DIAG focused$i bounds=${n.boundsInRoot}") }
        val card = focused
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            .single { n ->
                var cur: androidx.compose.ui.semantics.SemanticsNode? = n
                var hit = false
                while (cur != null) {
                    if (cur.config.filter { it.key.name == "ContentDescription" || it.key.name == "Text" }.joinToString(" ") { it.value.toString() }.contains("CW Episode 1")) { hit = true; break }
                    cur = cur.parent
                }
                hit
            }.boundsInRoot
        capture("back-focus-changed-cw")
        org.junit.Assert.assertTrue("focused CW card inside viewport horizontally (card=$card root=$root)",
            card.left >= root.left && card.right <= root.right && card.width > 0f)
        org.junit.Assert.assertTrue("focused CW card inside viewport vertically (card=$card root=$root)",
            card.top >= root.top && card.bottom <= root.bottom && card.height > 0f)
    }
}
