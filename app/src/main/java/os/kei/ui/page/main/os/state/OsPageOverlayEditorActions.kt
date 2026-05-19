package os.kei.ui.page.main.os.state

import android.content.Context
import android.content.Intent
import os.kei.core.ext.showToast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.R
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionItem
import os.kei.ui.page.main.os.shortcut.applyGoogleSystemServiceSuggestion
import os.kei.ui.page.main.os.shortcut.applyShortcutImplicitDefaults
import os.kei.ui.page.main.os.shortcut.newOsActivityShortcutCardId
import os.kei.ui.page.main.os.shortcut.normalizeActivityShortcutConfig

internal data class OsPageOverlayEditorActions(
    val onDeleteShellCommandCard: () -> Unit,
    val onDismissShellCommandCardEditor: () -> Unit,
    val onSaveShellCommandCard: () -> Unit,
    val onOpenActivitySuggestionSheet: (ShortcutSuggestionField) -> Unit,
    val onDeleteActivityCard: () -> Unit,
    val onDismissActivityEditor: () -> Unit,
    val onSaveActivityEditor: () -> Unit,
    val onApplySuggestion: (ShortcutSuggestionItem) -> Unit,
    val onApplyExplicitActionRecommendation: () -> Unit,
    val onApplyImplicitActionRecommendation: () -> Unit,
    val onApplyExplicitCategoryRecommendation: () -> Unit,
    val onApplyImplicitCategoryRecommendation: () -> Unit,
    val onDismissShellCardDeleteConfirm: () -> Unit,
    val onConfirmShellCardDelete: () -> Unit,
    val onDismissActivityCardDeleteConfirm: () -> Unit,
    val onConfirmActivityCardDelete: () -> Unit
)

