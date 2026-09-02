@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import os.kei.R
import os.kei.ui.page.main.host.pager.MainPageContentBackdropScene
import os.kei.ui.page.main.os.OsPageCardListDerivedState
import os.kei.ui.page.main.os.OsPageDerivedState
import os.kei.ui.page.main.os.OsPageMainListActions
import os.kei.ui.page.main.os.OsCardExportAction
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.SectionKind
import os.kei.ui.page.main.os.SystemOverviewState
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.os.osLucideEnterIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import os.kei.ui.page.main.widget.chrome.AppPageColumnGap
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.chrome.appFloatingDockEndPadding
import os.kei.ui.page.main.widget.chrome.appFloatingDockStartPadding
import os.kei.ui.page.main.widget.chrome.rememberAppPullToRefreshState
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.AppOverviewPill
import os.kei.ui.page.main.widget.core.AppOverviewPillFlow
import os.kei.ui.page.main.widget.core.AppOverviewPillItem
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppEdgeStackListTopInset
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingRefreshStatus
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalSearchActionDock
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.appFloatingDockBottomTarget
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class OsPageMainListChromeState(
    val isDark: Boolean,
    val titleColor: Color,
    val backdropProducerActive: Boolean,
    val contentBottomPadding: Dp,
    val bottomBarVisible: Boolean,
    val floatingDockSide: AppFloatingDockSide,
    val onExpandFloatingDock: () -> Unit,
)

@Immutable
internal data class OsPageMainListOverviewState(
    val refreshing: Boolean,
    val overviewState: SystemOverviewState,
    val indicatorProgress: Float,
    val statusColor: Color,
    val indicatorBg: Color,
    val statusLabel: String,
    val overviewCardColor: Color,
    val overviewBorderColor: Color,
    val overviewMetrics: List<OsOverviewMetric>,
)

@Immutable
internal data class OsPageMainListContentState(
    val noMatchedResultsText: String,
    val derivedState: OsPageDerivedState,
    val cardListDerivedState: OsPageCardListDerivedState,
    val runningShellCommandCardIds: Set<String>,
    val activityIconBitmaps: Map<String, Bitmap>,
    val defaultActivityCardTitle: String,
    val exportingCard: OsSectionCard?,
)

@Immutable
internal data class OsPageMainListExpansionState(
    val topInfoExpanded: Boolean,
    val shellRunnerExpanded: Boolean,
    val shellCommandCardExpanded: Map<String, Boolean>,
    val activityCardExpanded: Map<String, Boolean>,
    val systemTableExpanded: Boolean,
    val secureTableExpanded: Boolean,
    val globalTableExpanded: Boolean,
    val androidPropsExpanded: Boolean,
    val javaPropsExpanded: Boolean,
    val linuxEnvExpanded: Boolean,
    val onTopInfoExpandedChange: (Boolean) -> Unit,
    val onShellRunnerExpandedChange: (Boolean) -> Unit,
    val onSystemTableExpandedChange: (Boolean) -> Unit,
    val onSecureTableExpandedChange: (Boolean) -> Unit,
    val onGlobalTableExpandedChange: (Boolean) -> Unit,
    val onAndroidPropsExpandedChange: (Boolean) -> Unit,
    val onJavaPropsExpandedChange: (Boolean) -> Unit,
    val onLinuxEnvExpandedChange: (Boolean) -> Unit,
)

@Immutable
internal data class OsPageMainListSearchDockState(
    val addActionEnabled: Boolean,
    val searchExpanded: Boolean,
    val queryInput: String,
    val searchLabel: String,
    val onQueryInputChange: (String) -> Unit,
    val onSearchExpandedChange: (Boolean) -> Unit,
)

