package tv.jetflix.app.update

import java.io.File
import java.io.IOException

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val build: Int) : UpdateUi
    data class Available(val info: ReleaseInfo) : UpdateUi
    data object Downloading : UpdateUi
    /** The APK was downloaded, verified and handed to the installer; [file] is kept so a dismissed
     * installer can be reopened without downloading the same build again (Randy: "download twice"). */
    data class ReadyToInstall(val build: Int, val file: File) : UpdateUi
    data class Error(val message: String) : UpdateUi
}

/**
 * The user-triggered steps, with Android parts injected so they can be tested:
 * check -> (user presses OK again) -> download -> preflight -> hand to the system installer.
 */
class UpdateFlow(
    private val updater: Updater,
    private val installedBuild: Int,
    private val installed: () -> ApkIdentity,
    private val readApk: (File) -> ApkIdentity?,
    private val install: (File) -> String?,
    private val cacheFile: File,
    /** Returns the message to show when the app may not install packages yet (opening the grant page),
     * or null when installing is allowed. Checked BEFORE the APK is downloaded. */
    private val canInstall: () -> String? = { null },
) {
    fun check(): UpdateUi = when (val r = updater.check(installedBuild)) {
        is CheckResult.UpToDate -> UpdateUi.UpToDate(r.build)
        is CheckResult.Available -> UpdateUi.Available(r.info)
        is CheckResult.Failed -> UpdateUi.Error(r.reason)
    }

    /**
     * Settings opened with no known update: look once, quietly, so the row already reads "Download" when a newer
     * build exists and installing is one OK press. The startup notice only runs when the app starts, so a TV that
     * has been running since before a release would otherwise need Check, then Download. A failed look is silent.
     * Nothing is downloaded here.
     */
    fun refreshOnOpen(current: UpdateUi): UpdateUi {
        if (current !is UpdateUi.Idle) return current
        return when (val r = check()) {
            is UpdateUi.Error -> UpdateUi.Idle
            else -> r
        }
    }

    fun downloadAndInstall(info: ReleaseInfo): UpdateUi {
        // A first-ever install needs the unknown-sources grant: ask BEFORE spending the download,
        // not after, so granting it never makes the TV fetch the same APK a second time.
        canInstall()?.let { return UpdateUi.Error(it) }
        val file = try { updater.download(info, cacheFile) } catch (e: IOException) {
            return UpdateUi.Error("Download failed or did not verify. Nothing was installed.")
        }
        val why = UpdatePreflight.reject(readApk(file), installed(), info.build)
        if (why != null) { file.delete(); return UpdateUi.Error("$why Nothing was installed.") }
        val problem = install(file)
        return if (problem != null) UpdateUi.Error(problem) else UpdateUi.ReadyToInstall(info.build, file)
    }

    /** Reopens the system installer with the APK already downloaded and verified: dismissing the
     * installer must never make the TV download the same build again. If the cached file is gone
     * (cache cleared), fall back to a fresh check so the row offers a real download. */
    fun installAgain(state: UpdateUi.ReadyToInstall): UpdateUi {
        if (!state.file.exists()) return check()
        val problem = install(state.file)
        return if (problem != null) UpdateUi.Error(problem) else state
    }
}
