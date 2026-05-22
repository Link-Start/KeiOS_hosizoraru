@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
internal fun shareImportVersionLabel(
    versionName: String,
    versionCode: String,
): String {
    val normalizedVersionName = versionName.trim()
    val normalizedVersionCode = versionCode.trim()
    return when {
        normalizedVersionName.isNotBlank() && normalizedVersionCode.isNotBlank() -> {
            stringResource(
                R.string.github_share_import_dialog_version_value,
                normalizedVersionName,
                normalizedVersionCode,
            )
        }

        normalizedVersionName.isNotBlank() -> {
            normalizedVersionName
        }

        normalizedVersionCode.isNotBlank() -> {
            normalizedVersionCode
        }

        else -> {
            ""
        }
    }
}

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

@Composable
internal fun GitHubShareImportPendingSheet(
    pending: GitHubPendingShareImportTrackRecord?,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = pending != null,
        title = stringResource(R.string.github_share_import_pending_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = false,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = pending != null)
        val pendingTrack = pending ?: return@SnapshotWindowBottomSheet
        val remainingMinutes = shareImportRemainingMinutes(pendingTrack.armedAtMillis)
        SheetContentColumn(
            modifier = Modifier.shareImportSheetTags(),
            verticalSpacing = 10.dp,
        ) {
            SheetDescriptionText(
                text =
                    stringResource(
                        if (pendingTrack.packageName.isNotBlank()) {
                            R.string.github_share_import_pending_dialog_summary_exact
                        } else {
                            R.string.github_share_import_pending_dialog_summary
                        },
                    ),
            )
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 6.dp,
            ) {
                StatusPill(
                    label =
                        stringResource(
                            R.string.github_share_import_pending_remaining_minutes,
                            remainingMinutes,
                        ),
                    color = GitHubStatusPalette.PreRelease,
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_pending_label_target),
                    value = "${pendingTrack.owner}/${pendingTrack.repo}",
                )
                if (pendingTrack.releaseTag.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_release),
                        value = pendingTrack.releaseTag,
                    )
                }
                if (pendingTrack.assetName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_asset),
                        value = pendingTrack.assetName,
                    )
                }
                if (pendingTrack.packageName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_attach_dialog_label_package),
                        value = pendingTrack.packageName,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLiquidDialogActionButton(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(KeiOsTestTags.GitHubShareImportPendingClose),
                    text = stringResource(R.string.common_close),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onClose,
                )
                AppLiquidDialogActionButton(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(KeiOsTestTags.GitHubShareImportPendingCancel),
                    text = stringResource(R.string.github_share_import_pending_action_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                    variant = GitHubShareImportActionStyle.cancelVariant,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
internal fun GitHubShareImportAttachConfirmSheet(
    candidate: GitHubPendingShareImportAttachCandidate?,
    duplicateExists: Boolean,
    submitting: Boolean,
    submittingAndOpen: Boolean,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmAndOpenGitHub: (() -> Unit)? = null,
) {
    SnapshotWindowBottomSheet(
        show = candidate != null,
        title = stringResource(R.string.github_share_import_attach_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = allowDismiss,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = candidate != null)
        val attachCandidate = candidate ?: return@SnapshotWindowBottomSheet
        SheetContentColumn(
            modifier = Modifier.shareImportSheetTags(),
            verticalSpacing = 10.dp,
        ) {
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 6.dp,
            ) {
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_pending_label_target),
                    value = "${attachCandidate.owner}/${attachCandidate.repo}",
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_attach_dialog_label_app),
                    value = attachCandidate.appLabel,
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_attach_dialog_label_package),
                    value = attachCandidate.packageName,
                )
                val versionLabel =
                    shareImportVersionLabel(
                        versionName = attachCandidate.versionName,
                        versionCode = attachCandidate.versionCode,
                    )
                if (versionLabel.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_dialog_label_version),
                        value = versionLabel,
                    )
                }
            }
            if (duplicateExists) {
                SheetDescriptionText(
                    text = stringResource(R.string.github_share_import_attach_dialog_duplicate_hint),
                )
            }
            if (submitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidCircularProgressBar(size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            if (submittingAndOpen) {
                                stringResource(R.string.github_share_import_attach_dialog_processing_open)
                            } else {
                                stringResource(R.string.github_share_import_attach_dialog_processing_add)
                            },
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            if (duplicateExists) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppLiquidDialogActionButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(KeiOsTestTags.GitHubShareImportAttachClose),
                        text = stringResource(R.string.common_close),
                        leadingIcon = appLucideCloseIcon(),
                        containerColor = GitHubStatusPalette.Active,
                        onClick = onCancel,
                        enabled = !submitting,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppLiquidDialogActionButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(KeiOsTestTags.GitHubShareImportAttachCancel),
                        text = stringResource(R.string.common_cancel),
                        leadingIcon = appLucideCloseIcon(),
                        containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                        variant = GitHubShareImportActionStyle.cancelVariant,
                        onClick = onCancel,
                        enabled = !submitting,
                    )
                    AppLiquidDialogActionButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(KeiOsTestTags.GitHubShareImportAttachConfirm),
                        text =
                            if (submitting && !submittingAndOpen) {
                                stringResource(R.string.common_processing)
                            } else {
                                stringResource(R.string.github_share_import_attach_dialog_action_confirm)
                            },
                        leadingIcon = appLucidePackageIcon(),
                        containerColor = GitHubStatusPalette.Active,
                        onClick = onConfirm,
                        enabled = !submitting,
                    )
                }
                if (onConfirmAndOpenGitHub != null) {
                    AppLiquidDialogActionButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(KeiOsTestTags.GitHubShareImportAttachConfirmOpenGitHub),
                        text =
                            if (submitting && submittingAndOpen) {
                                stringResource(R.string.common_processing)
                            } else {
                                stringResource(
                                    R.string.github_share_import_attach_dialog_action_confirm_and_open_github,
                                )
                            },
                        leadingIcon = appLucideExternalLinkIcon(),
                        containerColor = GitHubStatusPalette.Update,
                        onClick = onConfirmAndOpenGitHub,
                        enabled = !submitting,
                    )
                }
            }
        }
    }
}
