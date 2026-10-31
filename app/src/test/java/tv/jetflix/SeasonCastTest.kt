package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.details.seasonCast

/** Season-specific cast: with season 2 selected, the cast row comes from season 2's own episodes,
 *  not the series-wide People list. Series-level People are series-wide credits, not
 *  season-specific evidence, and the play target can be another season's episode, so neither is
 *  substituted: a season whose episodes carry no actor metadata yields an empty list, which hides
 *  the row instead of showing unrelated cast. */
class SeasonCastTest {

    private fun actor(id: String) = BaseItemPerson(Name = "Actor $id", Id = id, Type = "Actor")
    private fun ep(id: String, people: List<BaseItemPerson>) =
        BaseItemDto(Id = id, Type = "Episode", People = people)

    @Test
    fun selectedSeasonsEpisodesProvideTheCast() {
        val season2 = listOf(
            ep("e1", listOf(actor("a"), actor("b"))),
            ep("e2", listOf(actor("b"), actor("c"))), // b repeats across episodes: dedupe
        )
        val cast = seasonCast(season2)
        assertEquals(listOf("a", "b", "c"), cast.map { it.Id })
    }

    @Test
    fun otherSeasonsActorsAreNeverSubstituted() {
        // Season 2 has no actor metadata; season 1 (the play target's season) does.
        // The row must go empty, not borrow season 1's cast.
        val season2 = listOf(ep("e1", emptyList()), ep("e2", emptyList()))
        assertTrue(seasonCast(season2).isEmpty())
    }

    @Test
    fun nonActorPeopleAreIgnored() {
        val crew = BaseItemPerson(Name = "Director D", Id = "d", Type = "Director")
        val cast = seasonCast(listOf(ep("e1", listOf(crew, actor("a")))))
        assertEquals(listOf("a"), cast.map { it.Id })
    }

    @Test
    fun castIsCappedAtFifteen() {
        val season = listOf(ep("e1", (1..20).map { actor("p$it") }))
        assertEquals(15, seasonCast(season).size)
    }
}
