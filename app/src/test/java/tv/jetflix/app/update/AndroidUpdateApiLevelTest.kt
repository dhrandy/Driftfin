package tv.jetflix.app.update

import android.content.Context
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * AndroidUpdate reads the installed/downloaded app's identity and the install permission. Several platform
 * members it used (PackageInfo.signingInfo, longVersionCode: API 28; canRequestPackageInstalls: API 26)
 * do not exist on older Android, where touching them is a NoSuchFieldError/NoSuchMethodError. These run the
 * real code against each platform's own android-all jar, so a missing member fails here and not on a TV.
 * Written against Robolectric 4.14.1 APIs.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidUpdateApiLevelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** API 25-27 have no signingInfo/longVersionCode: identity must come from signatures + versionCode. */
    @Test @Config(sdk = [25, 26, 27])
    fun installedIdentityOnPreApi28UsesLegacySignaturesAndVersionCode() {
        val cert = byteArrayOf(1, 2, 3, 4)
        val pm = context.packageManager
        val info = shadowOf(pm).getInternalMutablePackageInfo(context.packageName)
        info.signatures = arrayOf(Signature(cert))
        @Suppress("DEPRECATION")
        info.versionCode = 50
        val id = AndroidUpdate.installed(context)
        assertEquals(context.packageName, id.packageName)
        assertEquals(50L, id.versionCode)
        assertEquals(setOf(sha(cert)), id.signerSha256)
    }

    /** Same signer bytes must give the same hash the API 28+ path gives, so the preflight signer check keeps its meaning. */
    @Test @Config(sdk = [25, 27])
    fun legacySignerHashMatchesPreflightComparison() {
        val cert = byteArrayOf(9, 8, 7)
        val info = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
        info.signatures = arrayOf(Signature(cert))
        @Suppress("DEPRECATION")
        info.versionCode = 51
        val installed = AndroidUpdate.installed(context)
        val sameSigner = ApkIdentity(installed.packageName, 52, setOf(sha(cert)))
        val otherSigner = ApkIdentity(installed.packageName, 52, setOf(sha(byteArrayOf(0))))
        assertNull(UpdatePreflight.reject(sameSigner, installed.copy(versionCode = 51), 52))
        assertEquals("The downloaded app is signed by a different certificate.",
            UpdatePreflight.reject(otherSigner, installed.copy(versionCode = 51), 52))
    }

    /** Before API 26 there is no per-app install permission; the check must not call the API 26 method. */
    @Test @Config(sdk = [25])
    fun installPermissionProblemIsNullOnApi25() {
        assertNull(AndroidUpdate.installPermissionProblem(context))
    }

    /** From API 26 the real permission is consulted: not granted gives a message, granted gives null. */
    @Test @Config(sdk = [26, 28, 34])
    fun installPermissionProblemUsesPermissionFromApi26() {
        val spm = shadowOf(context.packageManager)
        spm.setCanRequestPackageInstalls(true)
        assertNull(AndroidUpdate.installPermissionProblem(context))
        spm.setCanRequestPackageInstalls(false)
        assertTrue(AndroidUpdate.installPermissionProblem(context) != null)
    }

    /** installed() must not throw on any level; on 28+ the real signingInfo path is taken. */
    @Test @Config(sdk = [25, 26, 27, 28, 34])
    fun installedDoesNotThrowOnAnySupportedLevel() {
        val id = AndroidUpdate.installed(context)
        assertEquals(context.packageName, id.packageName)
    }

    /** An unreadable or missing file must come back null on every level, never an Error. */
    @Test @Config(sdk = [25, 26, 27, 28, 34])
    fun readApkOfMissingOrGarbageFileIsNull() {
        assertNull(AndroidUpdate.readApk(context, File(context.cacheDir, "does-not-exist.apk")))
        val junk = File(context.cacheDir, "junk.apk").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
        assertNull(AndroidUpdate.readApk(context, junk))
    }
}
