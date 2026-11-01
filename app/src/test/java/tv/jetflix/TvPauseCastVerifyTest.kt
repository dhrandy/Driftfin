package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.player.PauseCastStrip
import tv.jetflix.app.ui.player.PlayerKeys
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import tv.jetflix.app.ui.player.PlayerOverlay
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * Additive verification for the pause-cast strip and pause air-date line: every card browsable to
 * the very last one, the hint's "OK movies" on server people and "OK details" on TVMaze
 * display-only people (user report, build 52: OK on a Jury Duty season 2 guest did nothing -
 * every S2 credit there is TVMaze-only), and the guest panel that OK opens for a display-only
 * person, including its remote contract (scroll/close/media pass-through).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class TvPauseCastVerifyTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    /** 7 real Jellyfin people, then 8 TVMaze display-only people (Id null). */
    private fun mixedCast() = (1..15).map { i ->
        BaseItemPerson(
            Id = if (i <= 7) "p$i" else null,
            Name = "Person %02d".format(i),
            Role = "Character $i",
            Type = if (i % 3 == 0) "GuestStar" else "Actor",
        )
    }

    private fun strip(cast: List<BaseItemPerson>, selected: Int?) {
        rule.setContent { JetflixTheme { PauseCastStrip(cast = cast, selected = selected, imageUrlFor = { null }) } }
        rule.waitForIdle()
    }

    @Test fun lastCastCardIsReachableAndOnScreen() {
        strip(mixedCast(), 14)
        rule.onNodeWithText("Person 15").assertIsDisplayed()
    }

    @Test fun hintWithoutSelectionOnlyOffersBrowse() {
        strip(mixedCast(), null)
        rule.onNodeWithText("Cast   ▼ to browse").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("OK movies", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun hintOnOpenablePersonOffersOkMovies() {
        strip(mixedCast(), 2)
        rule.onNodeWithText("OK movies", substring = true).assertIsDisplayed()
    }

    @Test fun hintOnDisplayOnlyPersonOffersOkDetails() {
        strip(mixedCast(), 10)
        rule.onNodeWithText("Cast   ◀ ▶ choose   OK details   ▲ back").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("OK movies", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun guestPanelShowsPersonCharacterEpisodeAndCredit() {
        val guest = BaseItemPerson(Id = null, Name = "Guest Person", Role = "Christine", Type = "Actor",
            RemoteImageUrl = null, ProviderIds = mapOf("Tvmaze" to "187924"))
        rule.setContent {
            JetflixTheme {
                tv.jetflix.app.ui.player.GuestPersonPanel(person = guest, episodeLabel = "S2E2 · Team Building", imageUrl = null)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person").assertIsDisplayed()
        rule.onNodeWithText("as Christine").assertIsDisplayed()
        rule.onNodeWithText("Guest in S2E2 · Team Building").assertIsDisplayed()
        rule.onNodeWithText("Guest cast and photo: TVMaze (tvmaze.com)").assertIsDisplayed()
        capture("pause-guest-person-panel")
    }

    @Test fun guestPanelWithBioShowsExtractAndWikipediaCredit() {
        val guest = BaseItemPerson(Id = null, Name = "Guest Person", Role = "Christine", Type = "Actor",
            RemoteImageUrl = null, ProviderIds = mapOf("Tvmaze" to "187924"))
        rule.setContent {
            JetflixTheme {
                tv.jetflix.app.ui.player.GuestPersonPanel(person = guest, episodeLabel = "S2E2 · Team Building", imageUrl = null,
                    bio = "Guest Person is a Canadian actress and comedian known for viral videos.")
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person is a Canadian actress and comedian known for viral videos.").assertIsDisplayed()
        rule.onNodeWithText("Guest cast and photo: TVMaze (tvmaze.com) · Bio: Wikipedia (en.wikipedia.org)").assertIsDisplayed()
    }

    @Test fun guestPanelRemoteContractScrollCloseAndMediaPassThrough() {
        val guest = BaseItemPerson(Id = null, Name = "Guest Person", Role = "Christine", Type = "Actor",
            RemoteImageUrl = null, ProviderIds = mapOf("Tvmaze" to "187924"))
        val longBio = (1..60).joinToString(" ") { "Sentence $it of a long biography that keeps going." }
        val scroll = androidx.compose.foundation.ScrollState(0)
        var mediaActed = false
        rule.setContent {
            JetflixTheme {
                var open by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()
                val focus = remember { FocusRequester() }
                // The same routing the player's remote handler runs while the panel is open.
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (PlayerKeys.guestPanelKey(ev.nativeKeyEvent.keyCode)) {
                        PlayerKeys.GuestPanelKey.ScrollDown -> { scope.launch { scroll.scrollBy(160f) }; true }
                        PlayerKeys.GuestPanelKey.ScrollUp -> { scope.launch { scroll.scrollBy(-160f) }; true }
                        PlayerKeys.GuestPanelKey.Close -> { open = false; true }
                        PlayerKeys.GuestPanelKey.Swallow -> true
                        PlayerKeys.GuestPanelKey.PassThrough -> { mediaActed = true; true } // host performs the media action
                    }
                }.focusRequester(focus).focusable()) {
                    if (open) {
                        tv.jetflix.app.ui.player.GuestPersonPanel(person = guest, episodeLabel = "S2E2 · Team Building",
                            imageUrl = null, bio = longBio, scroll = scroll)
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person").assertIsDisplayed()
        // A long bio overflows and Down scrolls it; Up at the top stays put instead of closing.
        rule.runOnIdle { assertTrue("long bio must overflow the panel", scroll.maxValue > 0) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue("Down scrolled the bio", scroll.value > 0) }
        val at = rule.runOnIdle { scroll.value }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue("Up scrolled back, not closed", scroll.value < at) }
        rule.onNodeWithText("Guest Person").assertIsDisplayed()
        // A media key passes through to playback control and the panel stays open.
        rule.onRoot().performKeyInput { pressKey(Key.MediaPlayPause) }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person").assertIsDisplayed()
        capture("pause-guest-person-panel-bio")
        assertTrue("media key reached the host", mediaActed)
        // Back closes.
        rule.onRoot().performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person").assertDoesNotExist()
    }

    @Test fun guestPanelOkCloses() {
        val guest = BaseItemPerson(Id = null, Name = "Guest Person", Role = "Christine", Type = "Actor",
            RemoteImageUrl = null, ProviderIds = mapOf("Tvmaze" to "187924"))
        rule.setContent {
            JetflixTheme {
                var open by remember { mutableStateOf(true) }
                val focus = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (PlayerKeys.guestPanelKey(ev.nativeKeyEvent.keyCode) == PlayerKeys.GuestPanelKey.Close) { open = false; true } else true
                }.focusRequester(focus).focusable()) {
                    if (open) {
                        tv.jetflix.app.ui.player.GuestPersonPanel(person = guest, episodeLabel = "S2E2 · Team Building", imageUrl = null)
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithText("Guest Person").assertDoesNotExist()
    }

    @Test fun capturePausedOverlayDisplayOnlySelected() {
        val ep = BaseItemDto(
            Type = "Episode", SeriesName = "Jury Duty", ParentIndexNumber = 1, IndexNumber = 9,
            Name = "Behind the Scenes of Company Retreat: A Very Long Episode Title That Keeps Going",
            PremiereDate = "2026-04-09T00:00:00Z",
        )
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerOverlay(
                        visible = true, item = ep, isPlaying = false,
                        positionTicks = 60_000_000L, bufferedTicks = 60_000_000L, durationTicks = 600_000_000L,
                        castVisible = true, cast = mixedCast(), castSelected = 10, personImageUrl = { null },
                    )
                }
            }
        }
        capture("pause-cast-display-only-selected")
        rule.onNodeWithText("Cast   ◀ ▶ choose   OK details   ▲ back").assertIsDisplayed()
    }

    @Test fun capturePausedOverlayOpenableSelected() {
        val ep = BaseItemDto(
            Type = "Episode", SeriesName = "Jury Duty", ParentIndexNumber = 1, IndexNumber = 9,
            Name = "Behind the Scenes of Company Retreat", PremiereDate = "2026-04-09T00:00:00Z",
        )
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerOverlay(
                        visible = true, item = ep, isPlaying = false,
                        positionTicks = 60_000_000L, bufferedTicks = 60_000_000L, durationTicks = 600_000_000L,
                        castVisible = true, cast = mixedCast(), castSelected = 2, personImageUrl = { null },
                    )
                }
            }
        }
        capture("pause-cast-openable-selected")
        rule.onNodeWithText("OK movies", substring = true).assertIsDisplayed()
    }
}
