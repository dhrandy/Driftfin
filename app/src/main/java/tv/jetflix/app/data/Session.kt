package tv.jetflix.app.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One signed-in Jellyfin session. The access token never leaves [TokenStore] unencrypted. */
data class Session(
    val serverUrl: String,
    val userId: String,
    val userName: String,
    val token: String,
    val deviceId: String,
)

/**
 * Stores the Jellyfin access token encrypted with an AES/GCM key held in the
 * Android Keystore. The plaintext token is never written to disk or logs.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jetflix_session", Context.MODE_PRIVATE)

    /** Returns false when the login could not be encrypted and saved, so the caller can tell the user. */
    fun save(serverUrl: String, userId: String, userName: String, token: String): Boolean {
        val payload = listOf(serverUrl, userId, userName, token).joinToString("\u0001")
        val encrypted = encrypt(payload)
        if (encrypted == null) {
            prefs.edit().remove(KEY_BLOB).apply() // never leave an older login behind that no longer matches
            return false
        }
        prefs.edit().putString(KEY_BLOB, encrypted).putString("last_server", serverUrl).apply()
        return true
    }

    fun load(): Session? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        val plain = decrypt(blob) ?: return null
        val parts = plain.split("\u0001")
        if (parts.size != 4) return null
        return Session(
            serverUrl = parts[0],
            userId = parts[1],
            userName = parts[2],
            token = parts[3],
            deviceId = deviceId(),
        )
    }

    fun clear() = prefs.edit().remove(KEY_BLOB).apply()

    /** Server address only (not secret) so "switch profile" can skip retyping it. */
    fun lastServer(): String = prefs.getString("last_server", "") ?: ""
    fun saveLastServer(url: String) = prefs.edit().putString("last_server", url).apply()

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = "jetflix-" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun key(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                gen.init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                gen.generateKey()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encrypt(plain: String): String? {
        val k = key() ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, k)
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(blob: String): String? {
        val k = key() ?: return null
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jetflix_token_key"
        private const val KEY_BLOB = "session_blob"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

object SessionManager {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    fun signIn(session: Session) { _session.value = session }
    fun signOut() { _session.value = null }

    /**
     * Adds the auth header only when the request goes to the signed-in server (same scheme, host and port).
     * Absolute URLs that come back in server metadata (subtitles, transcode links, artwork) can point
     * anywhere, and the token must not travel to those hosts.
     */
    fun authorize(request: okhttp3.Request): okhttp3.Request {
        val server = _session.value?.serverUrl?.toHttpUrlOrNull() ?: return request
        val url = request.url
        return if (url.scheme == server.scheme && url.host.equals(server.host, ignoreCase = true) && url.port == server.port) {
            request.newBuilder().header("Authorization", authorizationHeader()).build()
        } else {
            request
        }
    }

    /** HTTP client builder for image and media requests: carries the token to the signed-in server only. */
    fun authenticatedClientBuilder(): okhttp3.OkHttpClient.Builder =
        okhttp3.OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(authorize(chain.request())) }

    fun authorizationHeader(token: String? = null, deviceId: String? = null): String {
        val base = "MediaBrowser Client=\"Driftfin\", Device=\"Android TV\", " +
            "DeviceId=\"${deviceId ?: _session.value?.deviceId ?: "jetflix"}\", Version=\"${tv.jetflix.app.BuildConfig.VERSION_NAME}.${tv.jetflix.app.BuildConfig.VERSION_CODE}\""
        val t = token ?: _session.value?.token
        return if (t != null) "$base, Token=\"$t\"" else base
    }
}
