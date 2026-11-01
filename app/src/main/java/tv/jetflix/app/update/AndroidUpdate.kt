package tv.jetflix.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** The parts that need a real device. Not provable in unit tests: see README. */
object AndroidUpdate {
    fun cacheFile(context: Context) = File(File(context.cacheDir, "updates"), "driftfin-update.apk")

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** PackageInfo.signingInfo and longVersionCode only exist from API 28; older Android uses signatures and versionCode.
     * Both paths hash the same signer certificate bytes, so the identity comparison keeps its meaning. */
    @Suppress("DEPRECATION")
    private val signerFlags: Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerCerts(i: PackageInfo): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= 28) i.signingInfo?.apkContentsSigners else i.signatures

    @Suppress("DEPRECATION")
    private fun versionCodeOf(i: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()

    private fun identity(info: PackageInfo?): ApkIdentity? {
        val i = info ?: return null
        val signers = signerCerts(i)?.map { sha(it.toByteArray()) }?.toSet().orEmpty()
        return ApkIdentity(i.packageName, versionCodeOf(i), signers)
    }

    fun installed(context: Context): ApkIdentity =
        identity(context.packageManager.getPackageInfo(context.packageName, signerFlags))!!

    fun readApk(context: Context, f: File): ApkIdentity? = try {
        identity(context.packageManager.getPackageArchiveInfo(f.path, signerFlags))
    } catch (_: Exception) { null }

    /** The message to show when the app may not install packages yet, opening the system page that grants
     * it; null when installs are allowed. Used before the APK download so the grant never costs a download. */
    fun installPermissionProblem(context: Context): String? {
        // The per-app "install unknown apps" permission exists from API 26; before that the system installer asks itself.
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) return null
        val s = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(s); "Allow installs from Driftfin in the screen that just opened, then press Check for updates again." }
        catch (_: Exception) { "Allow \"Install unknown apps\" for Driftfin in your TV's settings, then try again." }
    }

    /** Opens the system installer. Returns a message when the user must first allow installs from this app. */
    fun install(context: Context, f: File): String? {
        installPermissionProblem(context)?.let { return it }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(i); null } catch (_: Exception) { "This TV has no app installer available." }
    }
}
