package tv.jetflix

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.TokenCrypto
import tv.jetflix.app.data.TokenStore
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.login.LoginViewModel
import tv.jetflix.app.ui.theme.JetflixTheme

/** Quick Connect sign-in against a scripted Jellyfin server (MockWebServer), plus the code panel UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class QuickConnectTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var vm: LoginViewModel

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        SessionManager.signOut()
    }

    @After fun tearDown() {
        SessionManager.signOut()
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)


    /** Drives the test scheduler while giving OkHttp's real callback threads time to land between advances. */
    private fun await(advance: () -> Unit, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done() && System.currentTimeMillis() < deadline) {
            advance()
            Thread.sleep(25)
        }
    }

    private fun newVm(): LoginViewModel {
        // Robolectric's keystore can't do the app's real encryption; a pass-through keeps the save path exercised.
        val passthrough = object : TokenCrypto {
            override fun encrypt(plain: String): String = plain
            override fun decrypt(blob: String): String = blob
        }
        val tokenStore = TokenStore(RuntimeEnvironment.getApplication(), passthrough)
        return LoginViewModel(JellyfinRepository(), tokenStore)
    }

    @Test fun approvalSignsInAndSavesTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        server.enqueue(json("""{"ServerName":"demo","Version":"10.11.0","Id":"srv1"}"""))
        server.enqueue(json("""{"Secret":"sec123","Code":"QWERTY","Authenticated":false}"""))
        server.enqueue(json("""{"Secret":"sec123","Code":"QWERTY","Authenticated":false}"""))
        server.enqueue(json("""{"Secret":"sec123","Code":"QWERTY","Authenticated":true}"""))
        server.enqueue(json("""{"User":{"Id":"u1","Name":"randy"},"AccessToken":"tok-1"}"""))
        vm = newVm()
        vm.update(serverUrl = server.url("/").toString())
        var succeeded = 0
        vm.startQuickConnect { succeeded++ }
        // The coroutine suspends on real network callbacks between scheduler advances.
        await({ advanceUntilIdle() }) { succeeded == 1 || vm.state.value.error != null }

        assertEquals(1, succeeded)
        val session = SessionManager.session.value
        assertEquals("tok-1", session?.token)
        assertEquals("randy", session?.userName)
        assertNull(vm.state.value.quickCode)
        assertTrue(vm.state.value.savedProfiles.any { it == "randy" })
        // The poll carried the server's secret, and the code was shown while waiting.
        val paths = (1..server.requestCount).map { server.takeRequest().path }
        assertTrue(paths.any { it?.startsWith("/QuickConnect/Connect?secret=sec123") == true })
    }

    @Test fun disabledServerExplainsAndKeepsPasswordForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        server.enqueue(json("""{"ServerName":"demo","Version":"10.11.0","Id":"srv1"}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        vm = newVm()
        vm.update(serverUrl = server.url("/").toString())
        var succeeded = 0
        vm.startQuickConnect { succeeded++ }
        await({ advanceUntilIdle() }) { succeeded == 1 || vm.state.value.error != null }

        assertEquals(0, succeeded)
        assertNull(vm.state.value.quickCode)
        assertTrue(vm.state.value.error?.contains("Quick Connect isn't enabled") == true)
        assertNull(SessionManager.session.value)
    }

    @Test fun codePanelShowsCodeAndCancels() {
        var cancels = 0
        rule.setContent {
            JetflixTheme {
                LoginContent(LoginUiState(serverUrl = "http://demo.test", quickCode = "QWERTY"), { _, _, _ -> }, {}, onCancelQuickConnect = { cancels++ })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("QWERTY").assertExists()
        rule.onNodeWithText("Cancel").requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, cancels)
    }

    @Test fun passwordFormOffersQuickConnect() {
        rule.setContent {
            JetflixTheme { LoginContent(LoginUiState(serverUrl = "http://demo.test"), { _, _, _ -> }, {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Use Quick Connect instead").assertExists()
    }
}
