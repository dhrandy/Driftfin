package tv.jetflix
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.*
@RunWith(RobolectricTestRunner::class)
class AnimationOptionTest {
 @Test fun filtersOnlyWhenEnabledAndNeverFallsBackToAnimation() {
  val cartoon=BaseItemDto(Id="a",Type="Movie",Genres=listOf(" animation "),CommunityRating=9f)
  val movie=BaseItemDto(Id="m",Type="Movie",Genres=listOf("Drama"),CommunityRating=8f)
  assertEquals(2,BannerPicks.randomTopUnwatchedMovies(listOf(cartoon,movie),{true},6).size)
  assertEquals(listOf("m"),BannerPicks.randomTopUnwatchedMovies(listOf(cartoon,movie),{true},6,excludeAnimation=true).map{it.Id})
  assertTrue(BannerPicks.randomTopUnwatchedMovies(listOf(cartoon),{true},6,excludeAnimation=true).isEmpty())
 }
 @Test fun preferenceSurvivesReinit() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  AppSettings.init(ctx); AppSettings.setExcludeHomeAnimation(true); AppSettings.init(ctx)
  assertTrue(AppSettings.excludeHomeAnimation.value)
  AppSettings.setExcludeHomeAnimation(false); AppSettings.init(ctx)
  assertFalse(AppSettings.excludeHomeAnimation.value)
 }
}
