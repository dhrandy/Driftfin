package tv.jetflix.phone.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Where updates come from. The defaults are the project's public GitHub releases; tests point them at a local server. */
data class UpdateSource(
    val releasesUrl: String = "https://api.github.com/repos/dhrandy/Driftfin/releases?per_page=100",
    val assetPrefix: String = "https://github.com/dhrandy/Driftfin/releases/download/",
    /** Hosts a download may be redirected to. Taken from the observed GitHub chain: github.com -> release-assets.githubusercontent.com. */
    val allowedHosts: Set<String> = setOf("github.com", "release-assets.githubusercontent.com"),
    val requireHttps: Boolean = true,
)

@Serializable
data class GhAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0, val digest: String? = null)

@Serializable
data class GhRelease(val tag_name: String = "", val draft: Boolean = false, val assets: List<GhAsset> = emptyList())

data class ReleaseInfo(val build: Int, val apk: GhAsset, val sha: GhAsset?)

sealed interface CheckResult {
    data class UpToDate(val build: Int) : CheckResult
    data class Available(val info: ReleaseInfo) : CheckResult
    data class Failed(val reason: String) : CheckResult
}

/** The package, version and signing certificates read from an APK file or from the installed app. */
data class ApkIdentity(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)

object UpdatePreflight {
    /** Returns why the candidate must NOT be handed to the installer, or null when it is acceptable. */
    fun reject(candidate: ApkIdentity?, installed: ApkIdentity, expectedBuild: Int): String? = when {
        candidate == null -> "The downloaded file is not a readable app package."
        candidate.packageName != installed.packageName -> "The downloaded app is a different package."
        candidate.versionCode != expectedBuild.toLong() -> "The downloaded app's version does not match the release."
        candidate.versionCode <= installed.versionCode -> "The downloaded app is not newer than the installed one."
        candidate.signerSha256.isEmpty() || installed.signerSha256.isEmpty() -> "The app's signing certificate could not be read."
        candidate.signerSha256 != installed.signerSha256 -> "The downloaded app is signed by a different certificate."
        else -> null
    }
}

