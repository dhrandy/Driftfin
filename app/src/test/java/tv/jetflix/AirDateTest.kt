package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.formatAirDate

class AirDateTest {
    @Test fun usesTheCalendarDateWithoutTimeZoneShift() {
        for (tz in listOf("UTC", "America/New_York", "Pacific/Auckland")) {
            val old = java.util.TimeZone.getDefault()
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tz))
            try { assertEquals(tz, "Mar 3, 2021", formatAirDate("2021-03-03T00:00:00.0000000Z")) } finally { java.util.TimeZone.setDefault(old) }
        }
    }
    @Test fun missingOrBadDatesShowNothing() {
        assertNull(formatAirDate(null)); assertNull(formatAirDate("")); assertNull(formatAirDate("not a date"))
        assertNull(formatAirDate("0001-01-01T00:00:00Z")); assertNull(formatAirDate("2021-13-01"))
        assertNull(BaseItemDto(Id = "e", Type = "Episode").airDateLabel())
    }
    @Test fun episodeUsesItsOwnPremiereDate() {
        assertEquals("Mar 10, 2021", BaseItemDto(Id = "e", Type = "Episode", PremiereDate = "2021-03-10T00:00:00.0000000Z").airDateLabel())
    }

    @Test fun seriesWithAPremiereDateDropsTheStandaloneYear() {
        val series = BaseItemDto(Id = "s", Type = "Series", ProductionYear = 2020, PremiereDate = "2020-05-15T00:00:00.0000000Z")
        assertEquals(null, tv.jetflix.app.ui.details.metaYearText(series))
    }

    @Test fun seriesWithoutAPremiereDateKeepsTheYearAndMoviesAlwaysKeepIt() {
        val noDate = BaseItemDto(Id = "s", Type = "Series", ProductionYear = 2020)
        assertEquals("2020", tv.jetflix.app.ui.details.metaYearText(noDate))
        val movie = BaseItemDto(Id = "m", Type = "Movie", ProductionYear = 2020, PremiereDate = "2020-05-15T00:00:00.0000000Z")
        assertEquals("2020", tv.jetflix.app.ui.details.metaYearText(movie))
    }
}
