package tv.jetflix.app.update

import java.io.File
import java.io.IOException

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val build: Int) : UpdateUi
    data class Available(val info: ReleaseInfo) : UpdateUi
    data object Downloading : UpdateUi
    data class ReadyToInstall(val build: Int) : UpdateUi
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
        val file = try { updater.download(info, cacheFile) } catch (e: IOException) {
            return UpdateUi.Error("Download failed or did not verify. Nothing was installed.")
        }
        val why = UpdatePreflight.reject(readApk(file), installed(), info.build)
        if (why != null) { file.delete(); return UpdateUi.Error("$why Nothing was installed.") }
        val problem = install(file)
        return if (problem != null) UpdateUi.Error(problem) else UpdateUi.ReadyToInstall(info.build)
    }
}
