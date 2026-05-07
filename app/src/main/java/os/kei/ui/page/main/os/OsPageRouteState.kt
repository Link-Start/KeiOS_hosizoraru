package os.kei.ui.page.main.os

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

@Immutable
internal data class OsPageRouteState(
    val queryApplied: String,
    val uiSnapshot: OsUiSnapshot,
    val visibleCards: Set<OsSectionCard>,
    val activityShortcutCards: List<OsActivityShortcutCard>,
    val shellCommandCards: List<OsShellCommandCard>,
    val sectionStates: Map<SectionKind, SectionState>,
    val refreshing: Boolean,
    val refreshProgress: Float,
    val cachePersisted: Boolean,
    val runningShellCommandCardIds: Set<String>
)

@Composable
internal fun rememberOsPageRouteState(
    queryApplied: String,
    uiSnapshot: OsUiSnapshot,
    visibleCards: Set<OsSectionCard>,
    activityShortcutCards: List<OsActivityShortcutCard>,
    shellCommandCards: List<OsShellCommandCard>,
    sectionStates: Map<SectionKind, SectionState>,
    refreshing: Boolean,
    refreshProgress: Float,
    cachePersisted: Boolean,
    runningShellCommandCardIds: Set<String>
): OsPageRouteState {
    return remember(
        queryApplied,
        uiSnapshot,
        visibleCards,
        activityShortcutCards,
        shellCommandCards,
        sectionStates,
        refreshing,
        refreshProgress,
        cachePersisted,
        runningShellCommandCardIds
    ) {
        OsPageRouteState(
            queryApplied = queryApplied,
            uiSnapshot = uiSnapshot,
            visibleCards = visibleCards,
            activityShortcutCards = activityShortcutCards,
            shellCommandCards = shellCommandCards,
            sectionStates = sectionStates,
            refreshing = refreshing,
            refreshProgress = refreshProgress,
            cachePersisted = cachePersisted,
            runningShellCommandCardIds = runningShellCommandCardIds
        )
    }
}
