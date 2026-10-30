package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.phone.ui.blurSource

class BlurSourceTest {
    @Test fun replacesAnExistingWidth() =
        assertEquals("http://h/Items/1/Images/Backdrop/0?maxWidth=48", blurSource("http://h/Items/1/Images/Backdrop/0?maxWidth=960", 48))
    @Test fun addsAWidthWhenMissing() {
        assertEquals("http://h/x?maxWidth=320", blurSource("http://h/x", 320))
        assertEquals("http://h/x?tag=a&maxWidth=320", blurSource("http://h/x?tag=a", 320))
    }
}
