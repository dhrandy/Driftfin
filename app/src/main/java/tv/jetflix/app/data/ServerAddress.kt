package tv.jetflix.app.data

/** What the sign-in screen tells the user about the address they typed. */
object ServerAddress {
    const val HTTP_WARNING =
        "This address uses http://, so your username, password and viewing activity travel unencrypted on the network. " +
            "Use an https:// address if your server has one."

    /** True when connecting would use plain http. An address typed without a scheme is connected over http. */
    fun isCleartext(input: String): Boolean {
        val t = input.trim()
        if (t.isEmpty()) return false
        return !t.startsWith("https://", ignoreCase = true)
    }
}