@Composable
internal fun OsPageMainList(
    context: Context,
    listState: LazyListState,
    secondaryListState: LazyListState,
    columnCount: Int,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    topBarProducer: LayerBackdrop,
    topBarMaterial: Backdrop,
    contentBackdrop: Backdrop,
    sheetProducer: LayerBackdrop,
    chromeState: OsPageMainListChromeState,
    overviewState: OsPageMainListOverviewState,
    contentState: OsPageMainListContentState,
    expansionState: OsPageMainListExpansionState,
    searchDockState: OsPageMainListSearchDockState,
    actions: OsPageMainListActions,
) {
    val isDark = chromeState.isDark
    val titleColor = chromeState.titleColor
    val contentBottomPadding = chromeState.contentBottomPadding
    val bottomBarVisible = chromeState.bottomBarVisible
    val floatingDockSide = chromeState.floatingDockSide
    val refreshing = overviewState.refreshing
    val systemOverviewState = overviewState.overviewState
    val indicatorProgress = overviewState.indicatorProgress
    val statusColor = overviewState.statusColor
    val indicatorBg = overviewState.indicatorBg
    val statusLabel = overviewState.statusLabel
    val overviewCardColor = overviewState.overviewCardColor
    val overviewBorderColor = overviewState.overviewBorderColor
    val overviewMetrics = overviewState.overviewMetrics
    val overviewPills =
        overviewMetrics.map { metric ->
            AppOverviewPill(
                label =
                    stringResource(
                        R.string.os_overview_metric_pill,
                        metric.label,
                        metric.value,
                    ),
                color = metric.valueColor ?: MiuixTheme.colorScheme.primary,
            )
        }
    val topOverviewPill = overviewPills.firstOrNull()
    val bodyOverviewPills = overviewPills.drop(1)
    val noMatchedResultsText = contentState.noMatchedResultsText
    val derivedState = contentState.derivedState
    val query = derivedState.query
    val displayedTopInfoRows = derivedState.displayedTopInfoRows
    val groupedTopInfoRows = derivedState.groupedTopInfoRows
    val shellRunnerRows = derivedState.shellRunnerRows
    val displayedSystemRows = derivedState.displayedSystemRows
    val displayedSecureRows = derivedState.displayedSecureRows
    val displayedGlobalRows = derivedState.displayedGlobalRows
    val displayedAndroidRows = derivedState.displayedAndroidRows
    val displayedJavaRows = derivedState.displayedJavaRows
    val displayedLinuxRows = derivedState.displayedLinuxRows
    val prunedSystemRows = derivedState.prunedSystemRows
    val prunedSecureRows = derivedState.prunedSecureRows
    val prunedGlobalRows = derivedState.prunedGlobalRows
    val prunedAndroidRows = derivedState.prunedAndroidRows
    val prunedJavaRows = derivedState.prunedJavaRows
    val prunedLinuxRows = derivedState.prunedLinuxRows
    val shellCommandCards = contentState.cardListDerivedState.visibleShellCommandCards
    val activityShortcutCards = contentState.cardListDerivedState.visibleActivityShortcutCards
    val runningShellCommandCardIds = contentState.runningShellCommandCardIds
    val activityIconBitmaps = contentState.activityIconBitmaps
    val defaultActivityCardTitle = contentState.defaultActivityCardTitle
    val exportingCard = contentState.exportingCard
    val topInfoExpanded = expansionState.topInfoExpanded
    val shellRunnerExpanded = expansionState.shellRunnerExpanded
    val shellCommandCardExpanded = expansionState.shellCommandCardExpanded
    val activityCardExpanded = expansionState.activityCardExpanded
    val systemTableExpanded = expansionState.systemTableExpanded
    val secureTableExpanded = expansionState.secureTableExpanded
    val globalTableExpanded = expansionState.globalTableExpanded
    val androidPropsExpanded = expansionState.androidPropsExpanded
    val javaPropsExpanded = expansionState.javaPropsExpanded
    val linuxEnvExpanded = expansionState.linuxEnvExpanded
    val addActionEnabled = searchDockState.addActionEnabled
    val searchExpanded = searchDockState.searchExpanded
    val queryInput = searchDockState.queryInput
    val searchLabel = searchDockState.searchLabel
    val onTopInfoExpandedChange = expansionState.onTopInfoExpandedChange
    val onShellRunnerExpandedChange = expansionState.onShellRunnerExpandedChange
    val onSystemTableExpandedChange = expansionState.onSystemTableExpandedChange
    val onSecureTableExpandedChange = expansionState.onSecureTableExpandedChange
    val onGlobalTableExpandedChange = expansionState.onGlobalTableExpandedChange
    val onAndroidPropsExpandedChange = expansionState.onAndroidPropsExpandedChange
    val onJavaPropsExpandedChange = expansionState.onJavaPropsExpandedChange
    val onLinuxEnvExpandedChange = expansionState.onLinuxEnvExpandedChange
    val onOpenShellRunner = actions.onOpenShellRunner
    val onShellCommandCardExpandedChange = actions.onShellCommandCardExpandedChange
    val onOpenShellCommandCardEditor = actions.onOpenShellCommandCardEditor
    val onRunShellCommandCard = actions.onRunShellCommandCard
    val onActivityCardExpandedChange = actions.onActivityCardExpandedChange
    val onOpenActivityShortcutCard = actions.onOpenActivityShortcutCard
    val onOpenActivityShortcutCardEditor = actions.onOpenActivityShortcutCardEditor
    val isCardVisible = actions.isCardVisible
    val sectionSubtitle = actions.sectionSubtitle
    val onExportCard = actions.onExportCard
    val onRefreshAll = actions.onRefreshAll
    val onOpenAddActivityShortcutCard = actions.onOpenAddActivityShortcutCard
    val onQueryInputChange = searchDockState.onQueryInputChange
    val onSearchExpandedChange = searchDockState.onSearchExpandedChange
    val onExpandFloatingDock = chromeState.onExpandFloatingDock
    val searchDockBottomTarget =
        appFloatingDockBottomTarget(
            contentBottomPadding = contentBottomPadding,
            bottomBarVisible = bottomBarVisible,
        )
    val searchDockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = contentBottomPadding,
            bottomBarVisible = bottomBarVisible,
            label = "os_floating_search_bottom",
        )
    val floatingKeyboardLiftState =
        rememberAppFloatingKeyboardLiftState(
            restingBottomGap = searchDockBottomTarget,
            label = "os_floating_keyboard_lift",
        )
    val floatingKeyboardLiftProvider = remember(floatingKeyboardLiftState) { { floatingKeyboardLiftState.value } }
    val dockAlignment =
        if (floatingDockSide == AppFloatingDockSide.Start) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }
    val dockStartPadding = appFloatingDockStartPadding(floatingDockSide == AppFloatingDockSide.Start)
    val dockEndPadding = appFloatingDockEndPadding(floatingDockSide == AppFloatingDockSide.End)
    val refreshStatus =
        when (systemOverviewState) {
            SystemOverviewState.Refreshing -> AppFloatingRefreshStatus.Refreshing
            SystemOverviewState.Completed -> AppFloatingRefreshStatus.Success
            SystemOverviewState.Failed -> AppFloatingRefreshStatus.Danger
            SystemOverviewState.Cached -> AppFloatingRefreshStatus.Cached
            SystemOverviewState.Idle -> AppFloatingRefreshStatus.Idle
        }
    var pullRefreshSessionActive by remember { mutableStateOf(false) }
    val refreshingNow by rememberUpdatedState(refreshing)
    LaunchedEffect(pullRefreshSessionActive) {
        if (!pullRefreshSessionActive) return@LaunchedEffect
        val refreshStarted =
            withTimeoutOrNull(OsPullRefreshStartTimeoutMs) {
                snapshotFlow { refreshingNow }.first { it }
            } != null
        if (refreshStarted) {
            snapshotFlow { refreshingNow }.first { !it }
        }
        pullRefreshSessionActive = false
    }
    val moreIcon = appLucideMoreIcon()
    val expandDockDescription = stringResource(R.string.common_expand)

    val edgeStackState = rememberAppEdgeStackState(stackLine = AppEdgeStackListTopInset)
    MainPageContentBackdropScene(
        contentProducer = null,
        sheetProducer = sheetProducer,
        producerActive = chromeState.backdropProducerActive,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // The overview card is the page's status hub: it lives above the lazy list so it
        // never scrolls or joins the edge stack, and the pile forms beneath it.
        AppOverviewCard(
            title = stringResource(R.string.os_overview_title),
            backdrop = contentBackdrop,
            containerColor = overviewCardColor,
            borderColor = overviewBorderColor,
            contentColor = titleColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    // This card sits *above* the lazy list, so it never receives the list's content padding
                    // and has to add the large-screen gutter itself. Without it the status hub stayed
                    // full-bleed while every row under it narrowed, which read as two different pages.
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                    ),
            // Refresh has the pull gesture and the section's own controls; adding an
            // activity card had only a dock button. The scarcer surface wins the click.
            onClick = {
                if (!addActionEnabled) return@AppOverviewCard
                onOpenAddActivityShortcutCard()
            },
            titleAccessory = {
                topOverviewPill?.let { pill ->
                    AppOverviewPillItem(pill = pill)
                }
            },
            headerEndActions = {
                if (systemOverviewState != SystemOverviewState.Idle) {
                    LiquidCircularProgressBar(
                        progress = { indicatorProgress },
                        size = 16.dp,
                        strokeWidth = 2.dp,
                        activeColor = statusColor,
                        inactiveColor = indicatorBg,
                    )
                }
                StatusPill(
                    label = statusLabel,
                    color = statusColor,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    backgroundAlphaOverride = if (isDark) 0.24f else 0.34f,
                    borderAlphaOverride = if (isDark) 0.42f else 0.52f,
                )
            },
        ) {
            AppOverviewPillFlow(
                pills = bodyOverviewPills,
                batchLiquidBackdrop = true,
            )
        }
        CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
        PullToRefresh(
            isRefreshing = pullRefreshSessionActive,
            onRefresh = {
                if (!refreshing) {
                    pullRefreshSessionActive = true
                    onRefreshAll()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .layerBackdrop(topBarProducer),
            pullToRefreshState = rememberAppPullToRefreshState(),
            topAppBarScrollBehavior = scrollBehavior,
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
            refreshTexts =
                listOf(
                    stringResource(R.string.os_pull_refresh_pull),
                    stringResource(R.string.os_pull_refresh_release),
                    stringResource(R.string.os_pull_refresh_refreshing),
                    stringResource(R.string.os_pull_refresh_done),
                ),
        ) {
        // Inside PullToRefresh, not around it: the keep-alive box shifts the list up and takes over as
        // the stack container, and doing that below the RefreshHeader leaves the header's own anchor and
        // the pull threshold exactly where they were.
        AppEdgeStackKeepAlive(
            state = edgeStackState,
            modifier = Modifier.fillMaxSize(),
        ) {
        // Built once and laid out twice: the same cards become one list on a phone and two columns
        // on a tablet, so the two shapes cannot drift about which cards exist or in what order.
        val osCards =
            buildOsCards {
            addTopInfoCard(
                visible = isCardVisible(OsSectionCard.TOP_INFO),
                contentBackdrop = contentBackdrop,
                displayedTopInfoRows = displayedTopInfoRows,
                groupedTopInfoRows = groupedTopInfoRows,
                query = query,
                noMatchedResultsText = noMatchedResultsText,
                expanded = topInfoExpanded,
                onExpandedChange = onTopInfoExpandedChange,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.TOP_INFO,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.TOP_INFO) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.SHELL_RUNNER),
                card = OsSectionCard.SHELL_RUNNER,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_shell_card_title),
                subtitle = context.getString(R.string.os_shell_card_subtitle),
                expanded = shellRunnerExpanded,
                onExpandedChange = onShellRunnerExpandedChange,
                rows = shellRunnerRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    AppCompactIconAction(
                        icon = osLucideEnterIcon(),
                        contentDescription = stringResource(R.string.os_shell_card_cd_open),
                        onClick = onOpenShellRunner,
                        modifier = Modifier.testTag(KeiOsTestTags.OsShellRunnerButton),
                    )
                },
            )

            addShellCommandCards(
                cards = shellCommandCards,
                contentBackdrop = contentBackdrop,
                expandedStates = shellCommandCardExpanded,
                runningCardIds = runningShellCommandCardIds,
                onExpandedChange = onShellCommandCardExpandedChange,
                onHeaderLongClick = onOpenShellCommandCardEditor,
                onRunCard = onRunShellCommandCard,
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.SYSTEM),
                card = OsSectionCard.SYSTEM,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_system_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.SYSTEM,
                        if (query.isBlank()) prunedSystemRows.size else displayedSystemRows.size,
                    ),
                expanded = systemTableExpanded,
                onExpandedChange = onSystemTableExpandedChange,
                rows = displayedSystemRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.SYSTEM,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.SYSTEM) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.SECURE),
                card = OsSectionCard.SECURE,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_secure_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.SECURE,
                        if (query.isBlank()) prunedSecureRows.size else displayedSecureRows.size,
                    ),
                expanded = secureTableExpanded,
                onExpandedChange = onSecureTableExpandedChange,
                rows = displayedSecureRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.SECURE,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.SECURE) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.GLOBAL),
                card = OsSectionCard.GLOBAL,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_global_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.GLOBAL,
                        if (query.isBlank()) prunedGlobalRows.size else displayedGlobalRows.size,
                    ),
                expanded = globalTableExpanded,
                onExpandedChange = onGlobalTableExpandedChange,
                rows = displayedGlobalRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.GLOBAL,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.GLOBAL) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.ANDROID),
                card = OsSectionCard.ANDROID,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_android_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.ANDROID,
                        if (query.isBlank()) prunedAndroidRows.size else displayedAndroidRows.size,
                    ),
                expanded = androidPropsExpanded,
                onExpandedChange = onAndroidPropsExpandedChange,
                rows = displayedAndroidRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.ANDROID,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.ANDROID) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.JAVA),
                card = OsSectionCard.JAVA,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_java_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.JAVA,
                        if (query.isBlank()) prunedJavaRows.size else displayedJavaRows.size,
                    ),
                expanded = javaPropsExpanded,
                onExpandedChange = onJavaPropsExpandedChange,
                rows = displayedJavaRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.JAVA,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.JAVA) },
                    )
                },
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.LINUX),
                card = OsSectionCard.LINUX,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_linux_title),
                subtitle =
                    sectionSubtitle(
                        SectionKind.LINUX,
                        if (query.isBlank()) prunedLinuxRows.size else displayedLinuxRows.size,
                    ),
                expanded = linuxEnvExpanded,
                onExpandedChange = onLinuxEnvExpandedChange,
                rows = displayedLinuxRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.LINUX,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.LINUX) },
                    )
                },
            )

            addShortcutActivityCards(
                cards = activityShortcutCards,
                iconBitmaps = activityIconBitmaps,
                contentBackdrop = contentBackdrop,
                defaultCardTitle = defaultActivityCardTitle,
                expandedStates = activityCardExpanded,
                onExpandedChange = onActivityCardExpandedChange,
                onOpenActivity = onOpenActivityShortcutCard,
                onHeaderLongClick = onOpenActivityShortcutCardEditor,
            )
            }
        val lanes =
            if (columnCount >= 2) {
                osCardLanes(
                    cards = osCards,
                    shellCardKey = osKeyValueCardKey(OsSectionCard.SHELL_RUNNER),
                )
            } else {
                null
            }
        if (lanes == null) {
            AppPageLazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                // Every card used to be followed by its own 8dp Spacer item, which doubled the
                // list's item count: half of what the lazy column composed, measured and recorded
                // each frame was an empty box. The arrangement produces the identical gap for free.
                // The trailing 8dp the last spacer contributed moves into the bottom padding.
                innerPadding =
                    PaddingValues(bottom = innerPadding.calculateBottomPadding() + OsSectionGap),
                // The headroom is invisible viewport above the list, so the content inset has to absorb
                // it or the first card starts that far off screen.
                topExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset),
                sectionSpacing = OsSectionGap,
            ) {
                osCardItems(osCards)
            }
        } else {
            AppPageTwoColumnLists(
                innerPadding =
                    PaddingValues(bottom = innerPadding.calculateBottomPadding() + OsSectionGap),
                primaryState = listState,
                secondaryState = secondaryListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                topExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset),
                sectionSpacing = OsSectionGap,
                primary = { osCardItems(lanes.primary) },
                secondary = { osCardItems(lanes.secondary) },
            )
        }
        }
        }
        }
        }

        AppFloatingVerticalSearchActionDock(
            backdrop = topBarMaterial,
            expanded = searchExpanded,
            query = queryInput,
            onQueryChange = onQueryInputChange,
            onExpandedChange = onSearchExpandedChange,
            searchIcon = appLucideSearchIcon(),
            searchContentDescription = searchLabel,
            placeholder = searchLabel,
            addIcon = appLucideAddIcon(),
            addContentDescription = stringResource(R.string.os_cd_add_activity_card),
            onAddClick = onOpenAddActivityShortcutCard,
            refreshIcon = appLucideRefreshIcon(),
            refreshContentDescription = stringResource(R.string.common_refresh),
            onRefreshClick = onRefreshAll,
            showAddAction = false,
            showRefreshAction = false,
            refreshEnabled = !refreshing,
            refreshStatus = refreshStatus,
            compact = !bottomBarVisible,
            compactIcon = moreIcon,
            compactContentDescription = expandDockDescription,
            onCompactClick = onExpandFloatingDock,
            dockSide = floatingDockSide,
            keyboardLiftProvider = floatingKeyboardLiftProvider,
            modifier =
                Modifier
                    .align(dockAlignment)
                    .offset { IntOffset(x = 0, y = -searchDockBottomState.value.roundToPx()) }
                    .padding(start = dockStartPadding, end = dockEndPadding),
        )
    }
}

/** The gap between OS section cards, formerly an 8dp Spacer item after every card. */
private val OsSectionGap = 8.dp

private const val OsPullRefreshStartTimeoutMs = 4_000L
