package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.LoginScreen
import java.util.concurrent.CopyOnWriteArrayList

/** Quick Connect sign-in: the code renders, the poll authenticates, and an unavailable server gets a plain message. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneQuickConnectTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    @Volatile private var qcAvailable = true

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met within ${timeoutMs}ms\nREQUESTED:\n" + requested.joinToString("\n"), condition())
    }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.signOut()
        ctx.getSharedPreferences("driftfin_phone_session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        qcAvailable = true
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty(); requested += "${request.method} $p"
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    p.contains("QuickConnect/Initiate") ->
                        if (qcAvailable) json.setBody("""{"Secret":"sekrit","Code":"WDJTBH"}""")
                        else MockResponse().setResponseCode(404).setBody("{}")
                    p.contains("QuickConnect/Connect") -> json.setBody("""{"Authenticated":true}""")
                    p.contains("AuthenticateWithQuickConnect") -> json.setBody("""{"AccessToken":"tok","User":{"Id":"u1","Name":"Tester"}}""")
                    else -> json.setBody("""{"Items":[],"TotalRecordCount":0,"StartIndex":0}""")
                }
            }
        }
        server.start()
    }

    private fun launchLogin() {
        rule.setContent { DriftfinTheme { LoginScreen() } }
        rule.onNodeWithText("Server address").performClick()
        rule.onNodeWithText("Server address").performTextInput(server.url("/").toString().trimEnd('/'))
    }

    @Test fun quickConnectFlow() {
        // Unavailable server: a plain message, no session.
        qcAvailable = false
        launchLogin()
        rule.onNodeWithText("Sign in with Quick Connect").performClick()
        awaitReal(15_000) { rule.onAllNodesWithText("Quick Connect is not available on this server. Use a username and password.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(SessionManager.session.value == null)

        // Available server: the code renders and the poll signs in.
        qcAvailable = true
        rule.onNodeWithText("Sign in with Quick Connect").performClick()
        awaitReal(15_000) { rule.onAllNodesWithText("WDJTBH", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("WDJTBH", substring = true).assertIsDisplayed()
        // The poll waits on a 3s delay() driven by the test clock: advance it, then pump real time.
        var tries = 0
        while (SessionManager.session.value == null && tries < 10) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(3_200, java.util.concurrent.TimeUnit.MILLISECONDS)
            val end = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < end && SessionManager.session.value == null) {
                rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
            }
            tries++
        }
        assertNotNull("session established after poll\nREQUESTED:\n" + requested.joinToString("\n"), SessionManager.session.value)
        assertEquals("Tester", SessionManager.session.value?.userName)
        assertTrue(requested.any { it.contains("QuickConnect/Connect") && it.contains("secret=sekrit") })
        assertTrue(requested.any { it.contains("AuthenticateWithQuickConnect") })
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
