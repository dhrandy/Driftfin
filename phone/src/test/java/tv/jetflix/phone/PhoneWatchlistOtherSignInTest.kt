package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.Watchlist
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.MyStuffScreen

/** A list saved under another server/user key stays untouched and is reported by count only (no ids, no address)
 *  when the current sign-in's Watchlist is empty. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneWatchlistOtherSignInTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val otherKey = "http://old.example.invalid#other-user"

    private fun store() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        .getSharedPreferences("jetflix_watchlist", android.content.Context.MODE_PRIVATE)

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        Watchlist.resetForTest()
        Watchlist.init(ctx)
        ctx.getSharedPreferences("driftfin_phone_browse", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        store().edit().putString(otherKey, "p,q,r").commit()
        server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}""")
        }
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(url, "u", "N", "t", Graph.tokens.deviceId()))
        Watchlist.setUser(url, "u")
    }

    @Test fun otherSignInListIsCountedAndUntouched() {
        assertEquals(listOf(3), Watchlist.otherListSizes())
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Watchlist").performClick()
        val deadline = System.currentTimeMillis() + 15_000
        val msg = "This watchlist is empty, but this phone still holds another saved list. Nothing in that list has been changed."
        while (System.currentTimeMillis() < deadline && rule.onAllNodesWithTextCount(msg) == 0) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
        }
        rule.onNodeWithText(msg).assertIsDisplayed()
        capture("phone-watchlist-other-saved-list.png")
        assertEquals("p,q,r", store().getString(otherKey, null))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size

    @After fun tearDown() { runCatching { server.shutdown() }; Graph.signOut() }
    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}