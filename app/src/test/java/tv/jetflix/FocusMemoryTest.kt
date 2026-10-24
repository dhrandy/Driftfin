package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.components.FocusMemory

class FocusMemoryTest {
    @Test fun freshScreenHasNothingToRestore() {
        val m = FocusMemory(null)
        assertFalse(m.hasKey)
        assertFalse(m.pending)
    }

    @Test fun returningScreenRestoresOnceThenStopsStealingFocus() {
        val m = FocusMemory("Recently Added|abc")
        assertTrue(m.hasKey && m.pending)
        assertEquals("Recently Added|abc", m.key)
        m.pending = false
        m.key = "Recently Added|def"
        assertFalse(m.pending)
    }
}
