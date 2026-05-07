package os.kei.ui.page.main.model

import os.kei.feature.home.model.HomeOverviewCard

internal fun BottomPage.toHomeOverviewCardOrNull(): HomeOverviewCard? {
    return when (this) {
        BottomPage.Mcp -> HomeOverviewCard.MCP
        BottomPage.GitHub -> HomeOverviewCard.GITHUB
        BottomPage.Ba -> HomeOverviewCard.BA
        BottomPage.Home,
        BottomPage.Os -> null
    }
}
