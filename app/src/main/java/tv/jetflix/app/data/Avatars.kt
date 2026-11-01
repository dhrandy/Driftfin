package tv.jetflix.app.data

/** How the signed-in profile shows in the top bar. UsernameOnly is the original look and the default. */
enum class AvatarDisplay(val label: String) {
    UsernameOnly("Username only"),
    AvatarAndUsername("Avatar + username"),
    AvatarOnly("Avatar only");
    fun next() = values()[(ordinal + 1) % values().size]
}

/** The 20 built-in profile avatars (the approved v5 set), bundled at 256px. The choice is stored
 *  locally on this TV; nothing is sent to the server. */
object Avatars {
    data class Entry(val id: Int, val label: String, val res: Int)
    val all: List<Entry> = listOf(
        Entry(1, "Astronaut", tv.jetflix.app.R.drawable.avatar_01),
        Entry(2, "Robot", tv.jetflix.app.R.drawable.avatar_02),
        Entry(3, "Camp scene", tv.jetflix.app.R.drawable.avatar_03),
        Entry(4, "Fuzzy monster", tv.jetflix.app.R.drawable.avatar_04),
        Entry(5, "Goofy dog", tv.jetflix.app.R.drawable.avatar_05),
        Entry(6, "Three-eyed alien", tv.jetflix.app.R.drawable.avatar_06),
        Entry(7, "Cool shark", tv.jetflix.app.R.drawable.avatar_07),
        Entry(8, "T-Rex", tv.jetflix.app.R.drawable.avatar_08),
        Entry(9, "Astronaut cat", tv.jetflix.app.R.drawable.avatar_09),
        Entry(10, "Sly fox", tv.jetflix.app.R.drawable.avatar_10),
        Entry(11, "Panda", tv.jetflix.app.R.drawable.avatar_11),
        Entry(12, "Hoodie penguin", tv.jetflix.app.R.drawable.avatar_12),
        Entry(13, "Octopus", tv.jetflix.app.R.drawable.avatar_13),
        Entry(14, "Frog", tv.jetflix.app.R.drawable.avatar_14),
        Entry(15, "Kid wizard", tv.jetflix.app.R.drawable.avatar_15),
        Entry(16, "Pirate girl", tv.jetflix.app.R.drawable.avatar_16),
        Entry(17, "Owl with glasses", tv.jetflix.app.R.drawable.avatar_17),
        Entry(18, "Baby dragon", tv.jetflix.app.R.drawable.avatar_18),
        Entry(19, "Backwards-cap kid", tv.jetflix.app.R.drawable.avatar_19),
        Entry(20, "Sloth", tv.jetflix.app.R.drawable.avatar_20),
    )
    fun byId(id: Int): Entry? = all.firstOrNull { it.id == id }
}
