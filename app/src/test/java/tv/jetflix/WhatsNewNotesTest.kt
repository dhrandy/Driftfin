package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.update.WhatsNew

/** The in-app What's New card is fed from the release's RELEASE_NOTES.md bullets (build-22 complaint: stale notes). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsNewNotesTest {
    @Test fun parseKeepsBulletsDropsHeadingsAndBlanks() {
        // The CI embed step strips the "- " prefixes; the asset holds one change per line.
        val lines = listOf("", "Fixes:", "First real change", "", "Second real change")
        assertEquals(listOf("First real change", "Second real change"), WhatsNew.parse(lines))
    }

    @Test fun parseSurvivesIndentation() {
        assertEquals(listOf("A change"), WhatsNew.parse(listOf("  A change  ")))
    }
}
