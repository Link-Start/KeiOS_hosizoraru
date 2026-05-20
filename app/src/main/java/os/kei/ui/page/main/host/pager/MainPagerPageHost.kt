@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.ba.BAPage
import os.kei.ui.page.main.github.page.GitHubPage
import os.kei.ui.page.main.home.HomePage
import os.kei.ui.page.main.mcp.McpPage
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.os.OsPage
import os.kei.ui.page.main.widget.glass.GlassEffectRuntime
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import os.kei.ui.testing.KeiOsTestTags

@Composable
internal fun MainPagerPageHost(
    pageType: BottomPage,
    runtime: MainPageRuntime,
    visibleBottomPages: Set<BottomPage>,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    liquidActionBarLayeredStyleEnabled: Boolean,
    homeIconHdrEnabled: Boolean,
    homeDynamicFullEffectEnabled: Boolean,
    preloadingEnabled: Boolean,
    mcpServerManager: McpServerManager,
    homeMcpOverview: HomeMcpOverview,
    homeGitHubOverview: HomeGitHubOverview,
    homeBaOverview: HomeBaOverview,
    visibleOverviewCards: Set<HomeOverviewCard>,
    showCacheFreshnessInCards: Boolean,
    requestedGitHubRefreshToken: Int,
    requestedGitHubActionsTrackId: String?,
    requestedGitHubActionsSheetToken: Int,
    onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
    onShowBottomBar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenGitHubPage: () -> Unit,
    onOpenPoolGuideDetail: (String) -> Unit,
    onOpenBaGuideCatalog: () -> Unit,
    onOpenMcpSkill: () -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit,
) {
    val glassRuntime = remember { GlassEffectRuntime() }
    CompositionLocalProvider(
        LocalGlassEffectRuntime provides glassRuntime,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(pageType.pageRootTestTag()),
        ) {
            when (pageType) {
                BottomPage.Home -> {
                    HomePage(
                        shizukuStatus = shizukuStatus,
                        mcpOverview = homeMcpOverview,
                        homeGitHubOverview = homeGitHubOverview,
                        homeBaOverview = homeBaOverview,
                        homeIconHdrEnabled = homeIconHdrEnabled,
                        homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
                        runtime = runtime,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        visibleBottomPages = visibleBottomPages,
                        visibleOverviewCards = visibleOverviewCards,
                        showCacheFreshnessInCards = showCacheFreshnessInCards,
                        onBottomPageVisibilityChange = onBottomPageVisibilityChange,
                        onOverviewCardVisibilityChange = onOverviewCardVisibilityChange,
                        onCacheFreshnessVisibilityChange = onCacheFreshnessVisibilityChange,
                        onShowBottomBar = onShowBottomBar,
                        onOpenGitHubPage = onOpenGitHubPage,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Os -> {
                    OsPage(
                        runtime = runtime,
                        shizukuStatus = shizukuStatus,
                        shizukuApiUtils = shizukuApiUtils,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Ba -> {
                    BAPage(
                        runtime = runtime,
                        preloadingEnabled = preloadingEnabled,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onOpenPoolStudentGuide = onOpenPoolGuideDetail,
                        onOpenGuideCatalog = onOpenBaGuideCatalog,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Mcp -> {
                    McpPage(
                        mcpServerManager = mcpServerManager,
                        runtime = runtime,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onOpenSkill = onOpenMcpSkill,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.GitHub -> {
                    GitHubPage(
                        runtime = runtime,
                        externalRefreshTriggerToken = requestedGitHubRefreshToken,
                        externalActionsTrackId = requestedGitHubActionsTrackId,
                        externalActionsSheetToken = requestedGitHubActionsSheetToken,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }
            }
        }
    }
}

private fun BottomPage.pageRootTestTag(): String =
    when (this) {
        BottomPage.Home -> KeiOsTestTags.HomePageRoot
        BottomPage.Os -> KeiOsTestTags.OsPageRoot
        BottomPage.Mcp -> KeiOsTestTags.McpPageRoot
        BottomPage.GitHub -> KeiOsTestTags.GitHubPageRoot
        BottomPage.Ba -> KeiOsTestTags.BaPageRoot
    }
