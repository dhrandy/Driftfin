package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Launches the real MainActivity from a clean install and drives the sign-in with taps and typing against a local fake server. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FirstLaunchFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }
    }

    @Test fun cleanLaunchShowsSignInThenPasswordLoginReachesShell() {
        // Other tests in the same JVM may have signed in; a real clean install has no session.
        Graph.signOut(); rule.waitForIdle()
        val server = MockWebServer()
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty(); seen += "${request.method} $p"
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    p.contains("AuthenticateByName") -> json.setBody("""{"AccessToken":"tok","User":{"Id":"u1","Name":"Tester"}}""")
                    p.contains("Latest") -> json.setBody("[]")
                    else -> json.setBody("""{"Items":[],"TotalRecordCount":0,"StartIndex":0}""")
                }
            }
        }
        server.start()
        rule.onNodeWithText("Server address").assertIsDisplayed()
        rule.onNodeWithText("Sign in with Quick Connect").assertIsDisplayed()
        run {
            val d = rule.activity.window.decorView
            d.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            d.layout(0, 0, 1080, 2400)
            val b = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
            d.draw(android.graphics.Canvas(b))
            java.io.File("build/render-check").mkdirs()
            java.io.File("build/render-check/first-launch.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onNodeWithText("Server address").performClick()
        rule.onNodeWithText("Server address").performTextInput(server.url("/").toString().trimEnd('/'))
        rule.onNodeWithText("Username").performTextInput("tester")
        rule.onNodeWithText("Password").performTextInput("pw")
        rule.onNodeWithText("Sign in").performClick()
        pump(6000)
        println("REQUESTS: $seen")
        rule.onAllNodes(isRoot())[0].printToLog("TREE")
        println("TREE>>" + rule.onAllNodes(isRoot())[0].printToString(200))
        val decor = rule.activity.window.decorView
        println("DECOR ${decor.width}x${decor.height}")
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        decor.draw(android.graphics.Canvas(bmp))
        java.io.File("build/render-check").mkdirs()
        java.io.File("build/render-check/after-login.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText("Search").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        pump(1500)
        rule.onNodeWithText("Check for updates").assertIsDisplayed()
        server.shutdown()
    }
}
