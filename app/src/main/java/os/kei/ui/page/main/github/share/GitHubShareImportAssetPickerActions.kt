@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.testing.KeiOsTestTags

@Composable
internal fun GitHubShareImportAssetPickerActions(
    selectedAsset: GitHubReleaseAssetFile?,
    managedInstallProgress: GitHubShareImportManagedInstallProgress?,
    onCancel: () -> Unit,
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit,
) {
    val managedInstallRunning = managedInstallProgress.isRunningInstallPhase
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(KeiOsTestTags.GitHubShareImportCancel),
            text = stringResource(R.string.common_cancel),
            leadingIcon = appLucideCloseIcon(),
            containerColor = GitHubShareImportActionStyle.cancelContainerColor,
            variant = GitHubShareImportActionStyle.cancelVariant,
            onClick = onCancel,
        )
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(KeiOsTestTags.GitHubShareImportConfirm),
            text = gitHubShareImportConfirmLabel(managedInstallProgress),
            leadingIcon = appLucidePackageIcon(),
            containerColor = GitHubStatusPalette.Active,
            onClick = {
                selectedAsset?.let(onConfirmImport)
            },
            enabled = selectedAsset != null && !managedInstallRunning,
        )
    }
}

@Composable
private fun gitHubShareImportConfirmLabel(managedInstallProgress: GitHubShareImportManagedInstallProgress?): String =
    when {
        managedInstallProgress.isRunningInstallPhase -> {
            stringResource(R.string.common_processing)
        }

        managedInstallProgress?.phase == GitHubShareImportPhase.InstallReady -> {
            stringResource(R.string.github_share_import_notify_action_continue_install)
        }

        else -> {
            stringResource(R.string.github_share_import_dialog_action_confirm)
        }
    }
