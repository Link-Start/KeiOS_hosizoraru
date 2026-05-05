package os.kei.ui.page.main.os.components

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.GitHubOverviewMetricItem
import os.kei.ui.page.main.os.InfoRow
import os.kei.ui.page.main.os.OsCardExportAction
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.SectionKind
import os.kei.ui.page.main.os.SystemOverviewState
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.os.osLucideEnterIcon
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingRefreshStatus
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalSearchActionDock
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLift
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OsPageMainList(
    context: Context,
    listState: LazyListState,
    innerPadding: PaddingValues,
    scrollBehaviorConnection: NestedScrollConnection,
    contentBackdrop: LayerBackdrop,
    isDark: Boolean,
    titleColor: Color,
    refreshing: Boolean,
    overviewState: SystemOverviewState,
    indicatorProgress: Float,
    statusColor: Color,
    indicatorBg: Color,
    statusLabel: String,
    overviewCardColor: Color,
    overviewBorderColor: Color,
    overviewMetrics: List<OsOverviewMetric>,
    noMatchedResultsText: String,
    query: String,
    displayedTopInfoRows: List<InfoRow>,
    groupedTopInfoRows: List<Pair<String, List<InfoRow>>>,
    topInfoExpanded: Boolean,
    onTopInfoExpandedChange: (Boolean) -> Unit,
    shellRunnerRows: List<InfoRow>,
    shellRunnerExpanded: Boolean,
    onShellRunnerExpandedChange: (Boolean) -> Unit,
    onOpenShellRunner: () -> Unit,
    shellCommandCards: List<OsShellCommandCard>,
    shellCommandCardExpanded: Map<String, Boolean>,
    runningShellCommandCardIds: Set<String>,
    onShellCommandCardExpandedChange: (String, Boolean) -> Unit,
    onOpenShellCommandCardEditor: (OsShellCommandCard) -> Unit,
    onRunShellCommandCard: (OsShellCommandCard) -> Unit,
    activityShortcutCards: List<OsActivityShortcutCard>,
    defaultActivityCardTitle: String,
    activityCardExpanded: Map<String, Boolean>,
    onActivityCardExpandedChange: (String, Boolean) -> Unit,
    onOpenActivityShortcutCard: (OsActivityShortcutCard) -> Unit,
    onOpenActivityShortcutCardEditor: (OsActivityShortcutCard) -> Unit,
    displayedSystemRows: List<InfoRow>,
    displayedSecureRows: List<InfoRow>,
    displayedGlobalRows: List<InfoRow>,
    displayedAndroidRows: List<InfoRow>,
    displayedJavaRows: List<InfoRow>,
    displayedLinuxRows: List<InfoRow>,
    prunedSystemRows: List<InfoRow>,
    prunedSecureRows: List<InfoRow>,
    prunedGlobalRows: List<InfoRow>,
    prunedAndroidRows: List<InfoRow>,
    prunedJavaRows: List<InfoRow>,
    prunedLinuxRows: List<InfoRow>,
    systemTableExpanded: Boolean,
    onSystemTableExpandedChange: (Boolean) -> Unit,
    secureTableExpanded: Boolean,
    onSecureTableExpandedChange: (Boolean) -> Unit,
    globalTableExpanded: Boolean,
    onGlobalTableExpandedChange: (Boolean) -> Unit,
    androidPropsExpanded: Boolean,
    onAndroidPropsExpandedChange: (Boolean) -> Unit,
    javaPropsExpanded: Boolean,
    onJavaPropsExpandedChange: (Boolean) -> Unit,
    linuxEnvExpanded: Boolean,
    onLinuxEnvExpandedChange: (Boolean) -> Unit,
    isCardVisible: (OsSectionCard) -> Boolean,
    sectionSubtitle: (SectionKind, Int) -> String,
    exportingCard: OsSectionCard?,
    onExportCard: (OsSectionCard) -> Unit,
    onRefreshAll: () -> Unit,
    contentBottomPadding: Dp,
    showFloatingAddButton: Boolean,
    onOpenAddActivityShortcutCard: () -> Unit,
    bottomBarVisible: Boolean,
    searchExpanded: Boolean,
    queryInput: String,
    onQueryInputChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchLabel: String,
    floatingDockSide: AppFloatingDockSide
) {
    val topMetricLabel = stringResource(R.string.os_overview_metric_top_info)
    fun metricLabelWeight(label: String): Float {
        return if (label == topMetricLabel) 0.30f else 0.56f
    }
    fun metricValueWeight(label: String): Float {
        return if (label == topMetricLabel) 0.70f else 0.44f
    }
    val bottomBarOffset = if (bottomBarVisible) 0.dp else AppChromeTokens.floatingBottomBarOuterHeight
    val searchDockBottom by animateDpAsState(
        targetValue = contentBottomPadding - 24.dp - bottomBarOffset,
        label = "os_floating_search_bottom"
    )
    val floatingKeyboardLift = rememberAppFloatingKeyboardLift(
        restingBottomGap = searchDockBottom,
        label = "os_floating_keyboard_lift"
    )
    val dockAlignment = if (floatingDockSide == AppFloatingDockSide.Start) {
        Alignment.BottomStart
    } else {
        Alignment.BottomEnd
    }
    val dockStartPadding = if (floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val refreshStatus = when (overviewState) {
        SystemOverviewState.Refreshing -> AppFloatingRefreshStatus.Refreshing
        SystemOverviewState.Completed -> AppFloatingRefreshStatus.Success
        SystemOverviewState.Failed -> AppFloatingRefreshStatus.Danger
        SystemOverviewState.Cached -> AppFloatingRefreshStatus.Cached
        SystemOverviewState.Idle -> AppFloatingRefreshStatus.Idle
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppPageLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehaviorConnection),
            state = listState,
            innerPadding = innerPadding,
            sectionSpacing = 0.dp
        ) {
            item {
                AppOverviewCard(
                    title = stringResource(R.string.os_overview_title),
                    backdrop = contentBackdrop,
                    containerColor = overviewCardColor,
                    borderColor = overviewBorderColor,
                    contentColor = titleColor,
                    onClick = {
                        if (refreshing) return@AppOverviewCard
                        onRefreshAll()
                    },
                    headerEndActions = {
                        if (overviewState != SystemOverviewState.Idle) {
                            LiquidCircularProgressBar(
                                progress = { indicatorProgress },
                                size = 16.dp,
                                strokeWidth = 2.dp,
                                activeColor = statusColor,
                                inactiveColor = indicatorBg
                            )
                        }
                        StatusPill(
                            label = statusLabel,
                            color = statusColor,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                            backgroundAlphaOverride = if (isDark) 0.24f else 0.34f,
                            borderAlphaOverride = if (isDark) 0.42f else 0.52f,
                            backdrop = contentBackdrop
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
                    ) {
                        overviewMetrics.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
                            ) {
                                GitHubOverviewMetricItem(
                                    label = pair[0].label,
                                    value = pair[0].value,
                                    titleColor = if (isDark) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                                    valueColor = pair[0].valueColor
                                        ?: MiuixTheme.colorScheme.onBackground,
                                    labelMaxLines = 1,
                                    valueMaxLines = 1,
                                    labelWeight = metricLabelWeight(pair[0].label),
                                    valueWeight = metricValueWeight(pair[0].label),
                                    backdrop = contentBackdrop,
                                    modifier = Modifier.weight(1f)
                                )
                                if (pair.size > 1) {
                                    GitHubOverviewMetricItem(
                                        label = pair[1].label,
                                        value = pair[1].value,
                                        titleColor = if (isDark) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                                        valueColor = pair[1].valueColor
                                            ?: MiuixTheme.colorScheme.onBackground,
                                        labelMaxLines = 1,
                                        valueMaxLines = 1,
                                        labelWeight = metricLabelWeight(pair[1].label),
                                        valueWeight = metricValueWeight(pair[1].label),
                                        backdrop = contentBackdrop,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(AppChromeTokens.pageSectionGap)) }

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
                        onExportClick = { onExportCard(OsSectionCard.TOP_INFO) }
                    )
                }
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
                        onClick = onOpenShellRunner
                    )
                }
            )

            addShellCommandCards(
                cards = shellCommandCards,
                contentBackdrop = contentBackdrop,
                expandedStates = shellCommandCardExpanded,
                runningCardIds = runningShellCommandCardIds,
                onExpandedChange = onShellCommandCardExpandedChange,
                onHeaderLongClick = onOpenShellCommandCardEditor,
                onRunCard = onRunShellCommandCard
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.SYSTEM),
                card = OsSectionCard.SYSTEM,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_system_title),
                subtitle = sectionSubtitle(
                    SectionKind.SYSTEM,
                    if (query.isBlank()) prunedSystemRows.size else displayedSystemRows.size
                ),
                expanded = systemTableExpanded,
                onExpandedChange = onSystemTableExpandedChange,
                rows = displayedSystemRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.SYSTEM,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.SYSTEM) }
                    )
                }
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.SECURE),
                card = OsSectionCard.SECURE,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_secure_title),
                subtitle = sectionSubtitle(
                    SectionKind.SECURE,
                    if (query.isBlank()) prunedSecureRows.size else displayedSecureRows.size
                ),
                expanded = secureTableExpanded,
                onExpandedChange = onSecureTableExpandedChange,
                rows = displayedSecureRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.SECURE,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.SECURE) }
                    )
                }
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.GLOBAL),
                card = OsSectionCard.GLOBAL,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_global_title),
                subtitle = sectionSubtitle(
                    SectionKind.GLOBAL,
                    if (query.isBlank()) prunedGlobalRows.size else displayedGlobalRows.size
                ),
                expanded = globalTableExpanded,
                onExpandedChange = onGlobalTableExpandedChange,
                rows = displayedGlobalRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.GLOBAL,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.GLOBAL) }
                    )
                }
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.ANDROID),
                card = OsSectionCard.ANDROID,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_android_title),
                subtitle = sectionSubtitle(
                    SectionKind.ANDROID,
                    if (query.isBlank()) prunedAndroidRows.size else displayedAndroidRows.size
                ),
                expanded = androidPropsExpanded,
                onExpandedChange = onAndroidPropsExpandedChange,
                rows = displayedAndroidRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.ANDROID,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.ANDROID) }
                    )
                }
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.JAVA),
                card = OsSectionCard.JAVA,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_java_title),
                subtitle = sectionSubtitle(
                    SectionKind.JAVA,
                    if (query.isBlank()) prunedJavaRows.size else displayedJavaRows.size
                ),
                expanded = javaPropsExpanded,
                onExpandedChange = onJavaPropsExpandedChange,
                rows = displayedJavaRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.JAVA,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.JAVA) }
                    )
                }
            )

            addKeyValueSectionCard(
                visible = isCardVisible(OsSectionCard.LINUX),
                card = OsSectionCard.LINUX,
                contentBackdrop = contentBackdrop,
                title = context.getString(R.string.os_section_linux_title),
                subtitle = sectionSubtitle(
                    SectionKind.LINUX,
                    if (query.isBlank()) prunedLinuxRows.size else displayedLinuxRows.size
                ),
                expanded = linuxEnvExpanded,
                onExpandedChange = onLinuxEnvExpandedChange,
                rows = displayedLinuxRows,
                noMatchedResultsText = noMatchedResultsText,
                exportAction = {
                    OsCardExportAction(
                        card = OsSectionCard.LINUX,
                        exportingCard = exportingCard,
                        onExportClick = { onExportCard(OsSectionCard.LINUX) }
                    )
                }
            )

            addShortcutActivityCards(
                cards = activityShortcutCards,
                contentBackdrop = contentBackdrop,
                defaultCardTitle = defaultActivityCardTitle,
                expandedStates = activityCardExpanded,
                onExpandedChange = onActivityCardExpandedChange,
                onOpenActivity = onOpenActivityShortcutCard,
                onHeaderLongClick = onOpenActivityShortcutCardEditor
            )
        }

        AppFloatingVerticalSearchActionDock(
            backdrop = contentBackdrop,
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
            showAddAction = showFloatingAddButton,
            refreshEnabled = !refreshing,
            refreshStatus = refreshStatus,
            dockSide = floatingDockSide,
            keyboardLift = floatingKeyboardLift,
            modifier = Modifier
                .align(dockAlignment)
                .padding(start = dockStartPadding, end = dockEndPadding, bottom = searchDockBottom)
        )
    }
}
