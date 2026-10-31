package tv.jetflix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.*
import tv.jetflix.app.data.*
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w960dp-h540dp-xhdpi")
class SwitchCancelNavigationTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val server=okhttp3.mockwebserver.MockWebServer()
 @Before fun start(){server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(r:okhttp3.mockwebserver.RecordedRequest)=okhttp3.mockwebserver.MockResponse().setBody(if(r.path!!.contains("Users/Public") || r.path!!.contains("Latest")) "[]" else "{\"Items\":[],\"TotalRecordCount\":0}")};server.start()}
 @After fun stop(){SessionManager.signOut();server.shutdown()}
 private fun key(k:Key){rule.onRoot().performKeyInput{pressKey(k)};rule.waitForIdle()}
 @Test fun switchBackRestoresSettingsAndDoesNotSignOut(){
  rule.runOnIdle { AppSettings.lastSeenBuild=Int.MAX_VALUE;AppSettings.setStartupWall(false);SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'),"u","Demo","fake","install")) }
  rule.waitForIdle()
  rule.onNodeWithText("Settings").requestFocus();key(Key.DirectionCenter)
  rule.onNodeWithText("Account").requestFocus();rule.waitForIdle()
  rule.onNodeWithText("Switch profile").requestFocus();key(Key.DirectionCenter)
  rule.onNodeWithText("Sign in with Jellyfin").assertExists()
  assertEquals("fake",SessionManager.session.value?.token)
  rule.activityRule.scenario.onActivity{it.onBackPressedDispatcher.onBackPressed()};rule.waitForIdle()
  rule.onNodeWithText("Account").requestFocus();rule.waitForIdle()
  rule.onNodeWithText("Switch profile").assertExists();rule.onNodeWithText("Sign in with Jellyfin").assertDoesNotExist()
  assertEquals("fake",SessionManager.session.value?.token)
  rule.onNodeWithText("Sign out and forget this profile").requestFocus();key(Key.DirectionCenter)
  rule.onNodeWithText("Sign in with Jellyfin").assertExists();assertNull(SessionManager.session.value)
 }
}
