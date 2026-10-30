package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.details.genreLine

/** The series genre line leads with the show's premiere date once the episode's air date takes the
 * premiere date's slot in the meta row (the user's swap after the genre-line pick). */
class GenreLineTest {
    private val series = BaseItemDto(Id = "s", Type = "Series", Name = "Show", Genres = listOf("Drama", "Comedy"),
        PremiereDate = "2020-05-15T00:00:00.0000000Z")
    private val ep = BaseItemDto(Id = "e3", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 3, PremiereDate = "2023-04-21T00:00:00.0000000Z")

    @Test fun seriesWithDatedEpisodeLeadsWithPremiereDate() =
        assertEquals("Premiered May 15, 2020  ·  Drama · Comedy", genreLine(series, ep))

    @Test fun movieKeepsPlainGenres() =
        assertEquals("Drama · Comedy", genreLine(series.copy(Type = "Movie"), ep.copy(Type = "Movie")))

    @Test fun episodeWithoutADateKeepsPlainGenres() =
        assertEquals("Drama · Comedy", genreLine(series, ep.copy(PremiereDate = null)))

    @Test fun missingTargetKeepsPlainGenres() =
        assertEquals("Drama · Comedy", genreLine(series, null))
}
