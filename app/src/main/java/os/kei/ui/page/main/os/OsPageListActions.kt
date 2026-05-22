package os.kei.ui.page.main.os

import android.content.Context
import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellRunnerActivity
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.state.OsPageActionState
import os.kei.ui.page.main.os.state.OsPageTextBundle

@Immutable
internal data class OsPageMainListActions(
    val onOpenShellRunner: () -> Unit,
    val onShellCommandCardExpandedChange: (String, Boolean) -> Unit,
    val onOpenShellCommandCardEditor: (OsShellCommandCard) -> Unit,
    val onRunShellCommandCard: (OsShellCommandCard) -> Unit,
    val onActivityCardExpandedChange: (String, Boolean) -> Unit,
    val onOpenActivityShortcutCard: (OsActivityShortcutCard) -> Unit,
    val onOpenActivityShortcutCardEditor: (OsActivityShortcutCard) -> Unit,
    val isCardVisible: (OsSectionCard) -> Boolean,
    val sectionSubtitle: (SectionKind, Int) -> String,
    val onExportCard: (OsSectionCard) -> Unit,
    val onRefreshAll: () -> Unit,
    val onOpenAddActivityShortcutCard: () -> Unit
)

internal fun createOsPageMainListActions(
    context: Context,
    textBundle: OsPageTextBundle,
    actionState: OsPageActionState,
    routeState: OsPageRouteState,
    shizukuStatus: String,
    activityCardExpanded: MutableMap<String, Boolean>,
    shellCommandCardExpanded: MutableMap<String, Boolean>,
    osPageViewModel: OsPageViewModel
): OsPageMainListActions {
    return OsPageMainListActions(
        onOpenShellRunner = { OsShellRunnerActivity.launch(context) },
        onShellCommandCardExpandedChange = { cardId, expanded ->
            shellCommandCardExpanded[cardId] = expanded
        },
        onOpenShellCommandCardEditor = osPageViewModel::openShellCommandCardEditor,
        onRunShellCommandCard = { card ->
            actionState.runShellCommandCard(card)
        },
        onActivityCardExpandedChange = { cardId, expanded ->
            activityCardExpanded[cardId] = expanded
        },
        onOpenActivityShortcutCard = { card ->
            osPageViewModel.openActivityShortcutCard(
                card = card,
                defaults = textBundle.googleSystemServiceDefaults,
            )
        },
        onOpenActivityShortcutCardEditor = { card ->
            osPageViewModel.openActivityShortcutCardEditor(
                card = card,
                defaults = textBundle.googleSystemServiceDefaults,
            )
        },
        isCardVisible = { card -> isCardVisible(routeState.visibleCards, card) },
        sectionSubtitle = { section, size ->
            sectionSubtitle(
                sectionStates = routeState.sectionStates,
                context = context,
                section = section,
                size = size
            )
        },
        onExportCard = { card ->
            osPageViewModel.prepareSectionCardExport(
                card = card,
                context = context,
                googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
                shizukuStatus = shizukuStatus,
                ensureLoad = actionState.ensureLoad,
            )
        },
        onRefreshAll = actionState.refreshAllSections,
        onOpenAddActivityShortcutCard = {
            osPageViewModel.openAddActivityShortcutCardEditor(textBundle.googleSystemServiceDefaults)
        }
    )
}
