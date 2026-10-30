package tv.jetflix

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jetflix.app.update.*
import java.io.IOException
import java.security.MessageDigest

/** Synthetic APK bytes and a local server only. Nothing here is a real release. */
class UpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private val apk = ByteArray(5000) { (it % 251).toByte() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Before fun up() { server = MockWebServer().apply { start() }; other = MockWebServer().apply { start() } }
    @After fun down() { server.shutdown(); other.shutdown() }

    private fun base() = server.url("/dl/").toString()
    private fun src(hosts: Set<String> = setOf(server.hostName)) = UpdateSource(
        latestReleaseUrl = server.url("/latest").toString(), assetPrefix = base(), allowedHosts = hosts, requireHttps = false)
    private fun release(tag: String = "build-20", apkUrl: String? = null, digest: String? = "sha256:" + sha(apk), size: Long = apk.size.toLong()) =
        """{"tag_name":"$tag","assets":[{"name":"jetflix-release.apk","browser_download_url":"${apkUrl ?: (base() + tag + "/jetflix-release.apk")}","size":$size,"digest":${if (digest == null) "null" else "\"$digest\""}},
        {"name":"jetflix-release.apk.sha256","browser_download_url":"${base()}$tag/jetflix-release.apk.sha256","size":86}]}"""
    private fun info(digest: String? = "sha256:" + sha(apk), size: Long = apk.size.toLong()): ReleaseInfo {
        val r = (Updater(src()).check(10) as CheckResult.Available).info
        return r.copy(apk = r.apk.copy(digest = digest, size = size))
    }
    private fun enqueueRelease(body: String = release()) { server.enqueue(MockResponse().setBody(body)) }
    private fun updater() = Updater(src(), OkHttpClient())
    private fun dest() = tmp.newFolder().resolve("u/x.apk")

    @Test fun newerBuildIsOfferedSameOrOlderIsNot() {
        enqueueRelease(); assertTrue(updater().check(19) is CheckResult.Available)
        enqueueRelease(); assertEquals(CheckResult.UpToDate(20), updater().check(20))
        enqueueRelease(); assertEquals(CheckResult.UpToDate(25), updater().check(25))
    }

    @Test fun oddTagsAndOffSiteAssetsAreRejected() {
        enqueueRelease(release(tag = "v2.0")); assertTrue(updater().check(1) is CheckResult.Failed)
        enqueueRelease(release(tag = "build-99999999999")); assertTrue(updater().check(1) is CheckResult.Failed)
        enqueueRelease(release(apkUrl = "http://evil.test/jetflix-release.apk")); assertTrue(updater().check(1) is CheckResult.Failed)
        server.enqueue(MockResponse().setResponseCode(500)); assertTrue(updater().check(1) is CheckResult.Failed)
        server.enqueue(MockResponse().setBody("not json")); assertTrue(updater().check(1) is CheckResult.Failed)
    }

    @Test fun checkSendsNoCredentials() {
        enqueueRelease(); updater().check(1)
        val r = server.takeRequest()
        assertNull(r.getHeader("Authorization")); assertNull(r.getHeader("X-Emby-Authorization")); assertNull(r.getHeader("Cookie"))
    }

    @Test fun goodDownloadVerifies() {
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  jetflix-release.apk\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val i = (updater().check(1) as CheckResult.Available).info
        val f = updater().download(i, dest())
        assertEquals(sha(apk), sha(f.readBytes()))
    }

