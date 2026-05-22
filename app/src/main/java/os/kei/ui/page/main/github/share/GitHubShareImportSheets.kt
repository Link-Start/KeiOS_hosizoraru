@file:Suppress("FunctionName", "PropertyName")

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetLiquidChoiceIndicator
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val shareImportSheetInsideMargin =
    DpSize(
        BottomSheetDefaults.insideMargin.width,
        20.dp,
    )
private const val shareImportInfoLabelWeight = 0.24f

internal fun Modifier.shareImportSheetTags(): Modifier =
    this
        .fillMaxWidth()
        .semantics { testTagsAsResourceId = true }

private fun Modifier.shareImportSheetSafeArea(): Modifier =
    this
        .shareImportSheetTags()
        .navigationBarsPadding()
        .imePadding()
        .padding(bottom = 12.dp)

@Composable
internal fun ShareImportCompactInfoRow(
    key: String,
    value: String,
) {
    AppInfoRow(
        label = key,
        value = value,
        labelWeight = shareImportInfoLabelWeight,
        valueWeight = 1f - shareImportInfoLabelWeight,
        valueTextAlign = TextAlign.Start,
        horizontalSpacing = 8.dp,
        rowVerticalPadding = 2.dp,
    )
}

private fun compactProjectValue(preview: GitHubShareImportPreview): String {
    val owner = preview.owner.trim()
    val repo = preview.repo.trim()
    if (owner.isNotBlank() && repo.isNotBlank()) {
        return "$owner/$repo"
    }
    val rawProjectUrl = preview.projectUrl.trim()
    val compacted =
        rawProjectUrl
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
    onConfirmImport: (GitHubReleaseAssetFile) -> Unit,
) {
    val context = LocalContext.current
    val showSheet = resolving || preview != null
    val managedInstallRunning =
        managedInstallProgress?.phase in
            setOf(
                GitHubShareImportPhase.InstallDownloading,
                GitHubShareImportPhase.Installing,
                GitHubShareImportPhase.InstallCommitting,
            )
    val managedInstallSessionActive =
        managedInstallRunning ||
            managedInstallProgress?.phase == GitHubShareImportPhase.InstallReady
    SnapshotWindowBottomSheet(
        show = showSheet,
        title = stringResource(R.string.github_share_import_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = !resolving && !managedInstallRunning,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = showSheet)
        if (resolving) {
            Column(
                modifier =
                    Modifier
                        .shareImportSheetSafeArea()
                        .heightIn(min = 236.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LiquidCircularProgressBar(size = 24.dp)
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(
                    label = stringResource(phase.labelRes),
                    color = phase.color,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.github_share_import_dialog_summary_parsing),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            return@SnapshotWindowBottomSheet
        }
        if (preview == null) return@SnapshotWindowBottomSheet

        val supportedAbis =
            remember {
                Build.SUPPORTED_ABIS?.toList().orEmpty()
            }
        val devicePreferredAssetIndex =
            remember(preview.assets, supportedAbis) {
                preview.assets.indexOfFirst { asset ->
                    assetIsPreferredForDevice(asset.name, supportedAbis)
                }
            }
        var selectedIndex by remember(
            preview.sourceUrl,
            preview.releaseTag,
            preview.assets,
            devicePreferredAssetIndex,
        ) {
            val initialIndex =
                when {
                    preview.preferredAssetName.isNotBlank() -> preview.defaultSelectedIndex
                    devicePreferredAssetIndex >= 0 -> devicePreferredAssetIndex
                    else -> preview.defaultSelectedIndex
                }.coerceAtLeast(0)
            mutableIntStateOf(initialIndex)
        }
        val safeSelectedIndex = selectedIndex.coerceIn(0, preview.assets.lastIndex)
        val selectedAsset = preview.assets.getOrNull(safeSelectedIndex)

        SheetContentColumn(
            modifier = Modifier.shareImportSheetTags(),
            verticalSpacing = 10.dp,
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
                    value = compactProjectValue(preview),
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
            SheetSectionTitle(stringResource(R.string.github_share_import_dialog_label_assets))
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalSpacing = 6.dp,
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = preview.assets,
                        key = { _, asset -> asset.name },
                        contentType = { _, _ -> "github_share_asset" },
                    ) { index, asset ->
                        val preferredForDevice = assetIsPreferredForDevice(asset.name, supportedAbis)
                        val likelyCompatible = assetLikelyCompatibleWithDevice(asset.name, supportedAbis)
                        val compatibilityHint =
                            if (!likelyCompatible) {
                                stringResource(
                                    R.string.github_share_import_dialog_asset_hint_maybe_incompatible,
                                )
                            } else {
                                null
                            }
                        val baseAssetSummary =
                            stringResource(
                                R.string.github_share_import_dialog_asset_summary,
                                formatAssetSize(asset.sizeBytes, context),
                                if (asset.apiAssetUrl.isNotBlank()) {
                                    stringResource(R.string.github_asset_fetch_source_api)
                                } else {
                                    stringResource(R.string.github_asset_transport_direct)
                                },
                            )
                        val assetSummary =
                            compatibilityHint?.let { hint ->
                                "$baseAssetSummary · $hint"
                            } ?: baseAssetSummary
                        val selected = safeSelectedIndex == index
                        SheetControlRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !managedInstallSessionActive) {
                                        selectedIndex = index
                                    },
                            label = asset.name,
                            summary = assetSummary,
                        ) {
                            if (preferredForDevice) {
                                StatusPill(
                                    label =
                                        stringResource(
                                            R.string.github_share_import_dialog_asset_badge_recommended,
                                        ),
                                    color = GitHubStatusPalette.Update,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else if (!likelyCompatible) {
                                StatusPill(
                                    label =
                                        stringResource(
                                            R.string.github_share_import_dialog_asset_badge_incompatible,
                                        ),
                                    color = GitHubStatusPalette.PreRelease,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            SheetLiquidChoiceIndicator(
                                selected = selected,
                                onSelect = {
                                    if (!managedInstallSessionActive) selectedIndex = index
                                },
                                accentColor = GitHubStatusPalette.Active,
                            )
                        }
                    }
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
                    text =
                        if (managedInstallRunning) {
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
                    enabled = selectedAsset != null && !managedInstallRunning,
                )
            }
        }
    }
}
