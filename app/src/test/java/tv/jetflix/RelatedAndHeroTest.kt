package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.RelatedItems
import tv.jetflix.app.ui.home.HeroRotation

class RelatedAndHeroTest {
    private fun m(id: String, genres: List<String>, type: String = "Movie") = BaseItemDto(Id = id, Name = id, Type = type, Genres = genres)

    @Test fun dropsSimilarItemsThatShareNoGenreWithTheSource() {
        val src = m("doc", listOf("Documentary", "Comedy"))
        val cands = listOf(m("a", listOf("Comedy")), m("b", listOf("Animation", "Family")), m("c", listOf("Documentary")), m("d", listOf("comedy")), m("e", listOf("Comedy")))
        assertEquals(listOf("a", "c", "d", "e"), RelatedItems.filter(src, cands).map { it.Id })
    }

    @Test fun hidesTheRowWhenTooFewRelatedItemsRemain() {
        val src = m("doc", listOf("Documentary"))
        assertTrue(RelatedItems.filter(src, listOf(m("a", listOf("Documentary")), m("b", listOf("Animation")))).isEmpty())
    }

    @Test fun hidesTheRowWhenTheSourceHasNoGenres() {
        assertTrue(RelatedItems.filter(m("x", emptyList()), List(8) { m("c$it", listOf("Comedy")) }).isEmpty())
    }

    @Test fun ignoresDifferentKindsAndTheSourceItself() {
        val src = m("s", listOf("Drama"))
        val cands = listOf(m("s", listOf("Drama")), m("t1", listOf("Drama"), "Series")) + List(3) { m("ok$it", listOf("Drama")) }
        assertTrue(RelatedItems.filter(src, cands).isEmpty())
    }

    @Test fun bannerCyclesAndWraps() {
        assertEquals(1, HeroRotation.next(0, 3)); assertEquals(0, HeroRotation.next(2, 3))
        assertEquals(0, HeroRotation.next(0, 1)); assertEquals(0, HeroRotation.next(0, 0))
    }
}
