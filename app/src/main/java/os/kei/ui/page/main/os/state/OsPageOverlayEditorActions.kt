package os.kei.ui.page.main.os.state

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.shell.createDefaultShellCommandCardDraft
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionItem
import os.kei.ui.page.main.os.shortcut.applyGoogleSystemServiceSuggestion
import os.kei.ui.page.main.os.shortcut.applyShortcutImplicitDefaults
import os.kei.ui.page.main.os.shortcut.createDefaultActivityShortcutDraft

internal data class OsPageOverlayEditorActions(
    val onDeleteShellCommandCard: () -> Unit,
    val onDismissShellCommandCardEditor: () -> Unit,
    val onDismissShellCommandCardEditorFinished: () -> Unit,
    val onSaveShellCommandCard: () -> Unit,
    val onOpenActivitySuggestionSheet: (ShortcutSuggestionField) -> Unit,
    val onDeleteActivityCard: () -> Unit,
    val onDismissActivityEditor: () -> Unit,
    val onDismissActivityEditorFinished: () -> Unit,
    val onSaveActivityEditor: () -> Unit,
    val onApplySuggestion: (ShortcutSuggestionItem) -> Unit,
    val onApplyExplicitActionRecommendation: () -> Unit,
    val onApplyImplicitActionRecommendation: () -> Unit,
    val onApplyExplicitCategoryRecommendation: () -> Unit,
    val onApplyImplicitCategoryRecommendation: () -> Unit,
    val onDismissShellCardDeleteConfirm: () -> Unit,
    val onConfirmShellCardDelete: () -> Unit,
    val onDismissActivityCardDeleteConfirm: () -> Unit,
    val onConfirmActivityCardDelete: () -> Unit,
)

