package os.kei.ui.page.main.os

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.components.OsOverviewMetricRow
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

@Immutable
internal data class OsPageDerivedState(
    val query: String,
    val displayedTopInfoRows: List<InfoRow>,
    val groupedTopInfoRows: List<Pair<String, List<InfoRow>>>,
    val shellRunnerRows: List<InfoRow>,
    val displayedSystemRows: List<InfoRow>,
    val displayedSecureRows: List<InfoRow>,
    val displayedGlobalRows: List<InfoRow>,
    val displayedAndroidRows: List<InfoRow>,
    val displayedJavaRows: List<InfoRow>,
    val displayedLinuxRows: List<InfoRow>,
    val prunedSystemRows: List<InfoRow>,
    val prunedSecureRows: List<InfoRow>,
    val prunedGlobalRows: List<InfoRow>,
    val prunedAndroidRows: List<InfoRow>,
    val prunedJavaRows: List<InfoRow>,
    val prunedLinuxRows: List<InfoRow>,
    val overviewUiState: OsOverviewUiState,
    val overviewMetricRows: List<OsOverviewMetricRow>,
    val visibleActivityShortcutCards: List<OsActivityShortcutCard>,
    val visibleShellCommandCards: List<OsShellCommandCard>
)

@Composable
internal fun rememberOsPageDerivedState(
    context: Context,
    queryApplied: String,
    shizukuStatus: String,
    shellSavedCountLabel: String,
    shellCommandCards: List<OsShellCommandCard>,
    sectionStates: Map<SectionKind, SectionState>,
    topInfoExpanded: Boolean,
    systemTableExpanded: Boolean,
    secureTableExpanded: Boolean,
    globalTableExpanded: Boolean,
    androidPropsExpanded: Boolean,
    javaPropsExpanded: Boolean,
    linuxEnvExpanded: Boolean,
    isDark: Boolean,
    inactiveColor: Color,
    cachedColor: Color,
    refreshingColor: Color,
    syncedColor: Color,
    surfaceColor: Color,
    refreshing: Boolean,
    refreshProgress: Float,
    cachePersisted: Boolean,
    visibleCards: Set<OsSectionCard>,
    activityShortcutCards: List<OsActivityShortcutCard>,
): OsPageDerivedState {
    var rowsState by remember { mutableStateOf(OsPageRowsDerivedState.Empty) }
    LaunchedEffect(
        queryApplied,
        sectionStates,
        topInfoExpanded,
        systemTableExpanded,
        secureTableExpanded,
        globalTableExpanded,
        androidPropsExpanded,
        javaPropsExpanded,
        linuxEnvExpanded
    ) {
        rowsState =
            withContext(AppDispatchers.uiDerivation) {
                deriveOsPageRowsState(
                    queryApplied = queryApplied,
                    sectionStates = sectionStates,
                    expansionFlags = OsPageExpansionFlags(
                        topInfoExpanded = topInfoExpanded,
                        systemTableExpanded = systemTableExpanded,
                        secureTableExpanded = secureTableExpanded,
                        globalTableExpanded = globalTableExpanded,
                        androidPropsExpanded = androidPropsExpanded,
                        javaPropsExpanded = javaPropsExpanded,
                        linuxEnvExpanded = linuxEnvExpanded
                    )
                )
            }
    }
    var groupedTopInfoRows by remember { mutableStateOf<List<Pair<String, List<InfoRow>>>>(emptyList()) }
    LaunchedEffect(
        context,
        rowsState.displayedTopInfoRows,
        topInfoExpanded,
        rowsState.query
    ) {
        groupedTopInfoRows =
            withContext(AppDispatchers.uiDerivation) {
                if (rowsState.query.isBlank() && !topInfoExpanded) {
                    emptyList()
                } else {
                    groupTopInfoRows(context, rowsState.displayedTopInfoRows)
                }
            }
    }
    val shellRunnerRows = remember(
        shizukuStatus,
        context,
        shellSavedCountLabel,
        shellCommandCards
    ) {
        listOf(
            InfoRow(
                key = context.getString(R.string.os_shell_card_status_label),
                value = shizukuStatus
            ),
            InfoRow(
                key = shellSavedCountLabel,
                value = context.getString(R.string.common_item_count, shellCommandCards.size)
            )
        )
    }
    val overviewUiState = remember(
        isDark,
        inactiveColor,
        cachedColor,
        refreshingColor,
        syncedColor,
        refreshing,
        refreshProgress,
        cachePersisted,
        visibleCards,
        sectionStates,
        rowsState.topInfoRows.size,
        rowsState.visibleRowsCount,
        activityShortcutCards,
        shellCommandCards,
        surfaceColor
    ) {
        buildOsOverviewUiState(
            context = context,
            isDark = isDark,
            inactiveColor = inactiveColor,
            cachedColor = cachedColor,
            refreshingColor = refreshingColor,
            syncedColor = syncedColor,
            surfaceColor = surfaceColor,
            refreshing = refreshing,
            refreshProgress = refreshProgress,
            cachePersisted = cachePersisted,
            visibleCards = visibleCards,
            sectionStates = sectionStates,
            topInfoCount = rowsState.topInfoRows.size,
            visibleRowsCount = rowsState.visibleRowsCount,
            activityCards = activityShortcutCards,
            shellCommandCards = shellCommandCards
        )
    }
    val overviewMetricRows = remember(overviewUiState.metrics) {
        overviewUiState.metrics.chunked(2).mapNotNull { pair ->
            pair.firstOrNull()?.let { first ->
                OsOverviewMetricRow(first = first, second = pair.getOrNull(1))
            }
        }
    }
    val visibleActivityShortcutCards = remember(activityShortcutCards) {
        activityShortcutCards.filter { it.visible }
    }
    val visibleShellCommandCards = remember(shellCommandCards) {
        shellCommandCards.filter { it.visible }
    }

    return OsPageDerivedState(
        query = rowsState.query,
        displayedTopInfoRows = rowsState.displayedTopInfoRows,
        groupedTopInfoRows = groupedTopInfoRows,
        shellRunnerRows = shellRunnerRows,
        displayedSystemRows = rowsState.displayedSystemRows,
        displayedSecureRows = rowsState.displayedSecureRows,
        displayedGlobalRows = rowsState.displayedGlobalRows,
        displayedAndroidRows = rowsState.displayedAndroidRows,
        displayedJavaRows = rowsState.displayedJavaRows,
        displayedLinuxRows = rowsState.displayedLinuxRows,
        prunedSystemRows = rowsState.prunedSystemRows,
        prunedSecureRows = rowsState.prunedSecureRows,
        prunedGlobalRows = rowsState.prunedGlobalRows,
        prunedAndroidRows = rowsState.prunedAndroidRows,
        prunedJavaRows = rowsState.prunedJavaRows,
        prunedLinuxRows = rowsState.prunedLinuxRows,
        overviewUiState = overviewUiState,
        overviewMetricRows = overviewMetricRows,
        visibleActivityShortcutCards = visibleActivityShortcutCards,
        visibleShellCommandCards = visibleShellCommandCards
    )
}
