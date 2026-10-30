package tv.jetflix.phone

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.PhoneSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneSettingsTest {
    @Test fun defaultsMatchTvAndChangesPersist() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("driftfin_phone_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        PhoneSettings.init(ctx)
        assertTrue(PhoneSettings.titleLogos.value)
        assertTrue(PhoneSettings.autoplayNext.value)
        PhoneSettings.setTitleLogos(false)
        PhoneSettings.setAutoplayNext(false)
        PhoneSettings.init(ctx) // reload from disk, as after an app restart
        assertFalse(PhoneSettings.titleLogos.value)
        assertFalse(PhoneSettings.autoplayNext.value)
        PhoneSettings.setTitleLogos(true); PhoneSettings.setAutoplayNext(true)
    }
}