@Composable
internal fun rememberOsPageOverlayEditorActions(
    context: Context,
    overlayState: OsPageOverlayState,
    activityShortcutCards: List<OsActivityShortcutCard>,
    onActivityShortcutCardsChange: (List<OsActivityShortcutCard>) -> Unit,
    onRemoveActivityCardExpanded: (String) -> Unit,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    googleSystemServiceDefaultIntentFlags: String,
    onShellCommandCardsChange: (List<OsShellCommandCard>) -> Unit,
    onRemoveShellCommandCardExpanded: (String) -> Unit,
    shellCardCommandRequiredToast: String,
    shellCardSavedToast: String,
    shellCardDeletedToast: String,
    activityCardDeletedToast: String
): OsPageOverlayEditorActions {
    return remember(
        context,
        overlayState,
        activityShortcutCards,
        onActivityShortcutCardsChange,
        onRemoveActivityCardExpanded,
        googleSystemServiceDefaults,
        googleSystemServiceDefaultIntentFlags,
        onShellCommandCardsChange,
        onRemoveShellCommandCardExpanded,
        shellCardCommandRequiredToast,
        shellCardSavedToast,
        shellCardDeletedToast,
        activityCardDeletedToast
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
            onSaveShellCommandCard = {
                val targetId = overlayState.editingShellCommandCardId.orEmpty().trim()
                if (targetId.isBlank()) {
                    context.showToast(shellCardCommandRequiredToast)
                    return@OsPageOverlayEditorActions
                }
                val updated = OsShellCommandCardStore.updateCard(
                    cardId = targetId,
                    title = overlayState.shellCommandCardDraft.title,
                    subtitle = overlayState.shellCommandCardDraft.subtitle,
                    command = overlayState.shellCommandCardDraft.command
                )
                if (updated == null) {
                    context.showToast(shellCardCommandRequiredToast)
                    return@OsPageOverlayEditorActions
                }
                onShellCommandCardsChange(OsShellCommandCardStore.loadCards())
                context.showToast(shellCardSavedToast)
                overlayState.onShowShellCommandCardEditorChange(false)
                overlayState.onShowShellCardDeleteConfirmChange(false)
            },
            onOpenActivitySuggestionSheet = { target ->
                overlayState.onGoogleSystemServiceSuggestionTargetChange(target)
                when (target) {
                    ShortcutSuggestionField.PackageName -> {
                        overlayState.onGoogleSystemServicePackageSuggestionQueryChange("")
                    }

                    ShortcutSuggestionField.ClassName -> {
                        overlayState.onGoogleSystemServiceClassSuggestionQueryChange("")
                    }

                    else -> Unit
                }
                overlayState.onShowActivitySuggestionSheetChange(true)
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
                overlayState.onShowActivityCardDeleteConfirmChange(false)
                overlayState.onEditingActivityShortcutBuiltInChange(false)
            },
            onSaveActivityEditor = {
                val normalized = normalizeActivityShortcutConfig(
                    config = overlayState.activityShortcutDraft,
                    defaults = googleSystemServiceDefaults
                )
                val updatedCards = if (overlayState.activityCardEditMode == OsActivityCardEditMode.Add) {
                    activityShortcutCards + OsActivityShortcutCard(
                        id = newOsActivityShortcutCardId(),
                        visible = true,
                        isBuiltInSample = false,
                        config = normalized
                    )
                } else {
                    val targetId = overlayState.editingActivityShortcutCardId
                    if (targetId.isNullOrBlank()) {
                        activityShortcutCards + OsActivityShortcutCard(
                            id = newOsActivityShortcutCardId(),
                            visible = true,
                            isBuiltInSample = false,
                            config = normalized
                        )
                    } else {
                        activityShortcutCards.map { card ->
                            if (card.id == targetId) card.copy(config = normalized) else card
                        }
                    }
                }
                onActivityShortcutCardsChange(updatedCards)
                OsActivityShortcutCardStore.saveCards(
                    cards = updatedCards,
                    defaults = googleSystemServiceDefaults
                )
                context.showToast(R.string.os_google_system_service_toast_saved)
                overlayState.onShowActivityShortcutEditorChange(false)
                overlayState.onShowActivityCardDeleteConfirmChange(false)
                overlayState.onEditingActivityShortcutBuiltInChange(false)
            },
            onApplySuggestion = { suggestion ->
                overlayState.onActivityShortcutDraftChange(
                    applyGoogleSystemServiceSuggestion(
                        draft = overlayState.activityShortcutDraft,
                        target = overlayState.googleSystemServiceSuggestionTarget,
                        item = suggestion,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags
                    )
                )
                overlayState.onShowActivitySuggestionSheetChange(false)
            },
            onApplyExplicitActionRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    overlayState.activityShortcutDraft.copy(intentAction = Intent.ACTION_VIEW)
                )
            },
            onApplyImplicitActionRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    applyShortcutImplicitDefaults(
                        draft = overlayState.activityShortcutDraft,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags
                    )
                )
            },
            onApplyExplicitCategoryRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    overlayState.activityShortcutDraft.copy(intentCategory = "")
                )
            },
            onApplyImplicitCategoryRecommendation = {
                overlayState.onActivityShortcutDraftChange(
                    applyShortcutImplicitDefaults(
                        draft = overlayState.activityShortcutDraft,
                        defaultIntentFlags = googleSystemServiceDefaultIntentFlags
                    )
                )
            },
            onDismissShellCardDeleteConfirm = {
                overlayState.onShowShellCardDeleteConfirmChange(false)
            },
            onConfirmShellCardDelete = {
                val targetId = overlayState.editingShellCommandCardId.orEmpty().trim()
                overlayState.onShowShellCardDeleteConfirmChange(false)
                if (targetId.isBlank()) return@OsPageOverlayEditorActions
                onShellCommandCardsChange(OsShellCommandCardStore.deleteCard(targetId))
                onRemoveShellCommandCardExpanded(targetId)
                overlayState.onEditingShellCommandCardIdChange(null)
                overlayState.onShowShellCommandCardEditorChange(false)
                context.showToast(shellCardDeletedToast)
            },
            onDismissActivityCardDeleteConfirm = {
                overlayState.onShowActivityCardDeleteConfirmChange(false)
            },
            onConfirmActivityCardDelete = {
                val targetId = overlayState.editingActivityShortcutCardId.orEmpty().trim()
                overlayState.onShowActivityCardDeleteConfirmChange(false)
                if (targetId.isBlank()) return@OsPageOverlayEditorActions
                val updatedCards = activityShortcutCards.filterNot { card -> card.id == targetId }
                onActivityShortcutCardsChange(updatedCards)
                onRemoveActivityCardExpanded(targetId)
                OsActivityShortcutCardStore.saveCards(
                    cards = updatedCards,
                    defaults = googleSystemServiceDefaults
                )
                overlayState.onEditingActivityShortcutCardIdChange(null)
                overlayState.onShowActivityShortcutEditorChange(false)
                overlayState.onShowActivitySuggestionSheetChange(false)
                overlayState.onEditingActivityShortcutBuiltInChange(false)
                context.showToast(activityCardDeletedToast)
            }
        )
    }
}
