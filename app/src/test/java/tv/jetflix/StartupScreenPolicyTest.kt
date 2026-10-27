package tv.jetflix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.components.showStartupWall

class StartupScreenPolicyTest {
    @Test fun initialEmptyLoadShowsWall() = assertTrue(showStartupWall(true, false, false))
    @Test fun readyHomeDoesNotShowWall() = assertFalse(showStartupWall(false, true, false))
    @Test fun emptyLibraryDoesNotStayBehindWall() = assertFalse(showStartupWall(false, false, false))
    @Test fun failedLoadDoesNotStayBehindWall() = assertFalse(showStartupWall(false, false, false))
    @Test fun refreshWithContentDoesNotShowWall() = assertFalse(showStartupWall(true, true, false))
    @Test fun laterEmptyRefreshDoesNotReshowWall() = assertFalse(showStartupWall(true, false, true))
}
