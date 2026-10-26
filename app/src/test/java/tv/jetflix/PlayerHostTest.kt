package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.player.PlayerHost
import tv.jetflix.app.ui.player.PlayerHost.Layer

class PlayerHostTest {
    @Test fun playerOnTopOwnsTheRemote() = assertEquals(Layer.Interactive, PlayerHost.layer("Player", "Details"))
    @Test fun actorOverPlayerKeepsPlayerButNotInteractive() = assertEquals(Layer.CoveredByActor, PlayerHost.layer("Actor", "Player"))
    @Test fun actorOverDetailsHasNoPlayer() = assertEquals(Layer.Absent, PlayerHost.layer("Actor", "Details"))
    @Test fun movieOpenedFromActorOverPlayerDropsThePlayer() = assertEquals(Layer.Absent, PlayerHost.layer("Details", "Actor"))
    @Test fun everythingElseHasNoPlayer() {
        for (top in listOf("Home", "Details", "Settings", "Search", "Trailer", "")) assertEquals(Layer.Absent, PlayerHost.layer(top, "Player"))
    }
    @Test fun backFromActorToPlayerIsTheSameLayerAsBefore() {
        // [Details, Player, Actor] -> pop -> [Details, Player]: the player never leaves the host between the two states.
        assertEquals(Layer.CoveredByActor, PlayerHost.layer("Actor", "Player"))
        assertEquals(Layer.Interactive, PlayerHost.layer("Player", "Details"))
    }
}
