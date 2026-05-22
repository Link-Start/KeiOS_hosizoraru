package os.kei.ui.page.main.os

import android.content.Context
import androidx.compose.runtime.Immutable
import os.kei.R
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellRunnerActivity
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.createDefaultActivityShortcutDraft
import os.kei.ui.page.main.os.state.OsPageActionState
import os.kei.ui.page.main.os.state.OsPageOverlayState
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
    overlayState: OsPageOverlayState,
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
        onOpenShellCommandCardEditor = { card ->
            overlayState.onEditingShellCommandCardIdChange(card.id)
            overlayState.onShellCommandCardDraftChange(card)
            overlayState.onShowShellCommandCardEditorChange(true)
        },
        onRunShellCommandCard = { card ->
            actionState.runShellCommandCard(card)
        },
        onActivityCardExpandedChange = { cardId, expanded ->
            activityCardExpanded[cardId] = expanded
        },
        onOpenActivityShortcutCard = { card ->
            openOsActivityShortcutCard(
                context = context,
                card = card,
                defaults = textBundle.googleSystemServiceDefaults,
                invalidTargetMessage = context.getString(R.string.os_google_system_service_toast_invalid_target),
                openFailedMessage = { error ->
                    context.getString(
                        R.string.os_google_system_service_toast_open_failed,
                        error.javaClass.simpleName
                    )
                }
            )
        },
        onOpenActivityShortcutCardEditor = { card ->
            beginEditingOsActivityShortcutCard(
                card = card,
                defaults = textBundle.googleSystemServiceDefaults,
                onEditModeChange = overlayState.onActivityCardEditModeChange,
                onEditingCardIdChange = overlayState.onEditingActivityShortcutCardIdChange,
                onEditingBuiltInChange = overlayState.onEditingActivityShortcutBuiltInChange,
                onDraftChange = overlayState.onActivityShortcutDraftChange,
                onShowEditorChange = overlayState.onShowActivityShortcutEditorChange
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
            overlayState.onActivityCardEditModeChange(OsActivityCardEditMode.Add)
            overlayState.onEditingActivityShortcutCardIdChange(null)
            overlayState.onEditingActivityShortcutBuiltInChange(false)
            overlayState.onActivityShortcutDraftChange(
                createDefaultActivityShortcutDraft(textBundle.googleSystemServiceDefaults)
            )
            overlayState.onShowActivityShortcutEditorChange(true)
        }
    )
}