    @Test fun checksumMismatchDeletesTheFile() {
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(apk.copyOf().also { it[0] = 9 })))
        val i = (updater().check(1) as CheckResult.Available).info
        val d = dest()
        try { updater().download(i, d); fail() } catch (_: IOException) { }
        assertFalse(d.exists())
    }

    @Test fun apiDigestAndShaFileMustBothAgree() {
        enqueueRelease(release(digest = "sha256:" + "0".repeat(64)))
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val i = (updater().check(1) as CheckResult.Available).info
        val d = dest()
        try { updater().download(i, d); fail() } catch (_: IOException) { }
        assertFalse(d.exists())
    }

    @Test fun oversizeBodyIsCutOff() {
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(apk.size * 3))))
        val i = (updater().check(1) as CheckResult.Available).info
        val d = dest()
        try { updater().download(i, d); fail() } catch (_: IOException) { }
        assertFalse(d.exists())
    }

    @Test fun redirectToAnUnlistedHostIsRefusedAndNothingIsSentThere() {
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        // same machine, but a host name that is not on the allow list
        val alias = if (server.hostName == "127.0.0.1") "localhost" else "127.0.0.1"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/a.apk").newBuilder().host(alias).build().toString()))
        other.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val i = (updater().check(1) as CheckResult.Available).info
        val d = dest()
        try { updater().download(i, d); fail() } catch (_: IOException) { }
        assertEquals("the other host must never be contacted", 0, other.requestCount)
        assertFalse(d.exists())
    }

    @Test fun redirectToAListedHostIsFollowedWithoutCredentials() {
        // Listed by host name only (not port): use the second server's name under a different alias, so allow both hosts.
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/a.apk").toString()))
        other.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val u = Updater(src(setOf(server.hostName)), OkHttpClient())
        val i = (u.check(1) as CheckResult.Available).info
        // same host name, different port: allowed by host rule, so the redirect is followed
        val f = u.download(i, dest())
        assertEquals(sha(apk), sha(f.readBytes()))
        assertNull(other.takeRequest().getHeader("Authorization"))
    }

    @Test fun redirectLoopStops() {
        enqueueRelease()
        server.enqueue(MockResponse().setBody("${sha(apk)}  x\n"))
        repeat(8) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/loop").toString())) }
        val i = (updater().check(1) as CheckResult.Available).info
        try { updater().download(i, dest()); fail() } catch (_: IOException) { }
    }

    @Test fun missingChecksumRefuses() {
        enqueueRelease(release(digest = null))
        server.enqueue(MockResponse().setResponseCode(404))
        val i = (updater().check(1) as CheckResult.Available).info
        try { updater().download(i, dest()); fail() } catch (_: IOException) { }
    }

    @Test fun httpsIsRequiredByDefault() {
        val u = Updater(UpdateSource(latestReleaseUrl = server.url("/latest").toString()))
        assertTrue(u.check(1) is CheckResult.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test fun defaultSourceIsTheProjectsGithubOnly() {
        val d = UpdateSource()
        assertTrue(d.latestReleaseUrl.startsWith("https://api.github.com/repos/dhrandy/Driftfin/"))
        assertTrue(d.assetPrefix.startsWith("https://github.com/dhrandy/Driftfin/releases/download/"))
        assertEquals(setOf("github.com", "release-assets.githubusercontent.com"), d.allowedHosts)
        assertTrue(d.requireHttps)
    }

    // Preflight decisions
    private val mine = ApkIdentity("tv.jetflix.app", 15, setOf("aa"))
    @Test fun preflightAcceptsOnlyNewerSamePackageSameSigner() {
        assertNull(UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 16, setOf("aa")), mine, 16))
        assertNotNull("other package", UpdatePreflight.reject(ApkIdentity("evil.app", 16, setOf("aa")), mine, 16))
        assertNotNull("other signer", UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 16, setOf("bb")), mine, 16))
        assertNotNull("extra signer", UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 16, setOf("aa", "bb")), mine, 16))
        assertNotNull("no signer", UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 16, emptySet()), mine, 16))
        assertNotNull("not the release", UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 17, setOf("aa")), mine, 16))
        assertNotNull("downgrade", UpdatePreflight.reject(ApkIdentity("tv.jetflix.app", 15, setOf("aa")), mine, 15))
        assertNotNull("unreadable", UpdatePreflight.reject(null, mine, 16))
    }

    @Test fun installerIsNotLaunchedWhenPreflightFails() {
        enqueueRelease(); server.enqueue(MockResponse().setBody("${sha(apk)}  x\n")); server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        var launched = false
        val d = dest()
        val flow = UpdateFlow(updater(), 10, { ApkIdentity("tv.jetflix.app", 10, setOf("aa")) }, { ApkIdentity("tv.jetflix.app", 20, setOf("zz")) }, { launched = true; null }, d)
        val r = flow.downloadAndInstall((flow.check() as UpdateUi.Available).info)
        assertTrue(r is UpdateUi.Error); assertFalse(launched); assertFalse(d.exists())
    }

    @Test fun installerIsLaunchedWhenEverythingChecksOut() {
        enqueueRelease(); server.enqueue(MockResponse().setBody("${sha(apk)}  x\n")); server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        var launched = false
        val d = dest()
        val flow = UpdateFlow(updater(), 10, { ApkIdentity("tv.jetflix.app", 10, setOf("aa")) }, { ApkIdentity("tv.jetflix.app", 20, setOf("aa")) }, { launched = true; null }, d)
        val r = flow.downloadAndInstall((flow.check() as UpdateUi.Available).info)
        assertEquals(UpdateUi.ReadyToInstall(20, d), r); assertTrue(launched)
    }
}
