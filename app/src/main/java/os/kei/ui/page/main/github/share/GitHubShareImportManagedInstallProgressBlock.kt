@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar

@Composable
internal fun ManagedInstallProgressBlock(progress: GitHubShareImportManagedInstallProgress?) {
    progress ?: return
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(4.dp))
    if (progress.assetName.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_pending_label_asset),
            value = progress.assetName,
        )
    }
    val appDisplayName = progress.appDisplayName
    if (
        appDisplayName.isNotBlank() &&
        !appDisplayName.equals(progress.packageName, ignoreCase = true) &&
        !appDisplayName.equals(progress.assetName, ignoreCase = true)
    ) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_attach_dialog_label_app),
            value = appDisplayName,
        )
    }
    if (progress.packageName.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_attach_dialog_label_package),
            value = progress.packageName,
        )
    }
    val versionLabel = managedInstallVersionLabel(progress)
    if (versionLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_version),
            value = versionLabel,
        )
    }
    val abiLabel = managedInstallAbiLabel(progress)
    if (abiLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_abi),
            value = abiLabel,
        )
    }
    val sdkLabel = managedInstallSdkLabel(progress)
    if (sdkLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_sdk),
            value = sdkLabel,
        )
    }
    val showDownloadText =
        progress.phase == GitHubShareImportPhase.InstallDownloading || progress.downloadedBytes > 0L
    if (showDownloadText) {
        val progressText =
            remember(
                progress.downloadedBytes,
                progress.totalBytes,
            ) {
                formatManagedInstallDownloadProgress(context, progress)
            }
        if (progressText.isNotBlank()) {
            ShareImportCompactInfoRow(
                key = stringResource(R.string.github_share_import_dialog_label_download),
                value = progressText,
            )
        }
    }
    if (progress.hasKnownDownloadProgress) {
        val percentText =
            stringResource(
                R.string.github_refresh_progress_percent,
                progress.boundedProgressPercent,
            )
        LiquidLinearProgressBar(
            progress = { progress.progressFraction },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            activeColor = progress.phase.color,
            contentDescription =
                stringResource(
                    R.string.common_progress_with_value,
                    percentText,
                ),
        )
    }
}

@Composable
private fun managedInstallVersionLabel(progress: GitHubShareImportManagedInstallProgress): String =
    shareImportVersionLabel(
        versionName = progress.versionName,
        versionCode = progress.versionCode,
    )

@Composable
private fun managedInstallAbiLabel(progress: GitHubShareImportManagedInstallProgress): String {
    val abis =
        progress.nativeAbis
            .map { abi -> abi.trim() }
            .filter { abi -> abi.isNotBlank() }
            .distinct()
    if (abis.isNotEmpty()) return abis.joinToString(", ")
    val inspectionReady =
        progress.packageName.isNotBlank() &&
            progress.phase in
            setOf(
                GitHubShareImportPhase.InstallReady,
                GitHubShareImportPhase.InstallCommitting,
            )
    return if (inspectionReady) {
        stringResource(R.string.github_share_import_dialog_abi_universal)
    } else {
        ""
    }
}

@Composable
private fun managedInstallSdkLabel(progress: GitHubShareImportManagedInstallProgress): String {
    val targetSdk = progress.targetSdk.trim()
    val minSdk = progress.minSdk.trim()
    return when {
        targetSdk.isNotBlank() && minSdk.isNotBlank() -> {
            stringResource(
                R.string.github_share_import_dialog_sdk_value,
                targetSdk,
                minSdk,
            )
        }

        targetSdk.isNotBlank() -> {
            stringResource(
                R.string.github_share_import_dialog_sdk_target_value,
                targetSdk,
            )
        }

        minSdk.isNotBlank() -> {
            stringResource(
                R.string.github_share_import_dialog_sdk_min_value,
                minSdk,
            )
        }

        else -> {
            ""
        }
    }
}

private fun formatManagedInstallDownloadProgress(
    context: android.content.Context,
    progress: GitHubShareImportManagedInstallProgress,
): String {
    val downloadedBytes = progress.downloadedBytes.coerceAtLeast(0L)
    if (downloadedBytes <= 0L && progress.totalBytes <= 0L) return ""
    val downloaded =
        android.text.format.Formatter
            .formatFileSize(context, downloadedBytes)
    if (progress.totalBytes > 0L) {
        return context.getString(
            R.string.github_share_import_notify_download_progress_known,
            downloaded,
            android.text.format.Formatter
                .formatFileSize(context, progress.totalBytes),
        )
    }
    return context.getString(
        R.string.github_share_import_notify_download_progress_unknown,
        downloaded,
    )
}
