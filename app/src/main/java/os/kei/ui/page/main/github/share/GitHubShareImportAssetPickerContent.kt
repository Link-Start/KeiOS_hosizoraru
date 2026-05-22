@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.status.StatusPill

@Composable
internal fun GitHubShareImportAssetPickerContent(
    preview: GitHubShareImportPreview,
    phase: GitHubShareImportPhase,
    managedInstallProgress: GitHubShareImportManagedInstallProgress?,
    onCancel: () -> Unit,
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit,
) {
    val pickerState = rememberGitHubShareImportAssetPickerState(preview)
    val managedInstallSessionActive = managedInstallProgress.hasActiveInstallSession
    SheetContentColumn(
        modifier = Modifier.shareImportSheetTags(),
        verticalSpacing = 10.dp,
    ) {
        GitHubShareImportPreviewSummaryCard(
            preview = preview,
            phase = phase,
            managedInstallProgress = managedInstallProgress,
        )
        SheetSectionTitle(stringResource(R.string.github_share_import_dialog_label_assets))
        SheetSectionCard(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalSpacing = 6.dp,
        ) {
            GitHubShareImportAssetPickerList(
                assets = preview.assets,
                supportedAbis = pickerState.supportedAbis,
                selectedIndex = pickerState.safeSelectedIndex,
                selectionEnabled = !managedInstallSessionActive,
                onSelect = pickerState::select,
            )
        }
        GitHubShareImportAssetPickerActions(
            selectedAsset = pickerState.selectedAsset,
            managedInstallProgress = managedInstallProgress,
            onCancel = onCancel,
            onConfirmImport = onConfirmImport,
        )
    }
}

@Composable
private fun GitHubShareImportPreviewSummaryCard(
    preview: GitHubShareImportPreview,
    phase: GitHubShareImportPhase,
    managedInstallProgress: GitHubShareImportManagedInstallProgress?,
) {
    SheetSectionCard(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalSpacing = 4.dp,
    ) {
        StatusPill(
            label = stringResource(phase.labelRes),
            color = phase.color,
        )
        ManagedInstallProgressBlock(managedInstallProgress)
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_project),
            value = compactShareImportProjectValue(preview),
        )
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_strategy),
            value = preview.strategyLabel,
        )
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_release),
            value = preview.releaseTag,
        )
    }
}
