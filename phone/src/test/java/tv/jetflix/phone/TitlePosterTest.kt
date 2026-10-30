package tv.jetflix.phone

import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.JellyfinRepository

class TitlePosterTest {
    private val repo = JellyfinRepository().also { it.apiFor("http://example.invalid") }

    @Test fun episodeUsesSeriesPosterNotItsStill() {
        val ep = BaseItemDto(Id = "e1", Name = "Ep", Type = "Episode", SeriesId = "s1", SeriesPrimaryImageTag = "st", ImageTags = mapOf("Primary" to "still"))
        val url = repo.titlePosterUrl(ep)!!
        assertTrue(url, url.contains("/Items/s1/Images/Primary") && url.contains("tag=st"))
    }

    @Test fun moviesKeepTheirOwnPoster() {
        val m = BaseItemDto(Id = "m1", Name = "Movie", Type = "Movie", ImageTags = mapOf("Primary" to "pt"))
        assertTrue(repo.titlePosterUrl(m)!!.contains("/Items/m1/Images/Primary"))
    }
}
