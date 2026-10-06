package os.kei.ui.page.main.host.main

import os.kei.MainActivity
import os.kei.ui.navigation.KeiosRoute

internal fun isMainStartupDestinationReady(host: MainHostUiState, route: KeiosRoute?): Boolean =
    when {
        host.requestedBaCalendarPoolToken > 0 -> route is KeiosRoute.BaCalendarPool &&
            route.nonce == host.requestedBaCalendarPoolToken.toLong() &&
            route.showPool == (host.requestedBaCalendarPoolRoute == MainActivity.TARGET_ROUTE_BA_POOL)
        host.requestedOsShellRunnerToken > 0 -> route == KeiosRoute.OsShellRunner
        host.requestedWebDavSyncToken > 0 -> route == KeiosRoute.WebDavSync
        host.requestedBaBgmPlaybackToken > 0 -> route is KeiosRoute.BaGuideCatalog &&
            route.openBgmPlaybackToken == host.requestedBaBgmPlaybackToken.toLong()
        else -> route != null && host.requestedBottomPage.isNullOrBlank()
    }
