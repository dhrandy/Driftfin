package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.actor.ActorContent
import tv.jetflix.app.ui.actor.ActorUiState
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.player.PlayerOverlay
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Renders (layoutlib, not a TV) of the actor page, the paused player overlay with the cast strip, and Settings. Art is synthetic gradients, no real people or titles. */
class ActorRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private fun synth(url: String?): BitmapPainter? {
        url ?: return null
        if (url.contains("nophoto")) return null
        val h = url.hashCode()
        val hue = ((h % 360) + 360) % 360
        val (w, ht) = when {
            url.contains("Backdrop") -> 960 to 540
            url.contains("Primary") && url.contains("/p") -> 200 to 200
            else -> 200 to 300
        }
        val bmp = android.graphics.Bitmap.createBitmap(w, ht, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), ht.toFloat(), android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.5f, 0.7f)), android.graphics.Color.HSVToColor(floatArrayOf(((hue + 60) % 360).toFloat(), 0.7f, 0.25f)), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), ht.toFloat(), p)
        if (url.contains("/p")) { val q = android.graphics.Paint().apply { color = android.graphics.Color.argb(160, 255, 255, 255) }; c.drawCircle(w / 2f, ht * 0.4f, ht * 0.18f, q); c.drawCircle(w / 2f, ht * 1.0f, ht * 0.4f, q) }
        return BitmapPainter(bmp.asImageBitmap())
    }

    private fun movies(n: Int, long: Boolean = false) = (1 until n + 1).map {
        BaseItemDto(Id = "m$it", Name = if (long && it % 3 == 0) "A Very Long Movie Title That Wraps Across More Than Two Lines On The Card $it" else "Movie $it", ProductionYear = 2024 - it % 30)
    }
    private val bio = "A short synthetic biography used only for layout testing. ".repeat(3)
    private val longBio = "A long synthetic biography used only for layout testing. ".repeat(40)

    private fun page(state: ActorUiState) = paparazzi.snapshot {
        CompositionLocalProvider(LocalTestArtwork provides { synth(it) }) {
            JetflixTheme { ActorContent(state, imageUrlFor = { "http://demo.test/Items/${it.Id}/Images/Primary" }, onOpenTitle = {}) }
        }
    }

    @Test fun actorFull() = page(ActorUiState(loading = false, name = "Alex Example", bio = bio, photoUrl = "http://demo.test/Items/p1/Images/Primary", titles = movies(18)))
    @Test fun actorLongNameLongBioManyMovies() = page(ActorUiState(loading = false, name = "Alexandria Bartholomew Example-Hargreaves The Third", bio = longBio, photoUrl = "http://demo.test/Items/p2/Images/Primary", titles = movies(60, long = true)))
    @Test fun actorMissingBioMissingPhoto() = page(ActorUiState(loading = false, name = "Sam Sample", bio = null, photoUrl = null, titles = movies(6)))
    @Test fun actorOneMovie() = page(ActorUiState(loading = false, name = "Sam Sample", bio = bio, photoUrl = "http://demo.test/Items/p3/Images/Primary", titles = movies(1)))
    @Test fun actorMixedMoviesAndShows() = page(ActorUiState(loading = false, name = "Alex Example", bio = bio, photoUrl = "http://demo.test/Items/p4/Images/Primary", titles = movies(9) + (1..3).map { BaseItemDto(Id = "s$it", Name = "Show $it", Type = "Series", ProductionYear = 2020 - it) }))
    @Test fun actorShowsOnly() = page(ActorUiState(loading = false, name = "Sam Sample", bio = bio, photoUrl = "http://demo.test/Items/p5/Images/Primary", titles = (1..4).map { BaseItemDto(Id = "s$it", Name = "Show $it", Type = "Series", ProductionYear = 2022 - it) }))
    @Test fun actorEmpty() = page(ActorUiState(loading = false, name = "Sam Sample", bio = bio, photoUrl = null, titles = emptyList()))
    @Test fun actorError() = page(ActorUiState(loading = false, name = "Sam Sample", error = "Couldn't load this actor's titles"))
    @Test fun actorLoading() = page(ActorUiState(loading = true, name = "Sam Sample"))

    private val cast = listOf("Pat Example" to "Lead", "Riley Sample" to "Sidekick", "Jordan Demo" to "A very long character name here", "Casey Test" to null, "Taylor Mock" to "Villain", "Morgan Fake" to "Narrator", "Quinn Placeholder" to "Cop", "Avery Dummy" to "Waiter", "Drew Sample" to "Judge", "Jamie Example" to "Pilot", "Reese Demo" to "Chef", "Skyler Test" to "Guard")
        .mapIndexed { i, (n, r) -> BaseItemPerson(Id = "p$i", Name = n, Role = r, Type = "Actor", PrimaryImageTag = if (i == 3) null else "t") }

    private fun overlay(item: BaseItemDto, playing: Boolean, castVisible: Boolean, selected: Int?) = paparazzi.snapshot {
        CompositionLocalProvider(LocalTestArtwork provides { synth(it) }) {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF334455))) {
                    tv.jetflix.app.ui.components.ArtImage(url = "http://demo.test/Items/frame/Images/Backdrop", contentDescription = null, modifier = Modifier.fillMaxSize())
                    PlayerOverlay(
                        visible = true, item = item, isPlaying = playing,
                        positionTicks = 3_600_000_0000L, bufferedTicks = 4_000_000_0000L, durationTicks = 7_200_000_0000L,
                        castVisible = castVisible, cast = cast.take(12), castSelected = selected,
                        personImageUrl = { if (it.PrimaryImageTag == null) null else "http://demo.test/Items/${it.Id}/Images/Primary" },
                    )
                }
            }
        }
    }

    @Test fun overlayPausedCastIdle() = overlay(BaseItemDto(Id = "x", Name = "Some Movie"), false, true, null)
    @Test fun overlayPausedCastSelected() = overlay(BaseItemDto(Id = "x", Name = "Some Movie"), false, true, 2)
    @Test fun overlayPausedEpisodeLongTitle() = overlay(BaseItemDto(Id = "x", Name = "An Episode Name That Is Fairly Long For A Title Bar", SeriesName = "A Series With A Long Name Indeed", Type = "Episode", ParentIndexNumber = 2, IndexNumber = 5), false, true, 11)
    @Test fun overlayPlayingNoCast() = overlay(BaseItemDto(Id = "x", Name = "Some Movie"), true, false, null)

    @Test fun settingsWithNewRows() = paparazzi.snapshot {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, pauseCast = true, formatBadges = true) }
    }

    private val demoItems: List<BaseItemDto> = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<tv.jetflix.app.data.BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    private fun details(is4k: Boolean, hdr: List<String>, name: String) = paparazzi.snapshot(name = name) {
        val movie = demoItems.first { it.Name == "The General" }
        CompositionLocalProvider(LocalTestArtwork provides { synth(it) }) {
            JetflixTheme {
                tv.jetflix.app.ui.details.DetailsContent(
                    tv.jetflix.app.ui.details.DetailsUiState(loading = false, item = movie.copy(People = cast.take(6)), playTarget = movie, similar = demoItems.take(8), is4k = is4k, hdrFormats = hdr),
                    tv.jetflix.app.data.JellyfinRepository().apply { apiFor("http://demo.test") }, {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
    }

    @Test fun detailsDolbyVisionHdr10Plus4k() = details(true, listOf("Dolby Vision", "HDR10+"), "details_dv_hdr10plus_4k")
    @Test fun detailsHdrOnly1080() = details(false, listOf("HDR10"), "details_hdr10_1080")
    @Test fun detailsNoHdrTags() = details(true, emptyList(), "details_sdr_4k")
}
