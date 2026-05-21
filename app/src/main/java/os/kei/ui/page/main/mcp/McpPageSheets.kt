@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import os.kei.ui.page.main.mcp.dialog.McpResetConfigDialog
import os.kei.ui.page.main.mcp.dialog.McpResetTokenDialog
import os.kei.ui.page.main.mcp.sheet.McpEditServiceSheet

@Composable
internal fun McpPageSheets(
    pageUiState: McpPageUiState,
    backdrops: MainPageBackdropSet,
    serverNameFieldWidth: Dp,
    portFieldWidth: Dp,
    serviceDraftChanged: Boolean,
    actions: McpPageActions,
) {
    McpEditServiceSheet(
        show = pageUiState.showEditSheet,
        backdrop = backdrops.sheet,
        serverName = pageUiState.serverName,
        onServerNameChange = actions.onServerNameChange,
        serverNameFieldWidth = serverNameFieldWidth,
        portText = pageUiState.portText,
        onPortTextChange = actions.onPortTextChange,
        portFieldWidth = portFieldWidth,
        allowExternal = pageUiState.allowExternal,
        hasUnsavedChanges = serviceDraftChanged,
        onAllowExternalChange = actions.onAllowExternalChange,
        onSave = actions.onSaveServiceConfig,
        onDismissRequest = actions.onDismissEditSheet,
        onShowResetTokenConfirm = actions.onShowResetTokenConfirm,
    )

    McpResetConfigDialog(
        show = pageUiState.showResetConfigConfirm,
        onConfirm = actions.onResetConfig,
        onDismissRequest = actions.onDismissResetConfigConfirm,
    )

    McpResetTokenDialog(
        show = pageUiState.showResetTokenConfirm,
        onConfirm = actions.onResetToken,
        onDismissRequest = actions.onDismissResetTokenConfirm,
    )
}
