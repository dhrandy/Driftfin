package tv.jetflix.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** The parts that need a real device. Not provable in unit tests: see README. */
object AndroidUpdate {
    fun cacheFile(context: Context) = File(File(context.cacheDir, "updates"), "driftfin-update.apk")

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun identity(info: android.content.pm.PackageInfo?): ApkIdentity? {
        val i = info ?: return null
        val signers = i.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet().orEmpty()
        return ApkIdentity(i.packageName, i.longVersionCode, signers)
    }

    fun installed(context: Context): ApkIdentity =
        identity(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))!!

    fun readApk(context: Context, f: File): ApkIdentity? = try {
        identity(context.packageManager.getPackageArchiveInfo(f.path, PackageManager.GET_SIGNING_CERTIFICATES))
    } catch (_: Exception) { null }

    /** Opens the system installer. Returns a message when the user must first allow installs from this app. */
    fun install(context: Context, f: File): String? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val s = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { context.startActivity(s); "Allow installs from Driftfin in the screen that just opened, then press Check for updates again." }
            catch (_: Exception) { "Allow \"Install unknown apps\" for Driftfin in your TV's settings, then try again." }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(i); null } catch (_: Exception) { "This TV has no app installer available." }
    }
}
