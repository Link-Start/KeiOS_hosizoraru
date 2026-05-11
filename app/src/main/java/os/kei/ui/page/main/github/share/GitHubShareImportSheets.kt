package os.kei.ui.page.main.github.share

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetLiquidChoiceIndicator
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val shareImportSheetInsideMargin = DpSize(
    BottomSheetDefaults.insideMargin.width,
    20.dp
)
private const val shareImportInfoLabelWeight = 0.24f

private fun Modifier.shareImportSheetSafeArea(): Modifier {
    return this
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(bottom = 12.dp)
}

@Composable
private fun ShareImportCompactInfoRow(
    key: String,
    value: String
) {
    AppInfoRow(
        label = key,
        value = value,
        labelWeight = shareImportInfoLabelWeight,
        valueWeight = 1f - shareImportInfoLabelWeight,
        valueTextAlign = TextAlign.Start,
        horizontalSpacing = 8.dp,
        rowVerticalPadding = 2.dp
    )
}

private fun compactProjectValue(preview: GitHubShareImportPreview): String {
    val owner = preview.owner.trim()
    val repo = preview.repo.trim()
    if (owner.isNotBlank() && repo.isNotBlank()) {
        return "$owner/$repo"
    }
    val rawProjectUrl = preview.projectUrl.trim()
    val compacted = rawProjectUrl
        .removePrefix("https://github.com/")
        .removePrefix("http://github.com/")
        .removePrefix("https://www.github.com/")
        .removePrefix("http://www.github.com/")
        .trim('/')
    return compacted.ifBlank { rawProjectUrl }
}

