@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.GitHubOverviewMetricItem
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
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingRefreshStatus
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalSearchActionDock
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.appFloatingDockBottomTarget
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import os.kei.ui.page.main.widget.glass.rememberAppFloatingKeyboardLiftState
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class OsPageMainListChromeState(
    val isDark: Boolean,
    val titleColor: Color,
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
    val overviewMetricRows: List<OsOverviewMetricRow>,
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
    val showFloatingAddButton: Boolean,
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
    innerPadding: PaddingValues,
    scrollBehaviorConnection: NestedScrollConnection,
    contentBackdrop: LayerBackdrop,
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
    val overviewMetricRows = overviewState.overviewMetricRows
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
    val showFloatingAddButton = searchDockState.showFloatingAddButton
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
    val topMetricLabel = stringResource(R.string.os_overview_metric_top_info)

    fun metricLabelWeight(label: String): Float = if (label == topMetricLabel) 0.30f else 0.56f

    fun metricValueWeight(label: String): Float = if (label == topMetricLabel) 0.70f else 0.44f
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
    val dockStartPadding = if (floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val refreshStatus =
        when (systemOverviewState) {
            SystemOverviewState.Refreshing -> AppFloatingRefreshStatus.Refreshing
            SystemOverviewState.Completed -> AppFloatingRefreshStatus.Success
            SystemOverviewState.Failed -> AppFloatingRefreshStatus.Danger
            SystemOverviewState.Cached -> AppFloatingRefreshStatus.Cached
            SystemOverviewState.Idle -> AppFloatingRefreshStatus.Idle
        }
    val moreIcon = appLucideMoreIcon()
    val expandDockDescription = stringResource(R.string.common_expand)

    Box(modifier = Modifier.fillMaxSize()) {
        AppPageLazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehaviorConnection),
            state = listState,
            innerPadding = innerPadding,
            sectionSpacing = 0.dp,
        ) {
            item(
                key = "os-overview-card",
                contentType = "os_overview_card",
            ) {
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
                            backdrop = contentBackdrop,
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
                    ) {
                        overviewMetricRows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap),
                            ) {
                                GitHubOverviewMetricItem(
                                    label = pair.first.label,
                                    value = pair.first.value,
                                    titleColor = if (isDark) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                                    valueColor =
                                        pair.first.valueColor
                                            ?: MiuixTheme.colorScheme.onBackground,
                                    labelMaxLines = 1,
                                    valueMaxLines = 1,
                                    labelWeight = metricLabelWeight(pair.first.label),
                                    valueWeight = metricValueWeight(pair.first.label),
                                    backdrop = contentBackdrop,
                                    modifier = Modifier.weight(1f),
                                )
                                val second = pair.second
                                if (second != null) {
                                    GitHubOverviewMetricItem(
                                        label = second.label,
                                        value = second.value,
                                        titleColor = if (isDark) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                                        valueColor =
                                            second.valueColor
                                                ?: MiuixTheme.colorScheme.onBackground,
                                        labelMaxLines = 1,
                                        valueMaxLines = 1,
                                        labelWeight = metricLabelWeight(second.label),
                                        valueWeight = metricValueWeight(second.label),
                                        backdrop = contentBackdrop,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item(
                key = "os-overview-space",
                contentType = "os_overview_space",
            ) {
                Spacer(modifier = Modifier.height(AppChromeTokens.pageSectionGap))
            }

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
