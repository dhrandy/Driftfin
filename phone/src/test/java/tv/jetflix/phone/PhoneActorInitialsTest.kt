package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.phone.ui.actorInitials

class PhoneActorInitialsTest {
    @Test fun firstAndLastWord() = assertEquals("KR", actorInitials("Keanu Reeves"))
    @Test fun lowerCaseIsUppercased() = assertEquals("KR", actorInitials("keanu reeves"))
    @Test fun singleWordGetsOneLetter() = assertEquals("M", actorInitials("Madonna"))
    @Test fun middleWordsAreIgnored() = assertEquals("CF", actorInitials("Charlie Sheen Ferrell"))
    @Test fun extraWhitespaceIsIgnored() = assertEquals("JR", actorInitials("  John   Rhys  "))
    @Test fun nonAsciiIsKept() = assertEquals("ÉZ", actorInitials("Émile Zola"))
    @Test fun blankNameGetsNoInitials() = assertEquals("", actorInitials("   "))
}