@Composable
internal fun GitHubShareImportSheet(
    preview: GitHubShareImportPreview?,
    resolving: Boolean,
    phase: GitHubShareImportPhase,
    managedInstallProgress: GitHubShareImportManagedInstallProgress? = null,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit
) {
    val context = LocalContext.current
    val showSheet = resolving || preview != null
    val managedInstallRunning = managedInstallProgress?.phase in setOf(
        GitHubShareImportPhase.InstallDownloading,
        GitHubShareImportPhase.Installing,
        GitHubShareImportPhase.InstallCommitting
    )
    val managedInstallSessionActive = managedInstallRunning ||
            managedInstallProgress?.phase == GitHubShareImportPhase.InstallReady
    SnapshotWindowBottomSheet(
        show = showSheet,
        title = stringResource(R.string.github_share_import_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = !resolving && !managedInstallRunning
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = showSheet)
        if (resolving) {
            Column(
                modifier = Modifier
                    .shareImportSheetSafeArea()
                    .heightIn(min = 236.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LiquidCircularProgressBar(size = 24.dp)
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(
                    label = stringResource(phase.labelRes),
                    color = phase.color
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.github_share_import_dialog_summary_parsing),
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            return@SnapshotWindowBottomSheet
        }
        if (preview == null) return@SnapshotWindowBottomSheet

        val supportedAbis = remember {
            Build.SUPPORTED_ABIS?.toList().orEmpty()
        }
        val devicePreferredAssetIndex = remember(preview.assets, supportedAbis) {
            preview.assets.indexOfFirst { asset ->
                assetIsPreferredForDevice(asset.name, supportedAbis)
            }
        }
        var selectedIndex by remember(
            preview.sourceUrl,
            preview.releaseTag,
            preview.assets,
            devicePreferredAssetIndex
        ) {
            val initialIndex = when {
                preview.preferredAssetName.isNotBlank() -> preview.defaultSelectedIndex
                devicePreferredAssetIndex >= 0 -> devicePreferredAssetIndex
                else -> preview.defaultSelectedIndex
            }.coerceAtLeast(0)
            mutableIntStateOf(initialIndex)
        }
        val safeSelectedIndex = selectedIndex.coerceIn(0, preview.assets.lastIndex)
        val selectedAsset = preview.assets.getOrNull(safeSelectedIndex)

        Column(
            modifier = Modifier.shareImportSheetSafeArea(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 4.dp
            ) {
                StatusPill(
                    label = stringResource(phase.labelRes),
                    color = phase.color
                )
                ManagedInstallProgressBlock(managedInstallProgress)
                ShareImportCompactInfoRow(
                    key = stringResource(R.string.github_share_import_dialog_label_project),
                    value = compactProjectValue(preview)
                )
                ShareImportCompactInfoRow(
                    key = stringResource(R.string.github_share_import_dialog_label_strategy),
                    value = preview.strategyLabel
                )
                ShareImportCompactInfoRow(
                    key = stringResource(R.string.github_share_import_dialog_label_release),
                    value = preview.releaseTag
                )
            }
            SheetSectionTitle(stringResource(R.string.github_share_import_dialog_label_assets))
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalSpacing = 6.dp
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = preview.assets,
                        key = { _, asset -> asset.name },
                        contentType = { _, _ -> "github_share_asset" }
                    ) { index, asset ->
                        val preferredForDevice = assetIsPreferredForDevice(asset.name, supportedAbis)
                        val likelyCompatible = assetLikelyCompatibleWithDevice(asset.name, supportedAbis)
                        val compatibilityHint = if (!likelyCompatible) {
                            stringResource(
                                R.string.github_share_import_dialog_asset_hint_maybe_incompatible
                            )
                        } else {
                            null
                        }
                        val baseAssetSummary = stringResource(
                            R.string.github_share_import_dialog_asset_summary,
                            formatAssetSize(asset.sizeBytes, context),
                            if (asset.apiAssetUrl.isNotBlank()) {
                                stringResource(R.string.github_asset_fetch_source_api)
                            } else {
                                stringResource(R.string.github_asset_transport_direct)
                            }
                        )
                        val assetSummary = compatibilityHint?.let { hint ->
                            "$baseAssetSummary · $hint"
                        } ?: baseAssetSummary
                        val selected = safeSelectedIndex == index
                        SheetControlRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !managedInstallSessionActive) {
                                    selectedIndex = index
                                },
                            label = asset.name,
                            summary = assetSummary
                        ) {
                            if (preferredForDevice) {
                                StatusPill(
                                    label = stringResource(
                                        R.string.github_share_import_dialog_asset_badge_recommended
                                    ),
                                    color = GitHubStatusPalette.Update
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (!likelyCompatible) {
                                StatusPill(
                                    label = stringResource(
                                        R.string.github_share_import_dialog_asset_badge_incompatible
                                    ),
                                    color = GitHubStatusPalette.PreRelease
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            SheetLiquidChoiceIndicator(
                                selected = selected,
                                onSelect = {
                                    if (!managedInstallSessionActive) selectedIndex = index
                                },
                                accentColor = GitHubStatusPalette.Active
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                    variant = GitHubShareImportActionStyle.cancelVariant,
                    onClick = onCancel
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = if (managedInstallRunning) {
                        stringResource(R.string.common_processing)
                    } else if (managedInstallProgress?.phase == GitHubShareImportPhase.InstallReady) {
                        stringResource(R.string.github_share_import_notify_action_continue_install)
                    } else {
                        stringResource(R.string.github_share_import_dialog_action_confirm)
                    },
                    leadingIcon = appLucidePackageIcon(),
                    containerColor = GitHubStatusPalette.Active,
                    onClick = {
                        selectedAsset?.let(onConfirmImport)
                    },
                    enabled = selectedAsset != null && !managedInstallRunning
                )
            }
        }
    }
}

@Composable
private fun ManagedInstallProgressBlock(
    progress: GitHubShareImportManagedInstallProgress?
) {
    progress ?: return
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(4.dp))
    if (progress.assetName.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_pending_label_asset),
            value = progress.assetName
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
            value = appDisplayName
        )
    }
    if (progress.packageName.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_attach_dialog_label_package),
            value = progress.packageName
        )
    }
    val versionLabel = managedInstallVersionLabel(progress)
    if (versionLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_version),
            value = versionLabel
        )
    }
    val abiLabel = managedInstallAbiLabel(progress)
    if (abiLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_abi),
            value = abiLabel
        )
    }
    val sdkLabel = managedInstallSdkLabel(progress)
    if (sdkLabel.isNotBlank()) {
        ShareImportCompactInfoRow(
            key = stringResource(R.string.github_share_import_dialog_label_sdk),
            value = sdkLabel
        )
    }
    val showDownloadText =
        progress.phase == GitHubShareImportPhase.InstallDownloading || progress.downloadedBytes > 0L
    if (showDownloadText) {
        val progressText = remember(
            progress.downloadedBytes,
            progress.totalBytes
        ) {
            formatManagedInstallDownloadProgress(context, progress)
        }
        if (progressText.isNotBlank()) {
            ShareImportCompactInfoRow(
                key = stringResource(R.string.github_share_import_dialog_label_download),
                value = progressText
            )
        }
    }
    if (progress.hasKnownDownloadProgress) {
        val percentText = stringResource(
            R.string.github_refresh_progress_percent,
            progress.boundedProgressPercent
        )
        LiquidLinearProgressBar(
            progress = { progress.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            activeColor = progress.phase.color,
            contentDescription = stringResource(
                R.string.common_progress_with_value,
                percentText
            )
        )
    }
}

