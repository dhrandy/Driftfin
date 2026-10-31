package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.phone.data.lifeDatesLabel

/** The phone actor page's "Born ... · Died ..." line: the server's own dates, never invented ones. */
class PhoneLifeDatesTest {
    @Test fun livingPersonShowsBirthOnly() {
        assertEquals("Born Mar 14, 1933", lifeDatesLabel("1933-03-14T00:00:00.0000000Z", null))
    }

    @Test fun deceasedPersonShowsBothDates() {
        assertEquals("Born Jun 1, 1926 · Died Aug 4, 1962", lifeDatesLabel("1926-06-01T00:00:00.0000000Z", "1962-08-04T00:00:00.0000000Z"))
    }

    @Test fun deathWithoutBirthShowsDeathOnly() {
        assertEquals("Died Aug 4, 1962", lifeDatesLabel(null, "1962-08-04"))
    }

    @Test fun missingOrBadDatesShowNoLine() {
        assertNull(lifeDatesLabel(null, null))
        assertNull(lifeDatesLabel("", "not a date"))
        assertNull(lifeDatesLabel("0001-01-01T00:00:00Z", null))
    }
}
