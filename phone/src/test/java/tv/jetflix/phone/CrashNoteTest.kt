package tv.jetflix.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashNoteTest {
    @Test fun noteKeepsClassNamesAndDropsMessages() {
        val note = CrashNote.sanitized(IllegalStateException("http://my.server:8096 token=abc123"))
        assertTrue(note.contains("IllegalStateException"))
        assertFalse(note.contains("token"))
        assertFalse(note.contains("8096"))
    }
}
