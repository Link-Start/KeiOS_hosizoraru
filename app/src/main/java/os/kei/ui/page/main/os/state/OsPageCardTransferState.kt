package os.kei.ui.page.main.os.state

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

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
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
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
            )
        }

    val confirmPendingImport: () -> Unit =
        remember(
            overlayState,
            osPageViewModel,
            googleSystemServiceDefaults,
            googleSettingsBuiltInSampleDefaults,
            builtInActivityShortcutCards,
        ) {
            confirmPendingImport@{
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
                )
            }
        }

    return remember(exportLauncher, importLauncher, confirmPendingImport) {
        OsPageCardTransferState(
            exportLauncher = exportLauncher,
            importLauncher = importLauncher,
            confirmImport = confirmPendingImport,
        )
    }
}
