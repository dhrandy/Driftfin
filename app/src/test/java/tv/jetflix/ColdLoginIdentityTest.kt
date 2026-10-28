package tv.jetflix
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.*
import tv.jetflix.app.ui.login.LoginViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ColdLoginIdentityTest {
 private val ctx:Context get()=ApplicationProvider.getApplicationContext()
 private val crypto=object:TokenCrypto { override fun encrypt(plain:String)=plain;override fun decrypt(blob:String)=blob }
 private lateinit var server:MockWebServer
 @Before fun up(){ Dispatchers.setMain(UnconfinedTestDispatcher());SessionManager.signOut();ctx.getSharedPreferences("jetflix_session",0).edit().clear().commit();server=MockWebServer();server.start() }
 @After fun down(){server.shutdown();SessionManager.signOut();Dispatchers.resetMain()}
 private fun store()=TokenStore(ctx,crypto)
 private fun waitFor(done:()->Boolean){val end=System.currentTimeMillis()+5000;while(!done()&&System.currentTimeMillis()<end)Thread.sleep(20);assertTrue(done())}
 private fun auth()=MockResponse().setBody("""{"User":{"Id":"u","Name":"Demo"},"AccessToken":"new-token"}""")
 @Test fun passwordHeaderMatchesPersistedIdentityAndTwoInstallsDiffer(){
  val ids=mutableListOf<String>()
  repeat(2){
   ctx.getSharedPreferences("jetflix_session",0).edit().clear().commit();SessionManager.signOut()
   val st=store();val id=st.deviceId();assertEquals(id,store().deviceId());ids+=id
   server.enqueue(MockResponse().setBody("""{"Id":"server"}"""));server.enqueue(auth())
   val vm=LoginViewModel(JellyfinRepository(),st);vm.update(serverUrl=server.url("/").toString(),username="Demo",password="p")
   var done=false;vm.connect{done=true};waitFor{done}
   assertEquals(id,SessionManager.session.value!!.deviceId);assertEquals(id,store().load()!!.deviceId)
   repeat(2){val h=server.takeRequest().getHeader("Authorization")!!;assertTrue(h.contains("DeviceId=\"$id\""));assertFalse(h.contains("Token="))}
   vm.cancelPendingLogin()
  }
  assertNotEquals(ids[0],ids[1])
 }
 @Test fun cancellingDelayedPasswordCannotReplaceOldSession(){
  val entered=CountDownLatch(1);val release=CountDownLatch(1)
  server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(r:RecordedRequest):MockResponse {
   if(r.path!!.contains("AuthenticateByName")){entered.countDown();release.await(4,TimeUnit.SECONDS);return auth()}
   return MockResponse().setBody("""{"Id":"server"}""")
  }}
  val old=Session("https://old.invalid","old","Old","old-token","old-device");SessionManager.signIn(old)
  val vm=LoginViewModel(JellyfinRepository(),store());vm.update(serverUrl=server.url("/").toString(),username="Demo",password="p")
  var done=false;vm.connect{done=true};assertTrue(entered.await(4,TimeUnit.SECONDS));vm.cancelPendingLogin();release.countDown();Thread.sleep(300)
  assertFalse(done);assertEquals(old,SessionManager.session.value);assertNull(store().load());assertFalse(vm.state.value.connecting)
 }

 @Test fun cancellingQuickConnectInitializationCannotFinishLater(){
  val entered=CountDownLatch(1);val release=CountDownLatch(1)
  server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(r:RecordedRequest):MockResponse {
   if(r.path!!.contains("QuickConnect/Initiate")){entered.countDown();release.await(4,TimeUnit.SECONDS);return MockResponse().setBody("""{"Secret":"s","Code":"ABC123","Authenticated":false}""")}
   return MockResponse().setBody("""{"Id":"server"}""")
  }}
  val old=Session("https://old.invalid","old","Old","old-token","old-device");SessionManager.signIn(old)
  val vm=LoginViewModel(JellyfinRepository(),store());vm.update(serverUrl=server.url("/").toString())
  var done=false;vm.startQuickConnect{done=true};assertTrue(entered.await(4,TimeUnit.SECONDS));vm.cancelPendingLogin();release.countDown();Thread.sleep(300)
  assertFalse(done);assertEquals(old,SessionManager.session.value);assertNull(vm.state.value.quickCode);assertFalse(vm.state.value.connecting)
 }
}
