package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.ServerAddress

class ServerAddressTest {
    @Test fun httpAndSchemelessWarn() {
        assertTrue(ServerAddress.isCleartext("http://example.test:8096"))
        assertTrue(ServerAddress.isCleartext("192.168.1.10:8096"))
        assertTrue(ServerAddress.isCleartext("media.local"))
        assertTrue(ServerAddress.isCleartext("HTTP://example.test"))
    }

    @Test fun httpsAndEmptyDoNot() {
        assertFalse(ServerAddress.isCleartext("https://example.test"))
        assertFalse(ServerAddress.isCleartext("  HTTPS://example.test  "))
        assertFalse(ServerAddress.isCleartext(""))
    }
}
