package os.kei.ui.page.main.os.state

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.transfer.localizedOsCardImportMessage

internal data class OsPageCardTransferState(
    val exportLauncher: ActivityResultLauncher<String>,
    val importLauncher: ActivityResultLauncher<Array<String>>,
    val confirmImport: () -> Unit,
)

@Composable
internal fun rememberOsPageCardTransferState(
    context: Context,
    osPageViewModel: OsPageViewModel,
    overlayState: OsPageOverlayState,
    activityCardExpanded: SnapshotStateMap<String, Boolean>,
    shellCommandCardExpanded: SnapshotStateMap<String, Boolean>,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    cardImportFailedWithReason: String,
    exportSuccessText: String,
): OsPageCardTransferState {
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            val content = overlayState.pendingExportContent
            if (uri == null || content.isNullOrBlank()) return@rememberLauncherForActivityResult
            osPageViewModel.writeCardExportContent(
                contentResolver = context.contentResolver,
                uri = uri,
                content = content,
                onSuccess = {
                    context.showToast(exportSuccessText)
                },
                onFailure = {
                    context.showToast(
                        context.getString(
                            R.string.common_export_failed_with_reason,
                            it.javaClass.simpleName,
                        ),
                    )
                },
            )
        }

    fun handleActivityImportResult(result: OsActivityCardImportMergeResult) {
        val validIds = result.cards.mapTo(mutableSetOf()) { it.id }
        activityCardExpanded.keys.retainAll(validIds)
        if (!validIds.contains(overlayState.editingActivityShortcutCardId.orEmpty())) {
            overlayState.onShowActivityShortcutEditorChange(false)
            overlayState.onShowActivityCardDeleteConfirmChange(false)
            overlayState.onEditingActivityShortcutCardIdChange(null)
        }
        context.showToast(
            context.getString(
                R.string.os_activity_card_toast_imported_summary,
                result.addedCount,
                result.updatedCount,
                result.unchangedCount,
            )
        )
    }

    fun handleShellImportResult(result: OsShellCardImportMergeResult) {
        val validIds = result.cards.mapTo(mutableSetOf()) { it.id }
        shellCommandCardExpanded.keys.retainAll(validIds)
        if (!validIds.contains(overlayState.editingShellCommandCardId.orEmpty())) {
            overlayState.onShowShellCommandCardEditorChange(false)
            overlayState.onShowShellCardDeleteConfirmChange(false)
            overlayState.onEditingShellCommandCardIdChange(null)
        }
        context.showToast(
            context.getString(
                R.string.os_shell_card_toast_imported_summary,
                result.addedCount,
                result.updatedCount,
                result.unchangedCount,
            )
        )
    }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            val target = overlayState.pendingImportTarget
            overlayState.onPendingImportTargetChange(null)
            if (uri == null || target == null) {
                overlayState.onCardTransferInProgressChange(false)
                return@rememberLauncherForActivityResult
            }
            osPageViewModel.requestCardImportPreview(
                contentResolver = context.contentResolver,
                uri = uri,
                target = target,
                googleSystemServiceDefaults = googleSystemServiceDefaults,
                googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                onPreview = { preview ->
                    overlayState.onPendingCardImportPreviewChange(preview)
                },
                onFailure = { error ->
                    context.showToast(
                        String.format(
                            cardImportFailedWithReason,
                            error.localizedOsCardImportMessage(context),
                        ),
                    )
                },
                onComplete = {
                    overlayState.onCardTransferInProgressChange(false)
                },
            )
        }

    val confirmPendingImport: () -> Unit = confirmPendingImport@{
        val preview = overlayState.pendingCardImportPreview ?: return@confirmPendingImport
        if (!preview.canImport || overlayState.cardTransferInProgress) {
            overlayState.onPendingCardImportPreviewChange(null)
            return@confirmPendingImport
        }
        overlayState.onCardTransferInProgressChange(true)
        osPageViewModel.confirmCardImport(
            preview = preview,
            googleSystemServiceDefaults = googleSystemServiceDefaults,
            googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
            onActivityImported = ::handleActivityImportResult,
            onShellImported = ::handleShellImportResult,
            onFailure = { error ->
                context.showToast(
                    String.format(
                        cardImportFailedWithReason,
                        error.localizedOsCardImportMessage(context),
                    ),
                )
            },
            onComplete = {
                overlayState.onPendingCardImportPreviewChange(null)
                overlayState.onCardTransferInProgressChange(false)
            },
        )
    }

    return OsPageCardTransferState(
        exportLauncher = exportLauncher,
        importLauncher = importLauncher,
        confirmImport = confirmPendingImport,
    )
}