class Updater(
    private val source: UpdateSource = UpdateSource(),
    client: OkHttpClient = OkHttpClient(),
    private val maxApkBytes: Long = 80L * 1024 * 1024,
) {
    /** Never follows redirects on its own and never carries any credential. */
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun hostAllowed(u: HttpUrl) = (!source.requireHttps || u.isHttps) && u.host in source.allowedHosts

    fun check(currentBuild: Int): CheckResult {
        val first = source.releasesUrl.toHttpUrlOrNull() ?: return CheckResult.Failed("Update address is invalid.")
        if (source.requireHttps && !first.isHttps) return CheckResult.Failed("Update address is not https.")
        return try {
            // Scan every page up to the cap and take the numeric highest phone build: creation
            // order is not trusted to match numbering. If pages remain past the cap, coverage is
            // unproven, so refuse instead of guessing.
            var url: HttpUrl? = first
            var pages = 0
            var latest: Pair<Int, GhRelease>? = null
            while (pages < MAX_PAGES) {
                val u = url ?: break
                val page = fetchReleasesPage(u)
                val best = page.first.mapNotNull { rel ->
                    // Phone builds are their own prerelease line (phone-build-N). The TV app's
                    // build-N releases share this repo and must never count as phone updates.
                    // Drafts are invisible to this credential-less request; skip one regardless.
                    if (rel.draft) return@mapNotNull null
                    val m = PHONE_TAG.matchEntire(rel.tag_name) ?: return@mapNotNull null
                    m.groupValues[1].toInt() to rel
                }.maxByOrNull { it.first }
                if (best != null && (latest == null || best.first > latest!!.first)) latest = best
                url = page.second
                pages++
            }
            if (url != null) return CheckResult.Failed("Too many releases to check safely.")
            val (build, rel) = latest ?: return CheckResult.Failed("No phone release found.")
            if (build <= currentBuild) return CheckResult.UpToDate(currentBuild)
            val base = source.assetPrefix + rel.tag_name + "/"
            val apk = rel.assets.firstOrNull { it.name == PHONE_APK_ASSET && it.browser_download_url == base + it.name }
                ?: return CheckResult.Failed("Release has no app file in the expected place.")
            val sha = rel.assets.firstOrNull { it.name == PHONE_SHA_ASSET && it.browser_download_url == base + it.name }
            CheckResult.Available(ReleaseInfo(build, apk, sha))
        } catch (e: HttpError) {
            CheckResult.Failed(if (e.code == 404) "No release found." else "Update server answered ${e.code}.")
        } catch (e: Exception) {
            CheckResult.Failed("Could not check for updates (${e.javaClass.simpleName}).")
        }
    }

    private class HttpError(val code: Int) : IOException("http $code")

    /** One page of releases and the next page's URL, following the Link header only on the same host. */
    private fun fetchReleasesPage(url: HttpUrl): Pair<List<GhRelease>, HttpUrl?> {
        if (source.requireHttps && !url.isHttps) throw IOException("not https")
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw HttpError(r.code)
            val text = r.peekBody(1_000_000).string()
            val releases = json.decodeFromString<List<GhRelease>>(text)
            val next = r.header("Link")?.let { link ->
                NEXT_LINK.find(link)?.groupValues?.get(1)?.toHttpUrlOrNull()
                    ?.takeIf { (!source.requireHttps || it.isHttps) && it.host == url.host }
            }
            return releases to next
        }
    }

    /** Fetch with manual redirects: every hop must be on an allowed host, over https. Returns the open response for a 200. */
    private fun <T> fetch(start: String, use: (okhttp3.Response) -> T): T {
        var url = start.toHttpUrlOrNull() ?: throw IOException("bad url")
        repeat(5) {
            if (!hostAllowed(url)) throw IOException("blocked host")
            val resp = http.newCall(Request.Builder().url(url).build()).execute()
            if (resp.code in setOf(301, 302, 303, 307, 308)) {
                val loc = resp.header("Location"); resp.close()
                url = loc?.let { url.resolve(it) } ?: throw IOException("bad redirect")
            } else {
                resp.use { r ->
                    if (!r.isSuccessful) throw IOException("http ${r.code}")
                    return use(r)
                }
            }
        }
        throw IOException("too many redirects")
    }

    /** Downloads the APK, enforcing size and checksum. Returns the file, or throws IOException (the partial file is deleted). */
    fun download(info: ReleaseInfo, dest: File): File {
        dest.parentFile?.mkdirs()
        dest.delete()
        try {
            val expected = mutableListOf<String>()
            info.apk.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { it.matches(HEX64) }?.let { expected += it }
            info.sha?.let { s ->
                val line = fetch(s.browser_download_url) { it.peekBody(4096).string() }
                HEX64.find(line.lowercase())?.value?.let { expected += it }
            }
            if (expected.isEmpty()) throw IOException("no checksum available")
            val cap = if (info.apk.size > 0) minOf(maxApkBytes, info.apk.size) else maxApkBytes
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            fetch(info.apk.browser_download_url) { r ->
                val body = r.body ?: throw IOException("empty")
                if (body.contentLength() > cap) throw IOException("too large")
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { ins ->
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            total += n
                            if (total > cap) throw IOException("too large")
                            md.update(buf, 0, n); out.write(buf, 0, n)
                        }
                    }
                }
            }
            if (info.apk.size > 0 && total != info.apk.size) throw IOException("size mismatch")
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (expected.any { it != got }) throw IOException("checksum mismatch")
            return dest
        } catch (e: Exception) {
            dest.delete()
            throw if (e is IOException) e else IOException(e.javaClass.simpleName)
        }
    }

    private companion object {
        val HEX64 = Regex("[0-9a-f]{64}")
        val PHONE_TAG = Regex("^phone-build-(\\d{1,9})$")
        val NEXT_LINK = Regex("<([^>]+)>;\\s*rel=\"next\"")
        const val MAX_PAGES = 5
    }
}

const val PHONE_APK_ASSET = "driftfin-phone-release.apk"
const val PHONE_SHA_ASSET = "driftfin-phone-release.apk.sha256"
