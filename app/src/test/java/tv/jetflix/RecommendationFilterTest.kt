package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.RecommendationFilter

class RecommendationFilterTest {
    private fun item(id: String) = BaseItemDto(Id = id)

    @Test fun onlyServerConfirmedUnplayedIdsSurvive() {
        val kept = RecommendationFilter.onlyIn(listOf(item("a"), item("b"), item("c")), setOf("b", "c"))
        assertEquals(listOf("b", "c"), kept.map { it.Id })
    }

    @Test fun emptyConfirmedSetFailsClosed() {
        assertEquals(emptyList<BaseItemDto>(), RecommendationFilter.onlyIn(listOf(item("a")), emptySet()))
    }
}
