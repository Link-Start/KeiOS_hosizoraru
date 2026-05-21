@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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

@Immutable
internal data class MainPagerHomePageState(
    val shizukuStatus: String,
    val homeIconHdrEnabled: Boolean,
    val homeDynamicFullEffectEnabled: Boolean,
    val visibleBottomPages: Set<BottomPage>,
    val homeMcpOverview: HomeMcpOverview,
    val homeGitHubOverview: HomeGitHubOverview,
    val homeBaOverview: HomeBaOverview,
    val visibleOverviewCards: Set<HomeOverviewCard>,
    val showCacheFreshnessInCards: Boolean,
    val onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    val onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    val onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
    val onOpenGitHubPage: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAbout: () -> Unit,
)

@Stable
internal data class MainPagerOsPageState(
    val shizukuStatus: String,
    val shizukuApiUtils: ShizukuApiUtils,
)

@Immutable
internal data class MainPagerBaPageState(
    val preloadingEnabled: Boolean,
    val onOpenPoolGuideDetail: (String) -> Unit,
    val onOpenBaGuideCatalog: () -> Unit,
)

@Stable
internal data class MainPagerMcpPageState(
    val mcpServerManager: McpServerManager,
    val onOpenMcpSkill: () -> Unit,
)

@Immutable
internal data class MainPagerGitHubPageState(
    val requestedGitHubRefreshToken: Int,
    val requestedGitHubActionsTrackId: String?,
    val requestedGitHubActionsSheetToken: Int,
)

@Composable
internal fun MainPagerPageHost(
    pageType: BottomPage,
    runtime: MainPageRuntime,
    liquidActionBarLayeredStyleEnabled: Boolean,
    homePageState: MainPagerHomePageState?,
    osPageState: MainPagerOsPageState?,
    baPageState: MainPagerBaPageState?,
    mcpPageState: MainPagerMcpPageState?,
    githubPageState: MainPagerGitHubPageState?,
    onShowBottomBar: () -> Unit,
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
                    val homeState = checkNotNull(homePageState) {
                        "Home page state is required for the Home tab"
                    }
                    HomePage(
                        shizukuStatus = homeState.shizukuStatus,
                        mcpOverview = homeState.homeMcpOverview,
                        homeGitHubOverview = homeState.homeGitHubOverview,
                        homeBaOverview = homeState.homeBaOverview,
                        homeIconHdrEnabled = homeState.homeIconHdrEnabled,
                        homeDynamicFullEffectEnabled = homeState.homeDynamicFullEffectEnabled,
                        runtime = runtime,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        visibleBottomPages = homeState.visibleBottomPages,
                        visibleOverviewCards = homeState.visibleOverviewCards,
                        showCacheFreshnessInCards = homeState.showCacheFreshnessInCards,
                        onBottomPageVisibilityChange = homeState.onBottomPageVisibilityChange,
                        onOverviewCardVisibilityChange = homeState.onOverviewCardVisibilityChange,
                        onCacheFreshnessVisibilityChange = homeState.onCacheFreshnessVisibilityChange,
                        onShowBottomBar = onShowBottomBar,
                        onOpenGitHubPage = homeState.onOpenGitHubPage,
                        onOpenSettings = homeState.onOpenSettings,
                        onOpenAbout = homeState.onOpenAbout,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Os -> {
                    val osState = checkNotNull(osPageState) {
                        "OS page state is required for the OS tab"
                    }
                    OsPage(
                        runtime = runtime,
                        shizukuStatus = osState.shizukuStatus,
                        shizukuApiUtils = osState.shizukuApiUtils,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Ba -> {
                    val baState = checkNotNull(baPageState) {
                        "BA page state is required for the BA tab"
                    }
                    BAPage(
                        runtime = runtime,
                        preloadingEnabled = baState.preloadingEnabled,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onOpenPoolStudentGuide = baState.onOpenPoolGuideDetail,
                        onOpenGuideCatalog = baState.onOpenBaGuideCatalog,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.Mcp -> {
                    val mcpState = checkNotNull(mcpPageState) {
                        "MCP page state is required for the MCP tab"
                    }
                    McpPage(
                        mcpServerManager = mcpState.mcpServerManager,
                        runtime = runtime,
                        liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                        onShowBottomBar = onShowBottomBar,
                        onOpenSkill = mcpState.onOpenMcpSkill,
                        onActionBarInteractingChanged = onActionBarInteractingChanged,
                    )
                }

                BottomPage.GitHub -> {
                    val githubState = checkNotNull(githubPageState) {
                        "GitHub page state is required for the GitHub tab"
                    }
                    GitHubPage(
                        runtime = runtime,
                        externalRefreshTriggerToken = githubState.requestedGitHubRefreshToken,
                        externalActionsTrackId = githubState.requestedGitHubActionsTrackId,
                        externalActionsSheetToken = githubState.requestedGitHubActionsSheetToken,
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
