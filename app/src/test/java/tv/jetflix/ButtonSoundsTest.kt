package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.components.ButtonSound
import tv.jetflix.app.ui.components.ButtonSounds

class ButtonSoundsTest {
    @Test fun offIsTheDefaultAndMakesNoSound() {
        assertEquals(ButtonSound.Off, ButtonSounds.style)
        assertEquals(0, ButtonSounds.samples(ButtonSound.Off, false).size)
        assertEquals(ButtonSound.Soft, ButtonSound.Off.next())
        assertEquals(ButtonSound.Off, ButtonSound.Pop.next())
    }

    @Test fun everyStyleIsShortQuietAndStartsAndEndsNearSilence() {
        for (s in ButtonSound.values().filter { it != ButtonSound.Off }) for (select in listOf(false, true)) {
            val d = ButtonSounds.samples(s, select)
            assertTrue("$s/$select is 8 to 80 ms", d.size in (22050 * 8 / 1000)..(22050 * 80 / 1000))
            val peak = d.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$s/$select peak $peak stays under 35% of full scale and is audible", peak in 1500..(32767 * 35 / 100))
            assertTrue("$s/$select fades out", kotlin.math.abs(d.last().toInt()) < peak / 10)
            assertTrue("$s/$select starts softly", kotlin.math.abs(d.first().toInt()) < peak / 10)
        }
    }
}
