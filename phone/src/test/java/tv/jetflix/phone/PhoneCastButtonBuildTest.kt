package tv.jetflix.phone

import android.view.ContextThemeWrapper
import androidx.mediarouter.app.MediaRouteButton
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.cast.CastManager

/** The cast button is an AppCompat-era widget: it must be built on an AppCompat
 *  theme (the app runs a platform Material theme) and must never crash the screen
 *  it sits on when the framework can't build it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneCastButtonBuildTest {

    @Test
    fun mediaRouteButtonBuildsOnAppCompatTheme() {
        val activity = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java).setup().get()
        val themed = ContextThemeWrapper(activity, androidx.appcompat.R.style.Theme_AppCompat)
        MediaRouteButton(themed) // must not throw
    }

    @Test
    fun buttonBrokenHidesTheButton() {
        CastManager.buttonBroken = true
        assert(!CastManager.available) { "buttonBroken must hide the cast button" }
        CastManager.buttonBroken = false
    }
}
