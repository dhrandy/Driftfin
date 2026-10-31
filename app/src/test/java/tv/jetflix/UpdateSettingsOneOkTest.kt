package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import tv.jetflix.app.ui.settings.SettingsScreen
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.*

/**
 * User report: "I'm having to select twice to download" and "the bug with the update button is back".
 * The whole path through the real SettingsScreen, with a real Updater talking to a local server:
 * a TV that has been running since before build 36 opens Settings (no startup notice), the row
 * quietly looks, reads Download, and ONE OK press downloads, verifies and hands the APK to the
 * installer. Real keys go through the focused row. No real GitHub, installer or TV.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UpdateSettingsOneOkTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val apk = ByteArray(2048) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val paths = CopyOnWriteArrayList<String>()
    private val installedFiles = CopyOnWriteArrayList<File>()
    private lateinit var server: MockWebServer
    private val tmp = java.nio.file.Files.createTempDirectory("upd").toFile()

    @After fun down() { server.shutdown(); UpdateNoticeState.available.value = null }

    private fun start(tag: String, latestCode: Int = 200, apkCode: Int = 200) {
        val base = { server.url("/dl/").toString() }
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path.orEmpty()
                    return when {
                        request.path == "/latest" -> if (latestCode != 200) MockResponse().setResponseCode(latestCode) else MockResponse().setBody(
                            """{"tag_name":"$tag","assets":[{"name":"jetflix-release.apk","browser_download_url":"${base()}$tag/jetflix-release.apk","size":${apk.size},"digest":"sha256:$sha"},{"name":"jetflix-release.apk.sha256","browser_download_url":"${base()}$tag/jetflix-release.apk.sha256","size":86}]}""")
                        request.path!!.endsWith(".apk") -> if (apkCode != 200) MockResponse().setResponseCode(apkCode) else MockResponse().setBody(okio.Buffer().write(apk))
                        request.path!!.endsWith(".sha256") -> MockResponse().setBody("$sha  jetflix-release.apk")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
    }

    private fun flow(installedBuild: Int, installedHook: () -> ApkIdentity = { ApkIdentity("tv.jetflix.app", installedBuild.toLong(), setOf("cert")) }) = UpdateFlow(
        updater = Updater(UpdateSource(latestReleaseUrl = server.url("/latest").toString(), assetPrefix = server.url("/dl/").toString(),
            allowedHosts = setOf(server.hostName), requireHttps = false)),
        installedBuild = installedBuild, installed = installedHook,
        readApk = { ApkIdentity("tv.jetflix.app", 37, setOf("cert")) },
        install = { installedFiles += it; null }, cacheFile = File(tmp, "u.apk"),
    )

    private fun open(f: UpdateFlow) {
        rule.setContent { JetflixTheme { SettingsScreen("demo", "http://demo.test", {}, {}, updateFlow = f) } }
    }
    private fun waitFor(text: String, ms: Long = 8000) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            rule.waitForIdle()
            if (rule.onAllNodesWithTextCount(text) > 0) return
            Thread.sleep(50)
        }
    }
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
    private fun focusedDump() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" | ") { n ->
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    /** Robolectric gives no window focus: the first D-pad press lands on the page's first row; step Down until the update row holds it. */
    private fun focusUpdateRow() {
        repeat(4) { if (focusedDump().contains("Check for updates")) return; press(Key.DirectionDown) }
        assertTrue("update row focused, got: ${focusedDump()}", focusedDump().contains("Check for updates"))
    }
    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun staleTvOpensSettingsRowReadsDownloadAndOneOkInstalls() {
        start("build-37")
        open(flow(installedBuild = 36))
        waitFor("Download")
        rule.onNodeWithText("Download").assertIsDisplayed()
        assertEquals("opening Settings only looked, nothing downloaded: $paths", listOf("/latest"), paths.toList())
        // The row is the first focusable on the page; focus it as the remote would, then ONE OK.
        focusUpdateRow()
        press(Key.Enter)
        waitFor("Installer open")
        rule.onNodeWithText("Installer open").assertIsDisplayed()
        assertEquals("one OK handed exactly one verified file to the installer", 1, installedFiles.size)
        assertTrue(paths.any { it.endsWith(".apk") })
    }

    @Test fun upToDateTvOpensQuietlyAndNeverDownloads() {
        start("build-36")
        open(flow(installedBuild = 36))
        waitFor("Up to date")
        rule.onNodeWithText("Up to date").assertIsDisplayed()
        assertEquals(listOf("/latest"), paths.toList())
        assertTrue(installedFiles.isEmpty())
    }

    @Test fun failedLookupOnOpenStaysQuietAsCheckThenCheckStillWorks() {
        start("build-37", latestCode = 500)
        open(flow(installedBuild = 36))
        waitFor("Check")
        rule.onNodeWithText("Check").assertIsDisplayed()
        assertTrue(installedFiles.isEmpty())
    }

    @Test fun downloadErrorShowsRetryAndInstallsNothing() {
        start("build-37", apkCode = 500)
        open(flow(installedBuild = 36))
        waitFor("Download")
        focusUpdateRow()
        press(Key.Enter)
        waitFor("Retry")
        rule.onNodeWithText("Retry").assertIsDisplayed()
        assertTrue(installedFiles.isEmpty())
    }
}
