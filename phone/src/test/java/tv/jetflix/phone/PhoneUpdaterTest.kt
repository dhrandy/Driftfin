package tv.jetflix.phone

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
import tv.jetflix.phone.update.*
import java.io.IOException
import java.security.MessageDigest

/** Synthetic APK bytes and a local server only. Nothing here is a real release. */
class PhoneUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private val apk = ByteArray(5000) { (it % 251).toByte() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Before fun up() { server = MockWebServer().apply { start() }; other = MockWebServer().apply { start() } }
    @After fun down() { server.shutdown(); other.shutdown() }

    private fun base() = server.url("/dl/").toString()
    private fun src(hosts: Set<String> = setOf(server.hostName)) = UpdateSource(
        releasesUrl = server.url("/releases").toString(), assetPrefix = base(), allowedHosts = hosts, requireHttps = false)
    private fun release(tag: String = "phone-build-20", draft: Boolean = false, apkUrl: String? = null, digest: String? = "sha256:" + sha(apk), size: Long = apk.size.toLong()) =
        """{"tag_name":"$tag","draft":$draft,"assets":[{"name":"driftfin-phone-release.apk","browser_download_url":"${apkUrl ?: (base() + tag + "/driftfin-phone-release.apk")}","size":$size,"digest":${if (digest == null) "null" else "\"$digest\""}},
        {"name":"driftfin-phone-release.apk.sha256","browser_download_url":"${base()}$tag/driftfin-phone-release.apk.sha256","size":86}]}"""
    private fun info(digest: String? = "sha256:" + sha(apk), size: Long = apk.size.toLong()): ReleaseInfo {
        val r = (Updater(src()).check(10) as CheckResult.Available).info
        return r.copy(apk = r.apk.copy(digest = digest, size = size))
    }
    private fun enqueueRelease(body: String = release()) = enqueueReleases(body)
    private fun enqueueReleases(vararg bodies: String) { server.enqueue(MockResponse().setBody(bodies.joinToString(",", "[", "]"))) }
    private fun updater() = Updater(src(), OkHttpClient())
    private fun dest() = tmp.newFolder().resolve("u/x.apk")

    @Test fun newerBuildIsOfferedSameOrOlderIsNot() {
        enqueueRelease(); assertTrue(updater().check(19) is CheckResult.Available)
        enqueueRelease(); assertEquals(CheckResult.UpToDate(20), updater().check(20))
        enqueueRelease(); assertEquals(CheckResult.UpToDate(25), updater().check(25))
    }

    @Test fun picksTheHighestPhoneBuildNotTheFirstListed() {
        enqueueReleases(release(tag = "phone-build-19"), release(tag = "phone-build-20"))
        assertEquals(20, (updater().check(10) as CheckResult.Available).info.build)
        enqueueReleases(release(tag = "phone-build-20"), release(tag = "phone-build-19"))
        assertEquals(20, (updater().check(10) as CheckResult.Available).info.build)
    }

    @Test fun tvReleasesAreNotPhoneUpdates() {
        enqueueReleases(release(tag = "build-999"))
        assertTrue(updater().check(1) is CheckResult.Failed)
        enqueueReleases(release(tag = "build-999"), release(tag = "phone-build-3"))
        assertEquals(3, (updater().check(1) as CheckResult.Available).info.build)
    }

    @Test fun noReleasesAtAllFails() {
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(updater().check(1) is CheckResult.Failed)
    }

    @Test fun draftReleasesAreIgnored() {
        enqueueReleases(release(tag = "phone-build-21", draft = true), release(tag = "phone-build-20"))
        assertEquals(20, (updater().check(10) as CheckResult.Available).info.build)
        enqueueReleases(release(tag = "phone-build-21", draft = true))
        assertTrue(updater().check(1) is CheckResult.Failed)
    }

    @Test fun followsPagesUntilAPhoneReleaseTurnsUp() {
        server.enqueue(MockResponse().setBody("[${release(tag = "build-100")}]")
            .setHeader("Link", "<${server.url("/releases?page=2")}>; rel=\"next\""))
        enqueueReleases(release(tag = "phone-build-7"))
        assertEquals(7, (updater().check(1) as CheckResult.Available).info.build)
        assertEquals(2, server.requestCount)
    }

    @Test fun aHigherBuildOnALaterPageWins() {
        // Numbering is trusted, not page order: a recreated release can land out of order.
        server.enqueue(MockResponse().setBody("[${release(tag = "phone-build-3")}]")
            .setHeader("Link", "<${server.url("/releases?page=2")}>; rel=\"next\""))
        enqueueReleases(release(tag = "phone-build-12"))
        assertEquals(12, (updater().check(1) as CheckResult.Available).info.build)
        assertEquals(2, server.requestCount)
    }

    @Test fun refusesWhenPagesRunPastTheCap() {
        repeat(5) { n ->
            server.enqueue(MockResponse().setBody("[${release(tag = "build-${100 - n}")}]")
                .setHeader("Link", "<${server.url("/releases?page=${n + 2}")}>; rel=\"next\""))
        }
        assertTrue(updater().check(1) is CheckResult.Failed)
        assertEquals(5, server.requestCount)
    }

    @Test fun nextPageOnAnotherHostIsRefused() {
        // same machine, but a host name that is not the page's host
        val alias = if (server.hostName == "127.0.0.1") "localhost" else "127.0.0.1"
        val foreign = other.url("/releases?page=2").newBuilder().host(alias).build()
        server.enqueue(MockResponse().setBody("[${release(tag = "build-100")}]")
            .setHeader("Link", "<$foreign>; rel=\"next\""))
        assertTrue(updater().check(1) is CheckResult.Failed)
        assertEquals("the other host must never be contacted", 0, other.requestCount)
    }

    @Test fun oddTagsAndOffSiteAssetsAreRejected() {
        enqueueRelease(release(tag = "v2.0")); assertTrue(updater().check(1) is CheckResult.Failed)
        enqueueRelease(release(tag = "phone-build-99999999999")); assertTrue(updater().check(1) is CheckResult.Failed)
        enqueueRelease(release(apkUrl = "http://evil.test/driftfin-phone-release.apk")); assertTrue(updater().check(1) is CheckResult.Failed)
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
        server.enqueue(MockResponse().setBody("${sha(apk)}  driftfin-phone-release.apk\n"))
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
        val u = Updater(UpdateSource(releasesUrl = server.url("/releases").toString()))
        assertTrue(u.check(1) is CheckResult.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test fun defaultSourceIsTheProjectsGithubOnly() {
        val d = UpdateSource()
        assertTrue(d.releasesUrl.startsWith("https://api.github.com/repos/dhrandy/Driftfin/releases"))
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
        val flow = UpdateFlow(updater(), 10, { ApkIdentity("tv.jetflix.app", 10, setOf("aa")) }, { ApkIdentity("tv.jetflix.app", 20, setOf("aa")) }, { launched = true; null }, dest())
        val r = flow.downloadAndInstall((flow.check() as UpdateUi.Available).info)
        assertEquals(UpdateUi.ReadyToInstall(20), r); assertTrue(launched)
    }
}
