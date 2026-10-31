package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.ActorScreen
import tv.jetflix.phone.ui.DriftfinTheme

/** Actor page: the person's bio plus separate Movies and Shows sections of library titles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneActorTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    @Volatile private var withBio = true
    @Volatile private var multiBio = false
    @Volatile private var withDates = false

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        withBio = true
        multiBio = false
        withDates = false
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                if (p.contains("/Images/")) return MockResponse().setResponseCode(404)
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    p.contains("/Items/p1") -> json.setBody(if (!withBio) """{"Id":"p1","Name":"Synthetic Actor","Type":"Person"}"""
                        else if (multiBio) """{"Id":"p1","Name":"Synthetic Actor","Type":"Person","Overview":"First paragraph of the bio.\n\nSecond paragraph with the rest of the story."}"""
                        else if (withDates) """{"Id":"p1","Name":"Synthetic Actor","Type":"Person","PremiereDate":"1926-06-01T00:00:00.0000000Z","EndDate":"1962-08-04T00:00:00.0000000Z","Overview":"A made-up bio for the synthetic actor."}"""
                        else """{"Id":"p1","Name":"Synthetic Actor","Type":"Person","Overview":"A made-up bio for the synthetic actor."}""")
                    p.contains("personIds=p1") -> json.setBody("""{"Items":[{"Id":"m1","Name":"Actor Movie","Type":"Movie","ImageTags":{"Primary":"a"}},{"Id":"s1","Name":"Actor Show","Type":"Series","ImageTags":{"Primary":"b"}}],"TotalRecordCount":2}""")
                    p.contains("/Items") -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                    else -> json.setBody("""{}""")
                }
            }
        }
        server.start()
        SessionManager.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun bioAndTypedSectionsRender() {
        rule.setContent { DriftfinTheme { ActorScreen("p1", "Synthetic Actor", onBack = {}, onOpen = {}) } }
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (rule.onAllNodes(androidx.compose.ui.test.hasText("Movies")).fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(80)
        }
        rule.onNodeWithText("A made-up bio for the synthetic actor.").assertIsDisplayed()
        org.junit.Assert.assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("More")).fetchSemanticsNodes().size)
        rule.onNodeWithText("Movies").assertIsDisplayed()
        rule.onNodeWithText("Shows").assertIsDisplayed()
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        java.io.File("build/render-check").mkdirs()
        java.io.File("build/render-check", "actor-bio.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun missingBioHidesCleanly() {
        withBio = false
        rule.setContent { DriftfinTheme { ActorScreen("p1", "Synthetic Actor", onBack = {}, onOpen = {}) } }
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (rule.onAllNodes(androidx.compose.ui.test.hasText("Movies")).fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(80)
        }
        rule.onNodeWithText("Movies").assertIsDisplayed()
        rule.onNodeWithText("Shows").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("A made-up bio for the synthetic actor.")).fetchSemanticsNodes().size)
    }

    @Test fun bioCollapsesToFirstParagraphWithMore() {
        multiBio = true
        rule.setContent { DriftfinTheme { ActorScreen("p1", "Synthetic Actor", onBack = {}, onOpen = {}) } }
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (rule.onAllNodes(androidx.compose.ui.test.hasText("More")).fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(80)
        }
        rule.onNodeWithText("First paragraph of the bio.").assertIsDisplayed()
        rule.onNodeWithText("More").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("Second paragraph with the rest of the story.", substring = true)).fetchSemanticsNodes().size)
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val collapsed = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(collapsed))
        java.io.File("build/render-check").mkdirs()
        java.io.File("build/render-check", "actor-bio-collapsed.png").outputStream().use { collapsed.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("More").performClick()
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.onNodeWithText("Second paragraph with the rest of the story.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Show less").assertIsDisplayed()
    }

    @Test fun lifeDatesUnderName() {
        withDates = true
        rule.setContent { DriftfinTheme { ActorScreen("p1", "Synthetic Actor", onBack = {}, onOpen = {}) } }
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (rule.onAllNodes(androidx.compose.ui.test.hasText("Born Jun 1, 1926", substring = true)).fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(80)
        }
        rule.onNodeWithText("Born Jun 1, 1926 · Died Aug 4, 1962").assertIsDisplayed()
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        java.io.File("build/render-check").mkdirs()
        java.io.File("build/render-check", "actor-life-dates.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
