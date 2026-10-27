package tv.jetflix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.player.shouldAutoFinish

/** Regression: while an actor page covers the player, ENDED must not pop the player out from under it. */
class PlayerAutoFinishTest {
    @Test fun endedWhileCoveredByAnActorPageWaits() {
        assertFalse("covered player must not finish on ENDED", shouldAutoFinish(ended = true, interactive = false))
    }

    @Test fun endedWhileInteractiveFinishes() {
        assertTrue(shouldAutoFinish(ended = true, interactive = true))
    }

    @Test fun stillPlayingNeverFinishes() {
        assertFalse(shouldAutoFinish(ended = false, interactive = true))
        assertFalse(shouldAutoFinish(ended = false, interactive = false))
    }

    @Test fun coveredPlayerFinishesOnceUncovered() {
        // Same state after Back removes the actor page: interactive flips true, the effect reruns, player finishes.
        assertTrue(shouldAutoFinish(ended = true, interactive = true))
    }
}
