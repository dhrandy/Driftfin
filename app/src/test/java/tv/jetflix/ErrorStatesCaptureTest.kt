package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.player.PlayerErrorOverlay
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/** Pixel proof for the two new failure notes (Todd row 86): Search's failure note and the player's
 *  next-episode lookup error. Functional tests alone don't show what the user sees. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ErrorStatesCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        val f = File("build/outputs/qa/$name.png"); f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CAPTURE ${f.absolutePath}")
    }

    @Test fun searchFailureNoteShowsInsteadOfSilentEmptyPage() {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                SearchContent(
                    SearchUiState(query = "dune", searching = false, searchFailed = true),
                    repo, "Demo", {}, {}, {}, {}, {},
                )
            }
        }
        rule.waitForIdle()
        rule.onAllNodes(hasText("Search failed. Check the server and try again.")).assertCountEquals(1)
        // The misleading "No matches" text must not show for a failure.
        rule.onAllNodes(hasText("No matches for \"dune\"")).assertCountEquals(0)
        capture("search-failed")
    }

    @Test fun playerLookupErrorSaysWhatHappenedAndHowToGetBack() {
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerErrorOverlay("Couldn't load the next episode.", Modifier.align(Alignment.Center))
                }
            }
        }
        rule.waitForIdle()
        rule.onAllNodes(hasText("Couldn't load the next episode.\nPress Back to return.")).assertCountEquals(1)
        capture("player-lookup-error")
    }
}