@Composable
internal fun rememberOsPageOverlayEditorActions(
    context: Context,
    osPageViewModel: OsPageViewModel,
    overlayState: OsPageOverlayState,
    activitySuggestionTarget: ShortcutSuggestionField,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    googleSystemServiceDefaultIntentFlags: String,
    shellCardCommandRequiredToast: String,
): OsPageOverlayEditorActions {
    return remember(
        context,
        osPageViewModel,
        overlayState,
        activitySuggestionTarget,
        googleSystemServiceDefaults,
        googleSystemServiceDefaultIntentFlags,
        shellCardCommandRequiredToast,
    ) {
        OsPageOverlayEditorActions(
            onDeleteShellCommandCard = {
                val targetId = overlayState.editingShellCommandCardId.orEmpty().trim()
                if (targetId.isBlank()) {
                    overlayState.onShowShellCommandCardEditorChange(false)
                    overlayState.onShowShellCardDeleteConfirmChange(false)
                    return@OsPageOverlayEditorActions
                }
                overlayState.onShowShellCardDeleteConfirmChange(true)
            },
            onDismissShellCommandCardEditor = {
                overlayState.onShowShellCommandCardEditorChange(false)
                overlayState.onShowShellCardDeleteConfirmChange(false)
            },
            onDismissShellCommandCardEditorFinished = {
                if (overlayState.showShellCommandCardEditor) {
                    return@OsPageOverlayEditorActions
                }
                overlayState.onEditingShellCommandCardIdChange(null)
                overlayState.onShellCommandCardDraftChange(createDefaultShellCommandCardDraft())
                overlayState.onShowShellCardDeleteConfirmChange(false)
            },
            onSaveShellCommandCard = {
                val targetId = overlayState.editingShellCommandCardId.orEmpty().trim()
                if (targetId.isBlank()) {
                    context.showToast(shellCardCommandRequiredToast)
                    return@OsPageOverlayEditorActions
                }
                val draft = overlayState.shellCommandCardDraft
                osPageViewModel.saveShellCommandCardEdit(
                    cardId = targetId,
                    title = draft.title,
                    subtitle = draft.subtitle,
                    command = draft.command,
                )
            },
            onOpenActivitySuggestionSheet = { target ->
                osPageViewModel.openActivitySuggestionSheet(target)
            },
            onDeleteActivityCard = {
                val targetId = overlayState.editingActivityShortcutCardId.orEmpty().trim()
                if (targetId.isBlank()) {
                    overlayState.onShowActivityShortcutEditorChange(false)
                    overlayState.onShowActivityCardDeleteConfirmChange(false)
                    return@OsPageOverlayEditorActions
                }
                overlayState.onShowActivityCardDeleteConfirmChange(true)
            },
            onDismissActivityEditor = {
                overlayState.onShowActivityShortcutEditorChange(false)
                osPageViewModel.dismissActivitySuggestionSheet()
                overlayState.onShowActivityCardDeleteConfirmChange(false)
                overlayState.onEditingActivityShortcutBuiltInChange(false)
            },
            onDismissActivityEditorFinished = {
                if (overlayState.showActivityShortcutEditor) {
                    return@OsPageOverlayEditorActions
                }
                overlayState.onActivityCardEditModeChange(OsActivityCardEditMode.Edit)
                overlayState.onEditingActivityShortcutCardIdChange(null)
                overlayState.onEditingActivityShortcutBuiltInChange(false)
                overlayState.onActivityShortcutDraftChange(
                    createDefaultActivityShortcutDraft(googleSystemServiceDefaults),
                )
                osPageViewModel.resetActivitySuggestionQueries()
                osPageViewModel.dismissActivitySuggestionSheet()
                overlayState.onShowActivityCardDeleteConfirmChange(false)
            },
            onSaveActivityEditor = {
                osPageViewModel.saveActivityShortcutCard(
                    editMode = overlayState.activityCardEditMode,
                    editingCardId = overlayState.editingActivityShortcutCardId,
                    draft = overlayState.activityShortcutDraft,
                    defaults = googleSystemServiceDefaults,
                )
            },
            onApplySuggestion = { suggestion ->
                overlayState.onActivityShortcutDraftChange(
                    applyGoogleSystemServiceSuggestion(
                        draft = overlayState.activityShortcutDraft,
                        target = activitySuggestionTarget,
                        item = suggestion,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags,
                    ),
                )
                osPageViewModel.dismissActivitySuggestionSheet()
            },
            onApplyExplicitActionRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    overlayState.activityShortcutDraft.copy(intentAction = Intent.ACTION_VIEW),
                )
            },
            onApplyImplicitActionRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    applyShortcutImplicitDefaults(
                        draft = overlayState.activityShortcutDraft,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags,
                    ),
                )
            },
            onApplyExplicitCategoryRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    overlayState.activityShortcutDraft.copy(intentCategory = ""),
                )
            },
            onApplyImplicitCategoryRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    applyShortcutImplicitDefaults(
                        draft = overlayState.activityShortcutDraft,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags,
                    ),
                )
            },
            onDismissShellCardDeleteConfirm = {
                overlayState.onShowShellCardDeleteConfirmChange(false)
            },
            onConfirmShellCardDelete = {
                val targetId = overlayState.editingShellCommandCardId.orEmpty().trim()
                overlayState.onShowShellCardDeleteConfirmChange(false)
                if (targetId.isBlank()) return@OsPageOverlayEditorActions
                osPageViewModel.deleteShellCommandCard(targetId)
            },
            onDismissActivityCardDeleteConfirm = {
                overlayState.onShowActivityCardDeleteConfirmChange(false)
            },
            onConfirmActivityCardDelete = {
                val targetId = overlayState.editingActivityShortcutCardId.orEmpty().trim()
                overlayState.onShowActivityCardDeleteConfirmChange(false)
                if (targetId.isBlank()) return@OsPageOverlayEditorActions
                osPageViewModel.deleteActivityShortcutCard(
                    cardId = targetId,
                    defaults = googleSystemServiceDefaults,
                )
            },
        )
    }
}
