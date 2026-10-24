package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.NextEpisode

class NextEpisodeTest {
    private fun ep(id: String) = BaseItemDto(Id = id, Type = "Episode")
    @Test fun picksFollowing() {
        val l = listOf(ep("a"), ep("b"), ep("c"))
        assertEquals("b", NextEpisode.pick(l, "a")?.Id)
        assertEquals("c", NextEpisode.pick(l, "b")?.Id)
    }
    @Test fun lastOrMissingHasNoNext() {
        val l = listOf(ep("a"), ep("b"))
        assertNull(NextEpisode.pick(l, "b"))
        assertNull(NextEpisode.pick(l, "zzz"))
    }
}
