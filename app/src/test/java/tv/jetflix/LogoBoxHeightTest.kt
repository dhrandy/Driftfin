package tv.jetflix

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.components.logoBoxHeight

class LogoBoxHeightTest {
    private val base = 96.dp
    private val tall = 168.dp

    @Test fun bannerAspectKeepsOriginalBox() {
        assertEquals(base, logoBoxHeight(5.0f, base, tall))
        assertEquals(base, logoBoxHeight(4.0f, base, tall))
    }

    @Test fun squarerArtGetsTheTallerBox() {
        assertEquals(tall, logoBoxHeight(3.99f, base, tall))
        assertEquals(tall, logoBoxHeight(2.53f, base, tall))
        assertEquals(tall, logoBoxHeight(1.88f, base, tall))
        assertEquals(tall, logoBoxHeight(1.0f, base, tall))
    }

    @Test fun unknownAspectKeepsOriginalBox() {
        assertEquals(base, logoBoxHeight(null, base, tall))
    }

    @Test fun callerWithoutStackedCapKeepsExactBox() {
        // The screensaver passes 48/300 with no stacked cap: its logos must render exactly as before.
        assertEquals(48.dp, logoBoxHeight(2.53f, 48.dp, 48.dp))
        assertEquals(48.dp, logoBoxHeight(1.0f, 48.dp, 48.dp))
        assertEquals(48.dp, logoBoxHeight(null, 48.dp, 48.dp))
    }
}
