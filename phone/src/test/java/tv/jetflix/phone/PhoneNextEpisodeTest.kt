package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.NextEpisode

class PhoneNextEpisodeTest {
    private val eps = listOf("a", "b", "c").map { BaseItemDto(Id = it, Name = "Ep $it", Type = "Episode") }
    @Test fun picksTheEpisodeAfterTheCurrentOne() = assertEquals("b", NextEpisode.pick(eps, "a")?.Id)
    @Test fun lastEpisodeHasNoNext() = assertNull(NextEpisode.pick(eps, "c"))
    @Test fun unknownCurrentHasNoNext() = assertNull(NextEpisode.pick(eps, "zzz"))
}
