package os.kei.ui.page.main.os.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel

internal data class OsPageOverlayTransferActions(
    val onExportAllActivityCards: () -> Unit,
    val onImportAllActivityCards: () -> Unit,
    val onExportAllShellCards: () -> Unit,
    val onImportAllShellCards: () -> Unit
)

@Composable
internal fun rememberOsPageOverlayTransferActions(
    context: Context,
    osPageViewModel: OsPageViewModel,
    overlayState: OsPageOverlayState,
    cardTransferState: OsPageCardTransferState,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig
): OsPageOverlayTransferActions {
    return remember(
        context,
        osPageViewModel,
        overlayState,
        cardTransferState,
        googleSystemServiceDefaults
    ) {
        OsPageOverlayTransferActions(
            onExportAllActivityCards = {
                overlayState.onCardTransferInProgressChange(true)
                osPageViewModel.prepareActivityCardsExport(
                    defaults = googleSystemServiceDefaults,
                    onReady = { payload ->
                        overlayState.onPendingExportContentChange(payload)
                        overlayState.onCardTransferInProgressChange(false)
                        cardTransferState.exportLauncher.launch("keios-os-activity-cards.json")
                    },
                    onFailure = { error ->
                        overlayState.onCardTransferInProgressChange(false)
                        context.showToast(
                            context.getString(
                                R.string.common_export_failed_with_reason,
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    },
                )
            },
            onImportAllActivityCards = {
                overlayState.onPendingCardImportPreviewChange(null)
                overlayState.onPendingImportTargetChange(OsCardImportTarget.Activity)
                overlayState.onCardTransferInProgressChange(true)
                cardTransferState.importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onExportAllShellCards = {
                overlayState.onCardTransferInProgressChange(true)
                osPageViewModel.prepareShellCardsExport(
                    onReady = { payload ->
                        overlayState.onPendingExportContentChange(payload)
                        overlayState.onCardTransferInProgressChange(false)
                        cardTransferState.exportLauncher.launch("keios-os-shell-cards.json")
                    },
                    onFailure = { error ->
                        overlayState.onCardTransferInProgressChange(false)
                        context.showToast(
                            context.getString(
                                R.string.common_export_failed_with_reason,
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    },
                )
            },
            onImportAllShellCards = {
                overlayState.onPendingCardImportPreviewChange(null)
                overlayState.onPendingImportTargetChange(OsCardImportTarget.Shell)
                overlayState.onCardTransferInProgressChange(true)
                cardTransferState.importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }
}
