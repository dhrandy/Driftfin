package tv.jetflix.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Fire TV launcher tile showed the round app icon instead of the wide banner. The banner was declared only
 * on <application>. Some launchers read it from the launcher activity, so MainActivity declares it too.
 * Reads the manifest source, so it cannot prove the tile on a device; that needs a look at the TV.
 */
class LauncherBannerManifestTest {
    private fun manifest(): String {
        val f = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }
        return f.readText()
    }

    @Test fun applicationAndLauncherActivityBothDeclareTheBanner() {
        val m = manifest()
        val appTag = Regex("<application[^>]*>", RegexOption.DOT_MATCHES_ALL).find(m)!!.value
        val activityTag = Regex("<activity[^>]*MainActivity[^>]*>", RegexOption.DOT_MATCHES_ALL).find(m)!!.value
        assertTrue("application banner", appTag.contains("android:banner=\"@drawable/banner_driftfin\""))
        assertTrue("MainActivity banner", activityTag.contains("android:banner=\"@drawable/banner_driftfin\""))
    }

    @Test fun mainActivityStillHasLeanbackAndPlainLauncherCategories() {
        val m = manifest()
        assertTrue(m.contains("android.intent.category.LAUNCHER"))
        assertTrue(m.contains("android.intent.category.LEANBACK_LAUNCHER"))
    }
}
