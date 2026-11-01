package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content

/** The hero pool's animation rule, same as the TV app: only exact Animation tags are dropped,
 *  and only when the setting is on. Lookalikes such as Anime always stay. */
class HeroPoolTest {
    private fun movie(id: String, vararg genres: String) = BaseItemDto(Id = id, Name = id, Type = "Movie", Genres = genres.toList())

    @Test fun offLeavesThePoolUntouched() {
        val pool = listOf(movie("a", "Animation"), movie("b", "Drama"))
        assertEquals(pool, Content.heroPool(pool, excludeAnimation = false))
    }

    @Test fun onDropsAnimationTagsOnly() {
        val pool = listOf(
            movie("a", "Animation"),
            movie("b", " animation "),     // surrounding space and case still count
            movie("c", "ANIMATION"),
            movie("d", "Anime"),           // a different genre, never dropped
            movie("e", "Drama", "Animation"), // any Animation tag excludes the title
            movie("f"),                    // no genres stays
            movie("g", "Drama"),
        )
        assertEquals(listOf("d", "f", "g"), Content.heroPool(pool, excludeAnimation = true).map { it.Id })
    }
}
