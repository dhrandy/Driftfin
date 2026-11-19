package tv.jetflix.phone

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.PhoneSettings
import tv.jetflix.phone.data.PosterSize
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.SectionRow

/** Home and Search rows follow the poster size setting, like the grids do (7-inch tablet report:
 *  the setting looked dead on the screens he opened because rows never followed it). Medium keeps
 *  the original 112 dp cards; Small/Large use the TV app's 0.85/1.25 row scale (95/140 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneRowPosterSizeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val item = BaseItemDto(Id = "i1", Name = "One", Type = "Movie")

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("driftfin_phone_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signIn(Session("http://127.0.0.1:9", "u1", "Tester", "tok", "dev"))
        PhoneSettings.init(ctx) // reread the cleared prefs so each test starts from defaults
    }

    private fun rowCardWidthDp(): Float =
        rule.onNodeWithTag("row-card-i1").fetchSemanticsNode().boundsInRoot.width / rule.density.density

    @Test fun rowCardsFollowPosterSize() {
        PhoneSettings.setPosterSize(PosterSize.Medium)
        rule.setContent { DriftfinTheme { SectionRow("Row", listOf(item)) {} } }
        rule.waitForIdle()
        // One composition; the setting flips underneath it, which is exactly how the app is used.
        for ((size, expectedDp) in listOf(PosterSize.Medium to 112, PosterSize.Small to 95, PosterSize.Large to 140)) {
            PhoneSettings.setPosterSize(size)
            rule.waitForIdle()
            assertEquals("poster size ${size.label}", expectedDp.toFloat(), rowCardWidthDp(), 0.5f)
        }
    }

    @Test fun explicitCardWidthStillWins() {
        PhoneSettings.setPosterSize(PosterSize.Large)
        rule.setContent { DriftfinTheme { SectionRow("Row", listOf(item), cardWidth = 200.dp) {} } }
        rule.waitForIdle()
        assertEquals(200f, rowCardWidthDp(), 0.5f)
    }
}
