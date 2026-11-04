package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.phone.data.AutoQuality

/** The Auto quality ladder: buffering-driven step-down shared by every player. */
class PhoneAutoQualityTest {

    @Test fun stepsDownAfterTwoUnderrunsInsideTheWindow() {
        val q = AutoQuality()
        q.start()
        assertNull(q.currentCap)
        assertNull(q.onUnderrun(1_000))
        assertEquals(20_000_000, q.onUnderrun(10_000))
        assertEquals(20_000_000, q.currentCap)
    }

    @Test fun isolatedStallsOutsideTheWindowDoNotStepDown() {
        val q = AutoQuality()
        q.start()
        assertNull(q.onUnderrun(0))
        assertNull("a stall 31s after the first is a new window, not a pattern", q.onUnderrun(31_000))
        assertEquals(20_000_000, q.onUnderrun(32_000))
    }

    @Test fun walksTheWholeLadderThenStaysAtTheFloor() {
        val q = AutoQuality()
        q.start()
        val expected = listOf(20_000_000, 10_000_000, 6_000_000, 4_000_000, 2_000_000, 1_000_000)
        var t = 0L
        for (cap in expected) {
            assertNull(q.onUnderrun(t))
            assertEquals(cap, q.onUnderrun(t + 1_000))
            t += 2_000
        }
        assertNull("the ladder is exhausted: the lowest cap stays in effect", q.onUnderrun(t + 1_000))
        assertNull(q.onUnderrun(t + 2_000))
        assertEquals(1_000_000, q.currentCap)
    }

    @Test fun firstStepLandsBelowTheFilesOwnBitrate() {
        // An 8 Mbps file direct-plays unchanged at 20M and 10M, so the first useful stop is 6M.
        val q = AutoQuality()
        q.start(sourceBitrateBps = 8_000_000)
        q.onUnderrun(0)
        assertEquals(6_000_000, q.onUnderrun(1_000))

        // A big remux starts at the top of the ladder.
        val big = AutoQuality()
        big.start(sourceBitrateBps = 45_000_000)
        big.onUnderrun(0)
        assertEquals(20_000_000, big.onUnderrun(1_000))

        // Nothing on the ladder is below a tiny file: transcoding cannot help it.
        val tiny = AutoQuality()
        tiny.start(sourceBitrateBps = 500_000)
        assertNull(tiny.onUnderrun(0))
        assertNull(tiny.onUnderrun(1_000))
    }

    @Test fun startResetsTheLadderForANewPlayback() {
        val q = AutoQuality()
        q.start()
        q.onUnderrun(0); q.onUnderrun(1_000)
        q.onUnderrun(2_000); q.onUnderrun(3_000)
        assertEquals(10_000_000, q.currentCap)
        q.start()
        assertNull(q.currentCap)
        q.onUnderrun(10_000)
        assertEquals(20_000_000, q.onUnderrun(11_000))
    }
}
