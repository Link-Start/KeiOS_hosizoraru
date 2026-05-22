@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet

@Composable
internal fun GitHubShareImportSheet(
    preview: GitHubShareImportPreview?,
    resolving: Boolean,
    phase: GitHubShareImportPhase,
    managedInstallProgress: GitHubShareImportManagedInstallProgress? = null,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit,
) {
    val showSheet = resolving || preview != null
    val managedInstallRunning = managedInstallProgress.isRunningInstallPhase
    SnapshotWindowBottomSheet(
        show = showSheet,
        title = stringResource(R.string.github_share_import_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = !resolving && !managedInstallRunning,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = showSheet)
        when {
            resolving -> {
                GitHubShareImportResolvingContent(phase)
            }

            preview != null -> {
                GitHubShareImportAssetPickerContent(
                    preview = preview,
                    phase = phase,
                    managedInstallProgress = managedInstallProgress,
                    onCancel = onCancel,
                    onConfirmImport = onConfirmImport,
                )
            }
        }
    }
}
