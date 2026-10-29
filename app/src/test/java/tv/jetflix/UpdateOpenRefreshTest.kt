package tv.jetflix

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jetflix.app.update.*

/** A TV that started before a release existed: opening Settings looks once, so the row already says Download. */
class UpdateOpenRefreshTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer().apply { start() } }
    @After fun down() { server.shutdown() }

    private fun flow(installed: Int) = UpdateFlow(
        updater = Updater(UpdateSource(latestReleaseUrl = server.url("/latest").toString(), assetPrefix = server.url("/dl/").toString(),
            allowedHosts = setOf(server.hostName), requireHttps = false)),
        installedBuild = installed, installed = { error("unused") }, readApk = { null }, install = { null }, cacheFile = tmp.newFolder().resolve("u.apk"),
    )
    private fun release(tag: String) =
        """{"tag_name":"$tag","assets":[{"name":"jetflix-release.apk","browser_download_url":"${server.url("/dl/")}$tag/jetflix-release.apk","size":10,"digest":null},{"name":"jetflix-release.apk.sha256","browser_download_url":"${server.url("/dl/")}$tag/jetflix-release.apk.sha256","size":86}]}"""

    @Test fun newerBuildMakesTheRowDownloadWithoutAnyDownload() {
        server.enqueue(MockResponse().setBody(release("build-35")))
        val r = flow(34).refreshOnOpen(UpdateUi.Idle)
        assertTrue("expected Available, was $r", r is UpdateUi.Available)
        assertEquals(1, server.requestCount) // one release lookup, nothing downloaded
    }

    @Test fun sameBuildSaysUpToDateAndAFailedLookupStaysQuiet() {
        server.enqueue(MockResponse().setBody(release("build-35")))
        assertEquals(UpdateUi.UpToDate(35), flow(35).refreshOnOpen(UpdateUi.Idle))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(UpdateUi.Idle, flow(34).refreshOnOpen(UpdateUi.Idle))
    }

    @Test fun anAlreadyKnownStateIsNotRechecked() {
        val known = UpdateUi.Downloading
        assertEquals(known, flow(34).refreshOnOpen(known))
        assertEquals(0, server.requestCount)
    }
}
