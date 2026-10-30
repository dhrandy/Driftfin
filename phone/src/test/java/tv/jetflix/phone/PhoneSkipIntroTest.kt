package tv.jetflix.phone

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.phone.data.MediaSegmentDto
import tv.jetflix.phone.data.MediaSegmentsResponse
import tv.jetflix.phone.data.SkipIntro

/** Synthetic segments only: nothing here comes from a real server. */
class PhoneSkipIntroTest {
    private fun seg(type: String, startSec: Long, endSec: Long) =
        MediaSegmentDto(Id = "s", ItemId = "i", Type = type, StartTicks = startSec * 10_000_000, EndTicks = endSec * 10_000_000)

    private val intro = listOf(seg("Intro", 30, 120), seg("Outro", 1300, 1400))

    @Test fun showsOnlyInsideAnIntro() {
        assertNull("before", SkipIntro.active(intro, 29_999))
        assertNotNull("start", SkipIntro.active(intro, 30_000))
        assertNotNull("middle", SkipIntro.active(intro, 90_000))
        assertNull("after", SkipIntro.active(intro, 121_000))
    }

    @Test fun hidesWhenAlmostNothingIsLeft() {
        assertNotNull(SkipIntro.active(intro, 118_900))
        assertNull(SkipIntro.active(intro, 119_100))
    }

    @Test fun ignoresOtherTypesAndBadSpans() {
        assertNull("outro is not an intro", SkipIntro.active(intro, 1_350_000))
        assertNull("empty span", SkipIntro.active(listOf(seg("Intro", 50, 50)), 50_000))
        assertNull("no segments", SkipIntro.active(emptyList(), 60_000))
    }

    @Test fun skipTargetIsTheServersEndTime() {
        assertEquals(120_000L, SkipIntro.endMs(intro[0]))
    }

    @Test fun parsesJellyfinMediaSegmentsJson() {
        val json = """{"Items":[{"Id":"a1","ItemId":"i1","Type":"Intro","StartTicks":300000000,"EndTicks":1200000000,"Extra":1}],"TotalRecordCount":1,"StartIndex":0}"""
        val r = Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<MediaSegmentsResponse>(json)
        assertEquals("Intro", r.Items.single().Type)
        assertEquals(120_000L, SkipIntro.endMs(r.Items.single()))
    }
}