@Composable
private fun managedInstallVersionLabel(
    progress: GitHubShareImportManagedInstallProgress
): String {
    return shareImportVersionLabel(
        versionName = progress.versionName,
        versionCode = progress.versionCode
    )
}

@Composable
private fun shareImportVersionLabel(
    versionName: String,
    versionCode: String
): String {
    val normalizedVersionName = versionName.trim()
    val normalizedVersionCode = versionCode.trim()
    return when {
        normalizedVersionName.isNotBlank() && normalizedVersionCode.isNotBlank() -> stringResource(
            R.string.github_share_import_dialog_version_value,
            normalizedVersionName,
            normalizedVersionCode
        )

        normalizedVersionName.isNotBlank() -> normalizedVersionName
        normalizedVersionCode.isNotBlank() -> normalizedVersionCode
        else -> ""
    }
}

@Composable
private fun managedInstallAbiLabel(
    progress: GitHubShareImportManagedInstallProgress
): String {
    val abis = progress.nativeAbis
        .map { abi -> abi.trim() }
        .filter { abi -> abi.isNotBlank() }
        .distinct()
    if (abis.isNotEmpty()) return abis.joinToString(", ")
    val inspectionReady = progress.packageName.isNotBlank() &&
            progress.phase in setOf(
        GitHubShareImportPhase.InstallReady,
        GitHubShareImportPhase.InstallCommitting
    )
    return if (inspectionReady) {
        stringResource(R.string.github_share_import_dialog_abi_universal)
    } else {
        ""
    }
}

@Composable
private fun managedInstallSdkLabel(
    progress: GitHubShareImportManagedInstallProgress
): String {
    val targetSdk = progress.targetSdk.trim()
    val minSdk = progress.minSdk.trim()
    return when {
        targetSdk.isNotBlank() && minSdk.isNotBlank() -> stringResource(
            R.string.github_share_import_dialog_sdk_value,
            targetSdk,
            minSdk
        )

        targetSdk.isNotBlank() -> stringResource(
            R.string.github_share_import_dialog_sdk_target_value,
            targetSdk
        )

        minSdk.isNotBlank() -> stringResource(
            R.string.github_share_import_dialog_sdk_min_value,
            minSdk
        )

        else -> ""
    }
}

private fun formatManagedInstallDownloadProgress(
    context: android.content.Context,
    progress: GitHubShareImportManagedInstallProgress
): String {
    val downloadedBytes = progress.downloadedBytes.coerceAtLeast(0L)
    if (downloadedBytes <= 0L && progress.totalBytes <= 0L) return ""
    val downloaded = android.text.format.Formatter.formatFileSize(context, downloadedBytes)
    if (progress.totalBytes > 0L) {
        return context.getString(
            R.string.github_share_import_notify_download_progress_known,
            downloaded,
            android.text.format.Formatter.formatFileSize(context, progress.totalBytes)
        )
    }
    return context.getString(
        R.string.github_share_import_notify_download_progress_unknown,
        downloaded
    )
}

@Composable
internal fun GitHubShareImportDisabledSheet(
    show: Boolean,
    onClose: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.github_share_import_disabled_title),
        onDismissRequest = onClose,
        insideMargin = shareImportSheetInsideMargin
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = show)
        Column(
            modifier = Modifier.shareImportSheetSafeArea(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetDescriptionText(
                text = stringResource(R.string.github_share_import_disabled_summary)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_close),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onClose
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_disabled_action_open),
                    leadingIcon = appLucideExternalLinkIcon(),
                    containerColor = GitHubStatusPalette.Active,
                    onClick = onOpenGitHub
                )
            }
        }
    }
}

