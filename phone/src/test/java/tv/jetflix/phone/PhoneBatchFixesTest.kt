package tv.jetflix.phone

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.Watchlist
import tv.jetflix.phone.ui.BrowseScreen
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen
import tv.jetflix.phone.ui.PlayerChrome
import tv.jetflix.phone.ui.PlayerScreen
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** The live-test correction batch: show star/rating/seasons on Details, episode pages carrying their
 *  show, Kids library separation and Categories, Home banner swipe and hero trailer, player chrome. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneBatchFixesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    private var heroCount = 3
    private var playbackInfoDelayMs = 0L

    private val seriesJson = """{"Id":"s1","Name":"Test Show","Type":"Series","CommunityRating":8.27,"ChildCount":2,"ProductionYear":2020,"PremiereDate":"2020-10-28T00:00:00.0000000Z","ImageTags":{"Primary":"p"},"BackdropImageTags":["b"]}"""
    private val episodeJson = """{"Id":"e1","Name":"Pilot","Type":"Episode","SeriesId":"s1","SeriesName":"Test Show","SeasonId":"se1","IndexNumber":1,"ParentIndexNumber":1,"Overview":"The first one.","ProductionYear":2020,"PremiereDate":"2020-11-02T00:00:00.0000000Z","RunTimeTicks":26400000000,"ImageTags":{"Primary":"p"},"BackdropImageTags":["b"]}"""
    private val seasonsJson = """{"Items":[{"Id":"se1","Name":"Season 1","Type":"Season","IndexNumber":1},{"Id":"se2","Name":"Season 2","Type":"Season","IndexNumber":2}]}"""
    private val episodesJson = """{"Items":[{"Id":"e1","Name":"Pilot","Type":"Episode","SeriesId":"s1","SeasonId":"se1","IndexNumber":1,"ParentIndexNumber":1,"PremiereDate":"2020-11-02T00:00:00.0000000Z","RunTimeTicks":26400000000,"ImageTags":{"Primary":"p"}},
        |{"Id":"e2","Name":"Second","Type":"Episode","SeriesId":"s1","SeasonId":"se1","IndexNumber":2,"ParentIndexNumber":1,"RunTimeTicks":31200000000,"ImageTags":{"Primary":"p"}},
        |{"Id":"e3","Name":"Third","Type":"Episode","SeriesId":"s1","SeasonId":"se2","IndexNumber":1,"ParentIndexNumber":2,"ImageTags":{"Primary":"p"}}]}""".trimMargin().replace("\n", "")
    private val libsJson = """{"Items":[{"Id":"libM","Name":"Movies","CollectionType":"movies"},{"Id":"libS","Name":"Shows","CollectionType":"tvshows"},{"Id":"libK","Name":"Kids"}]}"""
    private fun itemJson(id: String, name: String, type: String, genre: String) =
        """{"Id":"$id","Name":"$name","Type":"$type","Genres":["$genre"],"ProductionYear":2020,"ImageTags":{"Primary":"p"}}"""
    private fun heroJson(id: String, name: String, year: Int, trailer: Boolean) =
        """{"Id":"$id","Name":"$name","Type":"Movie","ProductionYear":$year,"CommunityRating":8.0,"ImageTags":{"Primary":"p"},"BackdropImageTags":["b"]""" +
        (if (trailer) ""","RemoteTrailers":[{"Url":"https://youtu.be/abc123def45"}]}""" else "}")

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        heroCount = 3
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += request.method + " " + path
                fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                if (path.contains("/Images/")) {
                    return MockResponse().setHeader("Content-Type", "image/png")
                        .setBody(okio.Buffer().write(TestArt.poster(0, "Title", "2020")))
                }
                if (request.method == "POST" && path.contains("/PlaybackInfo")) {
                    if (playbackInfoDelayMs > 0) Thread.sleep(playbackInfoDelayMs)
                    return MockResponse().setResponseCode(500)
                }
                val url = request.requestUrl!!
                val parent = url.queryParameter("parentId")
                val types = url.queryParameter("includeItemTypes")
                val genre = url.queryParameter("genres")
                val sortBy = url.queryParameter("sortBy")
                return when {
                    path.startsWith("/Items/Filters") -> json("""{"Genres":["Drama","Kids"]}""")
                    path.startsWith("/UserViews") -> json(libsJson)
                    path.startsWith("/UserItems/Resume") || path.startsWith("/Shows/NextUp") -> json("""{"Items":[]}""")
                    path.startsWith("/Shows/s1/Episodes") -> json(episodesJson)
                    path.startsWith("/Items/Latest") -> json("[]")
                    path.startsWith("/Items/Suggestions") -> json("""{"Items":[]}""")
                    path.contains("/LocalTrailers") -> json("[]")
                    path.startsWith("/Items/s1") -> json(seriesJson)
                    path.startsWith("/Items/e1") -> json(episodeJson)
                    path.startsWith("/MediaSegments") -> json("""{"Items":[]}""")
                    path.startsWith("/Items") -> when {
                        types == "Season" -> json(seasonsJson)
                        sortBy == "CommunityRating" && types == "Movie" -> json(
                            "{\"Items\":[" + listOf("Hero Alpha", "Hero Beta", "Hero Gamma").take(heroCount)
                                .mapIndexed { i, n -> heroJson("h$i", n, 2021 + i, i == 0) }.joinToString(",") + "]}")
                        parent == "libK" && genre == "Drama" -> json("""{"Items":[]}""")
                        genre == "Drama" && types == "Movie" -> json("{\"Items\":[${itemJson("m1", "Grownup Movie", "Movie", "Drama")}]}")
                        genre == "Drama" && types == "Series" -> json("{\"Items\":[${itemJson("t1", "Grownup Show", "Series", "Drama")}]}")
                        parent == "libK" && types == "Movie" -> json("{\"Items\":[${itemJson("k1", "Kids Film", "Movie", "Kids")}]}")
                        parent == "libK" && types == "Series" -> json("{\"Items\":[${itemJson("k2", "Kids Toon", "Series", "Kids")}]}")
                        types == "Movie" -> json("{\"Items\":[${itemJson("m1", "Grownup Movie", "Movie", "Drama")}]}")
                        types == "Series" -> json("{\"Items\":[${itemJson("t1", "Grownup Show", "Series", "Drama")}]}")
                        else -> json("""{"Items":[]}""")
                    }
                    else -> json("{}")
                }
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @After fun teardown() { server.shutdown() }

    /** Waits in REAL time (the compose virtual clock can starve network chains). */
    private fun awaitReal(timeoutMs: Long, debug: () -> String = { requested.takeLast(10).joinToString("\n") }, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        val dump = "DEBUG: " + debug() + "\nREQUESTED:\n" + requested.joinToString("\n")
        java.io.File("build/render-check/debug-awaitReal-" + System.currentTimeMillis() + ".txt").writeText(dump)
        assertTrue("condition not met within ${timeoutMs}ms: " + debug().take(600), condition())
    }

    private fun settle(ms: Long = 4000) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(60) }
    }

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("$name is not a blank frame (${colors.size} colors)", colors.size > 30)
    }

    @Test fun seriesDetailsShowStarRatingAndSeasons() {
        rule.setContent { DriftfinTheme { DetailsScreen("s1", onBack = {}, onPlay = {}) } }
        // The title is text until the (fixture) logo loads, then an image with the title as contentDescription - accept either.
        awaitReal(30_000) { rule.onAllNodes(hasText("Test Show") or hasContentDescription("Test Show")).fetchSemanticsNodes().isNotEmpty() }
        settle(1200)
        rule.onNodeWithText("2 Seasons", substring = true).assertIsDisplayed()
        rule.onNodeWithText("8.3").assertIsDisplayed()
        // Air date: the series keeps its year and adds the full premiere calendar date (no timezone shift).
        rule.onNodeWithText("Premiered Oct 28, 2020", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Season 1").assertIsDisplayed()
        rule.onNodeWithText("Season 2").assertIsDisplayed()
        // The first season is selected: its episodes show, season two's don't.
        rule.onNodeWithText("Pilot", substring = true).assertIsDisplayed()
        // Episode rows carry the air date by the runtime; an episode without a date still shows its runtime alone.
        rule.onNodeWithText("Nov 2, 2020", substring = true).assertIsDisplayed()
        rule.onNodeWithText("52m").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Third", substring = true).fetchSemanticsNodes().isEmpty())
        // Switching to season two swaps the list.
        rule.onNodeWithText("Season 2").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Third", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("Pilot", substring = true).fetchSemanticsNodes().isEmpty())
        // The star adds the show to the watchlist.
        rule.onNodeWithContentDescription("Add to watchlist").performClick()
        rule.waitUntil(5_000) { Watchlist.ids.value.contains("s1") }
        settle(1500)
        capture("details-series.png")
    }

    @Test fun episodeDetailsCarryTheShow() {
        var opened: String? = null
        rule.setContent { DriftfinTheme { DetailsScreen("e1", onBack = {}, onPlay = {}, onOpenItem = { opened = it }) } }
        awaitReal(30_000) { rule.onAllNodesWithText("Open the show page").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Show").assertIsDisplayed()
        // The episode page carries its air date by the runtime (header meta and the episode row both show it).
        assertTrue(rule.onAllNodesWithText("Nov 2, 2020", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("44m", substring = true).fetchSemanticsNodes().isNotEmpty())
        // The star on an episode page stars the show, not the episode.
        rule.onNodeWithContentDescription("Add to watchlist").performClick()
        rule.waitUntil(5_000) { Watchlist.ids.value.contains("s1") }
        assertTrue(!Watchlist.ids.value.contains("e1"))
        // This episode is marked in the season list, and the show row opens the series page.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("this episode", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Open the show page").performClick()
        rule.waitUntil(5_000) { opened == "s1" }
        capture("details-episode.png")
    }

    @Test fun genresAreMultiSelect() {
        Graph.appContext().getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        val instance = androidx.compose.runtime.mutableIntStateOf(1)
        rule.setContent { DriftfinTheme { androidx.compose.runtime.key(instance.intValue) { BrowseScreen("Movie", "Movies", onOpen = {}) } } }
        awaitReal(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        // Genres are the category values and multi-select: picks get checkmarks; the button shows the picks.
        rule.onNodeWithText("Genres").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("All genres", substring = true).fetchSemanticsNodes().isNotEmpty() }
        var before = requested.size
        rule.onNodeWithText("Drama").performClick()
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && it.contains("genres=Drama") } }
        rule.onNodeWithText("Drama").performClick() // close (button now shows the pick)
        rule.onNodeWithText("Drama").performClick() // reopen
        awaitReal(30_000) { rule.onAllNodesWithText("\u2713 Drama").fetchSemanticsNodes().isNotEmpty() }
        before = requested.size
        rule.onNodeWithText("Kids").performClick()
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && (it.contains("genres=Drama%2CKids") || it.contains("genres=Drama,Kids")) } }
        rule.onNodeWithText("Drama, Kids").performClick() // close
        rule.onNodeWithText("Drama, Kids").performClick() // reopen
        awaitReal(30_000) { rule.onAllNodesWithText("\u2713 Kids").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("\u2713 Drama").assertIsDisplayed()
        settle(1200)
        capture("browse-genres-menu.png") // open menu with both picks checkmarked
        // The menu is a popup (not in the activity decorView): draw the last-added window view directly.
        val wmgClass = Class.forName("android.view.WindowManagerGlobal")
        val wmg = wmgClass.getMethod("getInstance").invoke(null)
        val viewsField = wmgClass.getDeclaredField("mViews"); viewsField.isAccessible = true
        @Suppress("UNCHECKED_CAST") val windowViews = viewsField.get(wmg) as ArrayList<android.view.View>
        val popupView = windowViews.last()
        popupView.measure(android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.AT_MOST), android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.AT_MOST))
        popupView.layout(0, 0, popupView.measuredWidth, popupView.measuredHeight)
        val menuBmp = Bitmap.createBitmap(popupView.measuredWidth, popupView.measuredHeight, Bitmap.Config.ARGB_8888)
        popupView.draw(android.graphics.Canvas(menuBmp))
        File("build/render-check/browse-genres-menu-checks.png").outputStream().use { menuBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // All genres clears the picks and closes the menu.
        before = requested.size
        rule.onNodeWithText("All genres").performClick()
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && !it.contains("genres=") } }
        rule.onNodeWithText("Genres").assertIsDisplayed()
        // The picks persist per user and kind: re-pick Drama and a fresh instance restores it.
        rule.onNodeWithText("Genres").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Drama").fetchSemanticsNodes().isNotEmpty() }
        before = requested.size
        rule.onNodeWithText("Drama").performClick()
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && it.contains("genres=Drama") } }
        before = requested.size
        instance.intValue = 2
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && it.contains("genres=Drama") } }
        rule.onNodeWithText("Drama", substring = false).assertExists()
        settle(1200)
        capture("browse-genres-multi.png")
    }

    private val cover = hasClickAction() and !androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button)

    @Test fun kidsLibrarySeparatesMoviesAndCategoriesFollow() {
        Graph.appContext().getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        val instance = androidx.compose.runtime.mutableIntStateOf(1)
        rule.setContent { DriftfinTheme { androidx.compose.runtime.key(instance.intValue) { BrowseScreen("Movie", "Movies", onOpen = {}) } } }
        awaitReal(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        awaitReal(30_000) { rule.onAllNodesWithText("Library: ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Pick the Kids library: the request carries the library and stays Movie-typed.
        rule.onNodeWithText("Library: ", substring = true).performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Kids").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Kids").performClick()
        awaitReal(30_000, { requested.joinToString("\n") }) { requested.any { it.startsWith("GET /Items?") && it.contains("parentId=libK") && it.contains("includeItemTypes=Movie") } }
        // Exactly one cover remains: the kids film, not the grownup movie.
        awaitReal(30_000, { "covers=" + rule.onAllNodes(cover).fetchSemanticsNodes().size }) { rule.onAllNodes(cover).fetchSemanticsNodes().size == 1 }
        // Categories refetch is scoped to the picked library.
        awaitReal(30_000, { requested.filter { it.contains("Filters") }.joinToString("\n") }) { requested.any { it.startsWith("GET /Items/Filters") && it.contains("parentId=libK") } }
        // Genres send the genre and the library together; no kids movie is Drama, so the grid empties.
        rule.onNodeWithText("Genres").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Drama").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Drama").performClick()
        rule.onNodeWithText("Drama").performClick() // the pick leaves the menu open (multi-select); the button, now "Drama", closes it
        awaitReal(30_000, { requested.filter { it.startsWith("GET /Items?") }.joinToString("\n") }) { requested.any { it.startsWith("GET /Items?") && it.contains("genres=Drama") && it.contains("parentId=libK") && it.contains("includeItemTypes=Movie") } }
        awaitReal(30_000) { rule.onAllNodesWithText("Nothing here.").fetchSemanticsNodes().isNotEmpty() }
        settle(1500)
        capture("browse-kids-categories.png")
        // The pick persists for this user and kind: a fresh Movies screen instance starts in Kids.
        val before = requested.size
        instance.intValue = 2
        awaitReal(30_000, { requested.drop(before).joinToString("\n") }) { requested.drop(before).any { it.startsWith("GET /Items?") && it.contains("parentId=libK") && it.contains("includeItemTypes=Movie") } }
        rule.onNodeWithText("Library: Kids").assertIsDisplayed()
    }

    @Test fun kidsLibrarySeparatesShows() {
        Graph.appContext().getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        rule.setContent { DriftfinTheme { BrowseScreen("Series", "Shows", onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        awaitReal(30_000) { rule.onAllNodesWithText("Library: All").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Library: All").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Kids").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Kids").performClick()
        awaitReal(30_000) { requested.any { it.startsWith("GET /Items?") && it.contains("parentId=libK") && it.contains("includeItemTypes=Series") } }
        awaitReal(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().size == 1 }
    }

    @Test fun heroSwipesAndTrailerFollowsHero() {
        var opened: tv.jetflix.phone.data.BaseItemDto? = null
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = { opened = it }) } }
        // The hero title can render as logo art, so the hero is identified by its year in the meta row.
        val years = listOf("2021", "2022", "2023")
        fun currentHero() = years.indexOfFirst { rule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty() }
        awaitReal(30_000) { currentHero() >= 0 }
        settle(1000)
        capture("home-hero.png")
        val first = currentHero()
        // Trailer clapperboard only for a hero that has one (only the first fixture does).
        try {
            awaitReal(8_000, { "first=$first trailerNodes=" + rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Watch trailer")).fetchSemanticsNodes().size }) {
                (first == 0) == rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Watch trailer")).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val texts = rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") }
            val cds = rule.onAllNodes(androidx.compose.ui.test.hasAnyDescendant(hasText("", substring = true)), useUnmergedTree = true).fetchSemanticsNodes().size
            java.io.File("build/render-check/debug-hero-tree.txt").writeText("TEXTS:\n" + texts.joinToString("\n") + "\nREQUESTED:\n" + requested.joinToString("\n"))
            throw e
        }
        // Swipe left browses to the next hero; swipe right comes back.
        run {
            val b = rule.onNodeWithText(years[first], substring = true).fetchSemanticsNode().boundsInRoot
            val rw = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
            rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(rw * 0.75f, b.center.y), androidx.compose.ui.geometry.Offset(rw * 0.25f, b.center.y), 300) }
        }
        awaitReal(30_000, { "hero=$first now=${currentHero()}" }) { currentHero() >= 0 && currentHero() != first }
        val second = currentHero()
        assertNotEquals(first, second)
        run {
            val b = rule.onNodeWithText(years[second], substring = true).fetchSemanticsNode().boundsInRoot
            val rw = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
            rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(rw * 0.25f, b.center.y), androidx.compose.ui.geometry.Offset(rw * 0.75f, b.center.y), 300) }
        }
        awaitReal(30_000) { currentHero() == first }
        // A tap still opens the title.
        rule.onNodeWithText("More info").performClick()
        awaitReal(30_000) { opened != null }
        assertEquals("h$first", opened?.Id)
        // Cycle to the trailer-carrying hero: the clapperboard must be visibly there.
        var guard = 0
        while (currentHero() != 0 && guard < 3) {
            val yb = rule.onNodeWithText(years[currentHero()], substring = true).fetchSemanticsNode().boundsInRoot
            val rw2 = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
            rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(rw2 * 0.75f, yb.center.y), androidx.compose.ui.geometry.Offset(rw2 * 0.25f, yb.center.y), 300) }
            awaitReal(30_000) { currentHero() >= 0 }
            guard++
        }
        assertEquals(0, currentHero())
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Watch trailer")).assertIsDisplayed()
        settle(1000)
        capture("home-hero-trailer.png")
    }

    @Test fun singleHeroIgnoresSwipe() {
        heroCount = 1
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodesWithText("2021", substring = true).fetchSemanticsNodes().isNotEmpty() }
        run {
            val b = rule.onNodeWithText("2021", substring = true).fetchSemanticsNode().boundsInRoot
            val rw = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
            rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(rw * 0.75f, b.center.y), androidx.compose.ui.geometry.Offset(rw * 0.25f, b.center.y), 300) }
        }
        settle(800)
        rule.onNodeWithText("2021", substring = true).assertIsDisplayed()
    }

    @Test fun heroCandidatesCarryTrailers() {
        // The hero trailer depends on RemoteTrailers surviving the fetch fields and picking a playable source.
        val items = kotlinx.coroutines.runBlocking { tv.jetflix.phone.data.Content.heroCandidates() }
        org.junit.Assert.assertEquals(3, items.size)
        org.junit.Assert.assertTrue("parsed RemoteTrailers: " + items.map { it.RemoteTrailers.size }, items[0].RemoteTrailers.isNotEmpty())
        org.junit.Assert.assertTrue(tv.jetflix.phone.data.Trailers.pick(items[0], emptyList()) is tv.jetflix.phone.data.TrailerSource.YouTube)
    }

    @Test fun playerChromeFollowsVisibility() {
        var back = 0; var audio = 0; var zoom = 0
        val visible = androidx.compose.runtime.mutableStateOf(true)
        val fillState = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            DriftfinTheme {
                PlayerChrome(visible = visible.value, fill = fillState.value, onBack = { back++ }, onAudioSubtitles = { audio++ }, onToggleZoom = { zoom++; fillState.value = !fillState.value })
            }
        }
        rule.onNodeWithText("Audio · Subtitles").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Fill").performClick()
        rule.waitForIdle()
        assertEquals(1, back)
        assertEquals(1, audio)
        assertEquals(1, zoom)
        rule.onNodeWithText("Fit").assertIsDisplayed()
        visible.value = false
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Audio · Subtitles").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Fit").fetchSemanticsNodes().isEmpty())
    }

    @Test fun playerBackDuringLoadingExitsCleanly() {
        playbackInfoDelayMs = 4000
        var exits = 0
        rule.setContent { DriftfinTheme { PlayerScreen("e1", onExit = { exits++ }) } }
        // The player is still waiting on the server: chrome is up (not playing) and Back leaves at once.
        awaitReal(20_000) { rule.onAllNodesWithText("Audio · Subtitles").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Back").performClick()
        awaitReal(30_000) { exits == 1 }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
        settle(500)
        assertEquals(1, exits)
    }

    @Test fun playerBackRestoresPortraitAndExitsOnce() {
        var exits = 0
        rule.setContent { DriftfinTheme { PlayerScreen("e1", onExit = { exits++ }) } }
        // Playback is refused (500): the chrome still shows while paused so Back is reachable.
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Audio · Subtitles").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10_000) { exits == 1 }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
        settle(500)
        assertEquals(1, exits)
    }
}
