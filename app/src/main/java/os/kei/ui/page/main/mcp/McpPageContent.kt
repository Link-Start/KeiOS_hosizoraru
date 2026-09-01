@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.R
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import os.kei.ui.page.main.host.pager.MainPageContentBackdropScene
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.mcp.section.McpOnboardingGuideSection
import os.kei.ui.page.main.mcp.section.McpLogsSection
import os.kei.ui.page.main.mcp.section.McpOverviewCardSection
import os.kei.ui.page.main.mcp.section.McpServiceControlSection
import os.kei.ui.page.main.mcp.section.McpToolAdvancedSection
import os.kei.ui.page.main.mcp.section.McpToolBaSection
import os.kei.ui.page.main.mcp.section.McpToolCodexSection
import os.kei.ui.page.main.mcp.section.McpToolEntrypointsSection
import os.kei.ui.page.main.mcp.section.McpToolGithubSection
import os.kei.ui.page.main.mcp.section.McpToolRuntimeSection
import os.kei.ui.page.main.mcp.section.McpToolSystemSection
import os.kei.ui.page.main.mcp.section.McpToolWorkflowSection
import os.kei.ui.page.main.mcp.state.McpPageOverviewState
import os.kei.ui.page.main.mcp.state.McpToolBuckets
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageAlternatingLanes
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.chrome.rememberAppPullToRefreshState
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppEdgeStackListTopInset
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun McpPageContent(
    uiState: McpServerUiState,
    pageUiState: McpPageUiState,
    toolBuckets: McpToolBuckets,
    overviewState: McpPageOverviewState,
    runtime: MainPageRuntime,
    innerPadding: PaddingValues,
    listState: LazyListState,
    secondaryListState: LazyListState,
    columnCount: Int,
    scrollBehavior: ScrollBehavior,
    backdrops: MainPageBackdropSet,
    backdropProducerActive: Boolean,
    titleColor: Color,
    subtitleColor: Color,
    isDark: Boolean,
    refreshRunning: Boolean,
    actions: McpPageActions,
) {
    val edgeStackState = rememberAppEdgeStackState(stackLine = AppEdgeStackListTopInset)
    MainPageContentBackdropScene(
        contentProducer = null,
        sheetProducer = backdrops.sheetProducer,
        producerActive = backdropProducerActive,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // The MCP status hub stays pinned above the list; tool cards stack beneath it.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                    ),
        ) {
            McpOverviewCardSection(
                backdrop = backdrops.contentMaterial,
                titleColor = titleColor,
                overviewCardColor = overviewState.overviewCardColor,
                overviewBorderColor = overviewState.overviewBorderColor,
                overviewAccentColor = overviewState.overviewAccentColor,
                runtimeText = overviewState.runtimeText,
                isDark = isDark,
                running = uiState.running,
                overviewPills = overviewState.overviewPills,
                onToggleServer = actions.onToggleServer,
                onOpenEditSheet = actions.onOpenEditSheet,
            )
        }
        CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
        PullToRefresh(
            isRefreshing = refreshRunning,
            onRefresh = actions.onRefreshNow,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .layerBackdrop(backdrops.topBarProducer),
            pullToRefreshState = rememberAppPullToRefreshState(),
            topAppBarScrollBehavior = scrollBehavior,
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
            refreshTexts =
                listOf(
                    stringResource(R.string.mcp_pull_refresh_pull),
                    stringResource(R.string.mcp_pull_refresh_release),
                    stringResource(R.string.mcp_pull_refresh_refreshing),
                    stringResource(R.string.mcp_pull_refresh_done),
                ),
        ) {
        // Inside PullToRefresh, so the RefreshHeader above keeps its own anchor — see OsPageMainList.
        AppEdgeStackKeepAlive(
            state = edgeStackState,
            modifier = Modifier.fillMaxSize(),
        ) {
        val sections = mcpPageSections(toolBuckets)
        val sectionInput =
            McpSectionRenderInput(
                uiState = uiState,
                pageUiState = pageUiState,
                toolBuckets = toolBuckets,
                backdrop = backdrops.contentMaterial,
                subtitleColor = subtitleColor,
                actions = actions,
            )
        val listModifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        val listInnerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
        val listBottomExtra = appPageBottomPaddingWithFloatingOverlay(runtime.contentBottomPadding)
        val listTopExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset)
        if (columnCount >= 2) {
            val (left, right) = appPageAlternatingLanes(sections)
            AppPageTwoColumnLists(
                innerPadding = listInnerPadding,
                primaryState = listState,
                secondaryState = secondaryListState,
                modifier = listModifier,
                bottomExtra = listBottomExtra,
                topExtra = listTopExtra,
                sectionSpacing = 12.dp,
                primary = { mcpSectionLane(sections = left, input = sectionInput) },
                secondary = { mcpSectionLane(sections = right, input = sectionInput) },
            )
        } else {
            AppPageLazyColumn(
                innerPadding = listInnerPadding,
                state = listState,
                modifier = listModifier,
                bottomExtra = listBottomExtra,
                topExtra = listTopExtra,
                sectionSpacing = 12.dp,
            ) {
                mcpSectionItems(sections = sections, input = sectionInput)
            }
        }
        }
        }
        }
        }

        McpPageFloatingActionDock(
            backdrop = backdrops.topBar,
            uiState = uiState,
            runtime = runtime,
            actions = actions,
        )
    }
}
