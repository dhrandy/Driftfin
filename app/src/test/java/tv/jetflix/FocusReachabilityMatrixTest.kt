package tv.jetflix

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * D-pad reachability matrix (viewport dp x font scale 1.0/1.3). Walks focus with Down/Right the way a remote does and
 * requires every control that takes focus to be fully inside the window at that moment, and the key controls to be
 * reachable. Scrollable content need not all be visible at once. Login uses a full-size profile list (8 profiles,
 * 3 already signed in); nothing is squeezed. No hardware claim.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusReachabilityMatrixTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var fm: androidx.compose.ui.focus.FocusManager? = null

    private fun show(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                JetflixTheme { fm = androidx.compose.ui.platform.LocalFocusManager.current; content() }
            }
        }
        rule.waitForIdle()
    }

    private fun label(n: androidx.compose.ui.semantics.SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "") +
            (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString { it } ?: "") +
            n.children.joinToString("") { label(it) }

    /** Walks focus and returns the labels reached; fails if a focused control is not fully inside the window. */
    private fun walk(dirs: List<FocusDirection>, steps: Int, where: String): List<String> {
        val seen = mutableListOf<String>()
        repeat(steps) { i ->
            rule.runOnUiThread { fm!!.moveFocus(dirs[i % dirs.size]) }; rule.waitForIdle()
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            rule.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { n ->
                val b = n.boundsInRoot
                val l = label(n)
                if (l.isNotEmpty()) seen += l
                assertTrue("$where step $i: focused '$l' not fully visible: node=$b window=$root",
                    b.left >= root.left - 0.5f && b.top >= root.top - 0.5f && b.right <= root.right + 0.5f && b.bottom <= root.bottom + 0.5f)
            }
        }
        return seen
    }

    private fun walkLogin(f: Float) {
        val profiles = (1..8).map { "Profile $it" }
        show(f) { LoginContent(LoginUiState(serverUrl = "demo.test:8096", profiles = profiles, savedProfiles = profiles.take(3)), { _, _, _ -> }, {}) }
        val seen = walk(listOf(FocusDirection.Down), 14, "login")
        assertTrue("Sign In reachable by Down: $seen", seen.any { it.contains("Sign In") })
    }

    private fun walkSearch(f: Float) {
        val results = (1..18).map { BaseItemDto(Id = "r$it", Name = "Result $it", Type = "Movie") }
        show(f) { SearchContent(SearchUiState(query = "res", results = results), JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, {}) }
        val seen = walk(listOf(FocusDirection.Down, FocusDirection.Right, FocusDirection.Right, FocusDirection.Right, FocusDirection.Right, FocusDirection.Right), 12, "search")
        assertTrue("a poster in the grid is reachable: $seen", seen.any { it.startsWith("Result") })
        assertTrue("the sixth poster of a row is reachable by Right: $seen", seen.any { it.contains("Result 6") || it.contains("Result 12") })
    }

    @Config(sdk = [34], qualifiers = "w640dp-h360dp-mdpi")
    @Test fun login_narrow_fs100() = walkLogin(1.0f)

    @Config(sdk = [34], qualifiers = "w640dp-h360dp-mdpi")
    @Test fun login_narrow_fs130() = walkLogin(1.3f)

    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun login_standard_fs100() = walkLogin(1.0f)

    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun login_standard_fs130() = walkLogin(1.3f)

    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-tvdpi")
    @Test fun login_tvdpi_fs100() = walkLogin(1.0f)

    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-tvdpi")
    @Test fun login_tvdpi_fs130() = walkLogin(1.3f)

    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    @Test fun login_wide_fs100() = walkLogin(1.0f)

    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    @Test fun login_wide_fs130() = walkLogin(1.3f)

    @Config(sdk = [34], qualifiers = "w640dp-h360dp-mdpi")
    @Test fun search_narrow_fs100() = walkSearch(1.0f)

    @Config(sdk = [34], qualifiers = "w640dp-h360dp-mdpi")
    @Test fun search_narrow_fs130() = walkSearch(1.3f)

    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun search_standard_fs100() = walkSearch(1.0f)

    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun search_standard_fs130() = walkSearch(1.3f)

    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-tvdpi")
    @Test fun search_tvdpi_fs100() = walkSearch(1.0f)

    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-tvdpi")
    @Test fun search_tvdpi_fs130() = walkSearch(1.3f)

    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    @Test fun search_wide_fs100() = walkSearch(1.0f)

    @Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
    @Test fun search_wide_fs130() = walkSearch(1.3f)

}
