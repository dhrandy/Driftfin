package tv.jetflix

import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.logoRequest

@RunWith(RobolectricTestRunner::class)
class TitleLogoRequestTest {
    private val repo = JellyfinRepository().apply { apiFor("http://demo.test") }

    @Test fun theLogoRequestCarriesAnExplicitSizeSoItStartsWithoutBeingDrawn() {
        // Coil 2.7 suspends a size-less request until the painter is drawn; the logo Image is only
        // composed on Success, so without an explicit size the logo could never load.
        val r = logoRequest(ApplicationProvider.getApplicationContext(), "http://demo.test/Items/1/Images/Logo", 840, 192)
        val resolver = r.defined.sizeResolver
        assertNotNull("a size-less logo request deadlocks behind the success-only Image", resolver)
        assertEquals("Size(width=840, height=192)", kotlinx.coroutines.runBlocking { resolver!!.size() }.toString())
    }

    @Test fun anEpisodeLogoUsesTheSeriesId() {
        val ep = BaseItemDto(Id = "ep1", Type = "Episode", SeriesId = "series9")
        assertEquals("http://demo.test/Items/series9/Images/Logo?maxWidth=600", repo.logoUrl(ep))
    }

    @Test fun aMovieOrSeriesLogoUsesItsOwnId() {
        assertEquals("http://demo.test/Items/m1/Images/Logo?maxWidth=600", repo.logoUrl(BaseItemDto(Id = "m1", Type = "Movie")))
        assertEquals("http://demo.test/Items/s1/Images/Logo?maxWidth=600", repo.logoUrl(BaseItemDto(Id = "s1", Type = "Series")))
    }
}
