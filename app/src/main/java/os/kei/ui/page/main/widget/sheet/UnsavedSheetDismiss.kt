package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Stable
internal class UnsavedSheetDismissHandler(
    val showConfirmDialog: Boolean,
    val requestDismiss: () -> Unit,
    val keepEditing: () -> Unit,
    val discardChanges: () -> Unit
)

@Composable
internal fun rememberUnsavedSheetDismissHandler(
    hasUnsavedChanges: Boolean,
    onDismissRequest: () -> Unit
): UnsavedSheetDismissHandler {
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val currentHasUnsavedChanges by rememberUpdatedState(hasUnsavedChanges)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    return remember(showConfirmDialog) {
        UnsavedSheetDismissHandler(
            showConfirmDialog = showConfirmDialog,
            requestDismiss = {
                if (currentHasUnsavedChanges) {
                    showConfirmDialog = true
                } else {
                    currentOnDismissRequest()
                }
            },
            keepEditing = { showConfirmDialog = false },
            discardChanges = {
                showConfirmDialog = false
                currentOnDismissRequest()
            }
        )
    }
}

@Composable
internal fun UnsavedSheetDismissConfirmDialog(
    show: Boolean,
    onKeepEditing: () -> Unit,
    onDiscardChanges: () -> Unit
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.common_unsaved_changes_title),
        summary = stringResource(R.string.common_unsaved_changes_summary),
        onDismissRequest = onKeepEditing
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_keep_editing),
                    containerColor = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.SheetPrimaryAction,
                    onClick = onKeepEditing
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_discard_changes),
                    textColor = MiuixTheme.colorScheme.error,
                    variant = GlassVariant.SheetDangerAction,
                    onClick = onDiscardChanges
                )
            }
        }
    }
}
