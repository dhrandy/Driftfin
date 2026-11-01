package tv.jetflix.phone

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The keyboard must not overlay the app: with edge-to-edge, adjustResize delivers the IME as insets
 *  so the shell's imePadding can lift the content and bottom nav above it. Without this, the keyboard
 *  covers the nav bar on Search and taps meant for a tab land on the keyboard (seen in the field). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneKeyboardResizeTest {
    @Test fun mainActivityResizesForTheKeyboard() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx, MainActivity::class.java), 0)
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, info.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
    }
}
