package os.kei.ui.page.main.os.components

import androidx.compose.runtime.Composable
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.isCardVisible
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionItem
import os.kei.ui.page.main.os.transfer.OsCardImportPreview

@Composable
internal fun OsPageOverlaySheets(
    showCardManager: Boolean,
    visibleCardsTitle: String,
    sheetBackdrop: LayerBackdrop,
    cardsHintText: String,
    visibleCards: Set<OsSectionCard>,
    onDismissCardManager: () -> Unit,
    onCardVisibilityChange: (OsSectionCard, Boolean) -> Unit,
    showActivityVisibilityManager: Boolean,
    visibleActivitiesTitle: String,
    activityHintText: String,
    activityShortcutCards: List<OsActivityShortcutCard>,
    defaultActivityCardTitle: String,
    cardTransferInProgress: Boolean,
    onExportAllActivityCards: () -> Unit,
    onImportAllActivityCards: () -> Unit,
    onDismissActivityVisibilityManager: () -> Unit,
    onActivityCardVisibilityChange: (String, Boolean) -> Unit,
    showShellCardVisibilityManager: Boolean,
    visibleShellCardsTitle: String,
    visibleShellCardsDesc: String,
    shellRunnerVisible: Boolean,
    onShellRunnerVisibilityChange: (Boolean) -> Unit,
    shellCommandCards: List<OsShellCommandCard>,
    onExportAllShellCards: () -> Unit,
    onImportAllShellCards: () -> Unit,
    onDismissShellVisibilityManager: () -> Unit,
    onShellCommandCardVisibilityChange: (String, Boolean) -> Unit,
    showShellCommandCardEditor: Boolean,
    editShellCommandCardTitle: String,
    shellCommandCardDraft: OsShellCommandCard,
    onShellCommandCardDraftChange: (OsShellCommandCard) -> Unit,
    showShellCardDeleteAction: Boolean,
    shellCommandCardHasUnsavedChanges: Boolean,
    onDeleteShellCommandCard: () -> Unit,
    onDismissShellCommandCardEditor: () -> Unit,
    onDismissShellCommandCardEditorFinished: () -> Unit,
    onSaveShellCommandCard: () -> Unit,
    showActivityShortcutEditor: Boolean,
    activityEditorTitle: String,
    activityShortcutDraft: OsGoogleSystemServiceConfig,
    onActivityShortcutDraftChange: (OsGoogleSystemServiceConfig) -> Unit,
    onOpenActivitySuggestionSheet: (ShortcutSuggestionField) -> Unit,
    showBuiltInActivityCardBadge: Boolean,
    showDeleteActivityAction: Boolean,
    activityShortcutHasUnsavedChanges: Boolean,
    onDeleteActivityCard: () -> Unit,
    onDismissActivityEditor: () -> Unit,
    onDismissActivityEditorFinished: () -> Unit,
    onSaveActivityEditor: () -> Unit,
    showActivitySuggestionSheet: Boolean,
    suggestionTarget: ShortcutSuggestionField,
    packageSuggestions: List<ShortcutInstalledAppOption>,
    packageSuggestionsLoading: Boolean,
    packageSuggestionQuery: String,
    onPackageSuggestionQueryChange: (String) -> Unit,
    classSuggestions: List<ShortcutActivityClassOption>,
    classSuggestionsLoading: Boolean,
    classSuggestionQuery: String,
    onClassSuggestionQueryChange: (String) -> Unit,
    noMatchedResultsText: String,
    onDismissSuggestionSheet: () -> Unit,
    onApplySuggestion: (ShortcutSuggestionItem) -> Unit,
    onApplyExplicitActionRecommendation: () -> Unit,
    onApplyImplicitActionRecommendation: () -> Unit,
    onApplyExplicitCategoryRecommendation: () -> Unit,
    onApplyImplicitCategoryRecommendation: () -> Unit,
    showShellCardDeleteConfirm: Boolean,
    shellCardDeleteDialogTitle: String,
    shellCardDeleteDialogSummary: String,
    onDismissShellCardDeleteConfirm: () -> Unit,
    onConfirmShellCardDelete: () -> Unit,
    showActivityCardDeleteConfirm: Boolean,
    activityCardDeleteDialogTitle: String,
    activityCardDeleteDialogSummary: String,
    onDismissActivityCardDeleteConfirm: () -> Unit,
    onConfirmActivityCardDelete: () -> Unit,
    pendingCardImportPreview: OsCardImportPreview?,
    onDismissCardImportPreview: () -> Unit,
    onCancelCardImportPreview: () -> Unit,
    onConfirmCardImportPreview: () -> Unit
) {
    OsCardVisibilityManagerSheet(
        show = showCardManager,
        title = visibleCardsTitle,
        sheetBackdrop = sheetBackdrop,
        cardsHintText = cardsHintText,
        onDismissRequest = onDismissCardManager,
        isCardVisible = { card -> isCardVisible(visibleCards, card) },
        onCardVisibilityChange = onCardVisibilityChange
    )
    OsActivityVisibilityManagerSheet(
        show = showActivityVisibilityManager,
        title = visibleActivitiesTitle,
        sheetBackdrop = sheetBackdrop,
        activityHintText = activityHintText,
        cards = activityShortcutCards,
        defaultCardTitle = defaultActivityCardTitle,
        transferInProgress = cardTransferInProgress,
        onExportAllCards = onExportAllActivityCards,
        onImportAllCards = onImportAllActivityCards,
        onDismissRequest = onDismissActivityVisibilityManager,
        onCardVisibilityChange = onActivityCardVisibilityChange
    )
    OsShellCommandVisibilityManagerSheet(
        show = showShellCardVisibilityManager,
        title = visibleShellCardsTitle,
        sheetBackdrop = sheetBackdrop,
        shellHintText = visibleShellCardsDesc,
        shellRunnerVisible = shellRunnerVisible,
        onShellRunnerVisibilityChange = onShellRunnerVisibilityChange,
        cards = shellCommandCards,
        transferInProgress = cardTransferInProgress,
        onExportAllCards = onExportAllShellCards,
        onImportAllCards = onImportAllShellCards,
        onDismissRequest = onDismissShellVisibilityManager,
        onCardVisibilityChange = onShellCommandCardVisibilityChange
    )
    OsShellCommandCardEditorSheet(
        show = showShellCommandCardEditor,
        title = editShellCommandCardTitle,
        sheetBackdrop = sheetBackdrop,
        draft = shellCommandCardDraft,
        onDraftChange = onShellCommandCardDraftChange,
        showDeleteAction = showShellCardDeleteAction,
        hasUnsavedChanges = shellCommandCardHasUnsavedChanges,
        onDelete = onDeleteShellCommandCard,
        onDismissRequest = onDismissShellCommandCardEditor,
        onDismissFinished = onDismissShellCommandCardEditorFinished,
        onSave = onSaveShellCommandCard
    )
    OsActivityShortcutEditorHost(
        showEditor = showActivityShortcutEditor,
        editorTitle = activityEditorTitle,
        sheetBackdrop = sheetBackdrop,
        draft = activityShortcutDraft,
        onDraftChange = onActivityShortcutDraftChange,
        onOpenSuggestionSheet = onOpenActivitySuggestionSheet,
        showBuiltInBadge = showBuiltInActivityCardBadge,
        showDeleteAction = showDeleteActivityAction,
        hasUnsavedChanges = activityShortcutHasUnsavedChanges,
        onDeleteEditor = onDeleteActivityCard,
        onDismissEditor = onDismissActivityEditor,
        onDismissEditorFinished = onDismissActivityEditorFinished,
        onSaveEditor = onSaveActivityEditor,
        showSuggestionSheet = showActivitySuggestionSheet,
        suggestionTarget = suggestionTarget,
        packageSuggestions = packageSuggestions,
        packageSuggestionsLoading = packageSuggestionsLoading,
        packageSuggestionQuery = packageSuggestionQuery,
        onPackageSuggestionQueryChange = onPackageSuggestionQueryChange,
        classSuggestions = classSuggestions,
        classSuggestionsLoading = classSuggestionsLoading,
        classSuggestionQuery = classSuggestionQuery,
        onClassSuggestionQueryChange = onClassSuggestionQueryChange,
        noMatchedResultsText = noMatchedResultsText,
        onDismissSuggestionSheet = onDismissSuggestionSheet,
        onApplySuggestion = onApplySuggestion,
        onApplyExplicitActionRecommendation = onApplyExplicitActionRecommendation,
        onApplyImplicitActionRecommendation = onApplyImplicitActionRecommendation,
        onApplyExplicitCategoryRecommendation = onApplyExplicitCategoryRecommendation,
        onApplyImplicitCategoryRecommendation = onApplyImplicitCategoryRecommendation
    )
    OsDeleteConfirmDialog(
        show = showShellCardDeleteConfirm,
        title = shellCardDeleteDialogTitle,
        summary = shellCardDeleteDialogSummary,
        onDismissRequest = onDismissShellCardDeleteConfirm,
        onConfirmDelete = onConfirmShellCardDelete
    )
    OsDeleteConfirmDialog(
        show = showActivityCardDeleteConfirm,
        title = activityCardDeleteDialogTitle,
        summary = activityCardDeleteDialogSummary,
        onDismissRequest = onDismissActivityCardDeleteConfirm,
        onConfirmDelete = onConfirmActivityCardDelete
    )
    OsImportPreviewDialog(
        preview = pendingCardImportPreview,
        importInProgress = cardTransferInProgress,
        onDismissRequest = onDismissCardImportPreview,
        onCancel = onCancelCardImportPreview,
        onConfirmImport = onConfirmCardImportPreview
    )
}
