package tv.jetflix.app.data

/**
 * Per-profile settings storage. Each profile on this TV gets its own copy of the Settings
 * toggles: the stored key carries a short hash of server address + user id, so two profiles
 * never see each other's choices.
 *
 * Migration: choices saved before per-profile settings existed live under the original plain
 * keys. Those keys are never moved or deleted; a profile that has not changed a setting yet
 * reads the plain key, so everyone's current setup carries over exactly, and each profile
 * starts diverging only when they change something themselves.
 */
internal object ProfileScope {

    /** Storage key for one profile's copy of a setting: base key plus a short stable hash. */
    fun key(base: String, serverUrl: String, userId: String): String {
        val h = java.security.MessageDigest.getInstance("SHA-256").digest("$serverUrl$userId".toByteArray())
        return base + "_" + h.joinToString("") { "%02x".format(it) }.take(12)
    }

    /**
     * Which key to read: the profile's own key when it holds a value, else the legacy shared
     * key. When there is no active profile (signed out), scoped == base and the shared key is
     * read as before.
     */
    fun readKey(base: String, scoped: String, has: (String) -> Boolean): String =
        if (scoped != base && has(scoped)) scoped else base
}