@Composable
internal fun GitHubShareImportPendingSheet(
    pending: GitHubPendingShareImportTrackRecord?,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit
) {
    SnapshotWindowBottomSheet(
        show = pending != null,
        title = stringResource(R.string.github_share_import_pending_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = false
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = pending != null)
        val pendingTrack = pending ?: return@SnapshotWindowBottomSheet
        val remainingMinutes = shareImportRemainingMinutes(pendingTrack.armedAtMillis)
        Column(
            modifier = Modifier.shareImportSheetSafeArea(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetDescriptionText(
                text = stringResource(
                    if (pendingTrack.packageName.isNotBlank()) {
                        R.string.github_share_import_pending_dialog_summary_exact
                    } else {
                        R.string.github_share_import_pending_dialog_summary
                    }
                )
            )
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 6.dp
            ) {
                StatusPill(
                    label = stringResource(
                        R.string.github_share_import_pending_remaining_minutes,
                        remainingMinutes
                    ),
                    color = GitHubStatusPalette.PreRelease
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_pending_label_target),
                    value = "${pendingTrack.owner}/${pendingTrack.repo}"
                )
                if (pendingTrack.releaseTag.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_release),
                        value = pendingTrack.releaseTag
                    )
                }
                if (pendingTrack.assetName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_asset),
                        value = pendingTrack.assetName
                    )
                }
                if (pendingTrack.packageName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_attach_dialog_label_package),
                        value = pendingTrack.packageName
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_close),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onClose
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_pending_action_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                    variant = GitHubShareImportActionStyle.cancelVariant,
                    onClick = onCancel
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
    onConfirmAndOpenGitHub: (() -> Unit)? = null
) {
    SnapshotWindowBottomSheet(
        show = candidate != null,
        title = stringResource(R.string.github_share_import_attach_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = allowDismiss
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = candidate != null)
        val attachCandidate = candidate ?: return@SnapshotWindowBottomSheet
        Column(
            modifier = Modifier.shareImportSheetSafeArea(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 6.dp
            ) {
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_pending_label_target),
                    value = "${attachCandidate.owner}/${attachCandidate.repo}"
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_attach_dialog_label_app),
                    value = attachCandidate.appLabel
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_attach_dialog_label_package),
                    value = attachCandidate.packageName
                )
                val versionLabel = shareImportVersionLabel(
                    versionName = attachCandidate.versionName,
                    versionCode = attachCandidate.versionCode
                )
                if (versionLabel.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_dialog_label_version),
                        value = versionLabel
                    )
                }
            }
            if (duplicateExists) {
                SheetDescriptionText(
                    text = stringResource(R.string.github_share_import_attach_dialog_duplicate_hint)
                )
            }
            if (submitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidCircularProgressBar(size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (submittingAndOpen) {
                            stringResource(R.string.github_share_import_attach_dialog_processing_open)
                        } else {
                            stringResource(R.string.github_share_import_attach_dialog_processing_add)
                        },
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
            if (duplicateExists) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLiquidDialogActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.common_close),
                        leadingIcon = appLucideCloseIcon(),
                        containerColor = GitHubStatusPalette.Active,
                        onClick = onCancel,
                        enabled = !submitting
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLiquidDialogActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.common_cancel),
                        leadingIcon = appLucideCloseIcon(),
                        containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                        variant = GitHubShareImportActionStyle.cancelVariant,
                        onClick = onCancel,
                        enabled = !submitting
                    )
                    AppLiquidDialogActionButton(
                        modifier = Modifier.weight(1f),
                        text = if (submitting && !submittingAndOpen) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.github_share_import_attach_dialog_action_confirm)
                        },
                        leadingIcon = appLucidePackageIcon(),
                        containerColor = GitHubStatusPalette.Active,
                        onClick = onConfirm,
                        enabled = !submitting
                    )
                }
                if (onConfirmAndOpenGitHub != null) {
                    AppLiquidDialogActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (submitting && submittingAndOpen) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(
                                R.string.github_share_import_attach_dialog_action_confirm_and_open_github
                            )
                        },
                        leadingIcon = appLucideExternalLinkIcon(),
                        containerColor = GitHubStatusPalette.Update,
                        onClick = onConfirmAndOpenGitHub,
                        enabled = !submitting
                    )
                }
            }
        }
    }
}
