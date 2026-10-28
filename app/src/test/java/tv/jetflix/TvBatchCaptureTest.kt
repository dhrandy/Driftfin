package tv.jetflix
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.ui.login.*
import tv.jetflix.app.ui.settings.SettingsContent
import java.io.File
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class TvBatchCaptureTest {
 @get:Rule val rule=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private fun capture(name:String){rule.waitForIdle();val v=rule.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File("build/outputs/qa/$name.png");f.parentFile.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun login(){rule.setContent{JetflixTheme{LoginContent(LoginUiState(serverUrl="https://demo.test"),{_,_,_->},{})}};capture("batch-login")}
 @Test fun settings(){rule.setContent{JetflixTheme{SettingsContent(true,true,true,"Demo","https://demo.test",{},{},{},{},{})}};capture("batch-settings")}

 @Test fun animationSettingFocused(){
  var enabled=false
  rule.setContent{JetflixTheme{SettingsContent(true,true,true,"Demo",null,{},{},{},{},{},excludeHomeAnimation=enabled,onToggleExcludeHomeAnimation={enabled=!enabled})}}
  rule.onNodeWithText("Exclude animated movies from Home banner").requestFocus();rule.waitForIdle()
  rule.onRoot().performKeyInput{pressKey(Key.DirectionCenter)};rule.waitForIdle();Assert.assertTrue(enabled)
  capture("batch-animation-setting")
 }
}
