package os.kei.ui.page.main.host.main

import org.junit.Test
import os.kei.MainActivity
import os.kei.ui.navigation.KeiosRoute
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainStartupDestinationTest {
    @Test fun `Home becomes ready after requested tab is consumed`() {
        val request = MainHostUiState.Initial.copy(requestedBottomPage = "Ba", requestedBottomPageToken = 1)
        assertFalse(isMainStartupDestinationReady(request, KeiosRoute.Main))
        assertTrue(isMainStartupDestinationReady(request.copy(requestedBottomPage = null), KeiosRoute.Main))
        assertFalse(isMainStartupDestinationReady(MainHostUiState.Initial, null))
    }

    @Test fun `cold shortcuts do not reveal Home while their route is pending`() {
        for ((host, destination) in listOf(
            MainHostUiState.Initial.copy(requestedWebDavSyncToken = 1) to KeiosRoute.WebDavSync,
            MainHostUiState.Initial.copy(requestedOsShellRunnerToken = 1) to KeiosRoute.OsShellRunner,
            MainHostUiState.Initial.copy(requestedBaBgmPlaybackToken = 1) to KeiosRoute.BaGuideCatalog(openBgmPlaybackToken = 1),
        )) {
            assertFalse(isMainStartupDestinationReady(host, KeiosRoute.Main))
            assertTrue(isMainStartupDestinationReady(host, destination))
        }
    }

    @Test fun `banner launch requires the requested tab and nonce`() {
        val host = MainHostUiState.Initial.copy(
            requestedBaCalendarPoolToken = 3,
            requestedBaCalendarPoolRoute = MainActivity.TARGET_ROUTE_BA_POOL,
        )
        assertFalse(isMainStartupDestinationReady(host, KeiosRoute.BaCalendarPool(nonce = 3)))
        assertFalse(isMainStartupDestinationReady(host, KeiosRoute.BaCalendarPool(showPool = true, nonce = 2)))
        assertTrue(isMainStartupDestinationReady(host, KeiosRoute.BaCalendarPool(showPool = true, nonce = 3)))
    }
}
