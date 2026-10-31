package tv.jetflix.phone

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.seriesStatusLabel
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme

/** Series status ("Continuing"/"Ended"/"Canceled") in the details metadata line, same words as the TV app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSeriesStatusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val itemRequests = java.util.Collections.synchronizedList(mutableListOf<String>())

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.contains("/Images/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "X", "2000")))
                if (path.contains("/Items/s1")) itemRequests += path
                val body = when {
                    path.contains("/Items/s1") -> """{"Id":"s1","Name":"Synthetic Series","Type":"Series","ProductionYear":2024,"Status":"Continuing","Genres":["Drama"],"Overview":"x","ImageTags":{"Primary":"p"},"People":[],"UserData":{}}"""
                    else -> """{"Items":[],"TotalRecordCount":0}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun labelMappingMatchesTvWords() {
        assertEquals("Continuing", BaseItemDto(SeriesStatus = "Continuing").seriesStatusLabel())
        assertEquals("Ended", BaseItemDto(SeriesStatus = "Ended").seriesStatusLabel())
        assertEquals("Canceled", BaseItemDto(SeriesStatus = "Canceled").seriesStatusLabel())
        assertNull(BaseItemDto(SeriesStatus = null).seriesStatusLabel())
        assertNull(BaseItemDto(SeriesStatus = "Pilot").seriesStatusLabel())
    }

    @Test fun seriesPageShowsStatusInMetaLine() {
        rule.setContent { DriftfinTheme { DetailsScreen("s1", onBack = {}, onPlay = {}) } }
        awaitReal(30_000) { rule.onAllNodes(hasText("Continuing", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // The status must arrive over the real series lookup with no fields filter: current Jellyfin
        // sends it as "Status" (DtoService sets dto.Status = series.Status unconditionally); "SeriesStatus"
        // is not an ItemFields value there, so a fields=SeriesStatus request would not help and is never sent.
        assertTrue("series page fetched /Items/s1, got: $itemRequests", itemRequests.any { it.startsWith("/Items/s1") })
        assertTrue("series lookup sent no fields param: $itemRequests", itemRequests.none { it.contains("fields=", ignoreCase = true) })
    }

    /** The wire key a current server actually sends ("Status") must land in the DTO. */
    @Test fun dtoReadsCurrentWireKey() {
        val dto = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(tv.jetflix.phone.data.BaseItemDto.serializer(), """{"Id":"s1","Type":"Series","Status":"Ended"}""")
        assertEquals("Ended", dto.SeriesStatus)
        assertEquals("Ended", dto.seriesStatusLabel())
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
