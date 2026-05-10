package os.kei.ui.page.main.github.install

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubApkInstallSheet(
    state: GitHubApkInstallFlowState,
    backdrop: LayerBackdrop,
    onDismissRequest: () -> Unit = GitHubApkInstallFlowCoordinator::hideSheet
) {
    if (!state.sheetVisible || !state.active) return
    val context = LocalContext.current
    val unknownValue = stringResource(R.string.github_apk_install_value_unknown)
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_apk_install_sheet_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = stringResource(state.phase.labelRes()),
                        color = state.phase.statusColor()
                    )
                    if (state.phase in progressPhases) {
                        Text(
                            text = stringResource(
                                R.string.github_apk_install_summary_progress,
                                (state.progress * 100).toInt().coerceIn(0, 100)
                            ),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight
                        )
                    }
                }
                Text(
                    text = state.selectedCandidateName
                        .ifBlank { state.asset?.name.orEmpty() }
                        .ifBlank { state.request.displayLabel },
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                SheetDescriptionText(
                    text = stringResource(
                        R.string.github_apk_install_summary_source,
                        state.request.displayLabel
                    )
                )
                val displaySize = state.selectedCandidateSizeBytes
                    .takeIf { it > 0L }
                    ?: state.asset?.sizeBytes.orZero()
                if (displaySize > 0L) {
                    SheetDescriptionText(
                        text = stringResource(
                            R.string.github_apk_install_summary_size,
                            formatAssetSize(displaySize, context)
                        )
                    )
                }
                state.localArchiveInfo?.let { archive ->
                    SheetDescriptionText(
                        text = stringResource(
                            R.string.github_apk_install_summary_package,
                            archive.packageName
                        )
                    )
                    SheetDescriptionText(
                        text = stringResource(
                            R.string.github_apk_install_summary_version,
                            archive.versionName.ifBlank { unknownValue },
                            archive.versionCode
                        )
                    )
                    SheetDescriptionText(
                        text = stringResource(
                            R.string.github_apk_install_summary_sdk,
                            archive.minSdk,
                            archive.targetSdk
                        )
                    )
                }
                state.installedPackageInfo?.let { installed ->
                    SheetDescriptionText(
                        text = stringResource(
                            R.string.github_apk_install_summary_local_version,
                            installed.versionName.ifBlank { unknownValue },
                            installed.versionCode
                        )
                    )
                }
                if (state.phase in progressPhases) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LiquidCircularProgressBar(size = 24.dp)
                }
                state.message.takeIf { it.isNotBlank() }?.let { message ->
                    SheetDescriptionText(text = message)
                }
            }

            when (state.phase) {
                GitHubApkInstallPhase.SelectingApk -> ApkCandidateSection(
                    state = state,
                    context = context
                )

                GitHubApkInstallPhase.ReadyToInstall -> ReadyToInstallSection(state, backdrop)
                GitHubApkInstallPhase.PendingUserAction -> PendingUserActionSection(
                    context,
                    backdrop
                )

                GitHubApkInstallPhase.Failed -> FailureActionSection(context, backdrop)
                GitHubApkInstallPhase.Success -> SuccessActionSection(context, backdrop)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ApkCandidateSection(
    state: GitHubApkInstallFlowState,
    context: android.content.Context
) {
    SheetSectionTitle(stringResource(R.string.github_apk_install_phase_selecting))
    SheetSectionCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalSpacing = 6.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = state.candidates,
                key = { candidate -> candidate.index }
            ) { candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            GitHubApkInstallFlowCoordinator.selectCandidate(candidate.index)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = candidate.name,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatAssetSize(candidate.sizeBytes, context),
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyToInstallSection(
    state: GitHubApkInstallFlowState,
    backdrop: LayerBackdrop
) {
    val context = LocalContext.current
    SheetSectionTitle(stringResource(R.string.github_apk_install_reference_title))
    SheetSectionCard(verticalSpacing = 8.dp) {
        state.localArchiveInfo?.let { archive ->
            ApkReferenceRows(
                archive = archive,
                installed = state.installedPackageInfo,
                fileSize = state.selectedCandidateSizeBytes,
                context = context
            )
        }
        state.trustSignal?.let { signal ->
            StatusPill(
                label = stringResource(signal.level.labelRes()),
                color = signal.level.statusColor()
            )
            val reasons = signal.reasons.ifEmpty { emptyList() }
            if (reasons.isEmpty()) {
                SheetDescriptionText(text = stringResource(R.string.github_apk_trust_detail_reason_empty))
            } else {
                reasons.forEach { reason ->
                    SheetDescriptionText(text = stringResource(reason.labelRes()))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.common_cancel),
                onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
                modifier = Modifier.weight(1f),
                variant = GlassVariant.SheetAction
            )
            AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.github_apk_install_action_install),
                onClick = GitHubApkInstallFlowCoordinator::confirmInstall,
                modifier = Modifier.weight(1f),
                variant = GlassVariant.SheetAction
            )
        }
    }
}

@Composable
private fun ApkReferenceRows(
    archive: LocalApkArchiveInfo,
    installed: GitHubInstalledPackageInfo?,
    fileSize: Long,
    context: android.content.Context
) {
    val unknownValue = stringResource(R.string.github_apk_install_value_unknown)
    InfoRow(
        label = stringResource(R.string.github_apk_install_reference_package),
        value = archive.packageName
    )
    InfoRow(
        label = stringResource(R.string.github_apk_install_reference_candidate_version),
        value = stringResource(
            R.string.github_apk_install_reference_version_value,
            archive.versionName.ifBlank { unknownValue },
            archive.versionCode
        )
    )
    installed?.let { local ->
        InfoRow(
            label = stringResource(R.string.github_apk_install_reference_local_version),
            value = stringResource(
                R.string.github_apk_install_reference_version_value,
                local.versionName.ifBlank { unknownValue },
                local.versionCode
            )
        )
    }
    InfoRow(
        label = stringResource(R.string.github_apk_install_reference_sdk),
        value = stringResource(
            R.string.github_apk_install_reference_sdk_value,
            archive.minSdk,
            archive.targetSdk
        )
    )
    if (fileSize > 0L) {
        InfoRow(
            label = stringResource(R.string.github_apk_install_reference_size),
            value = formatAssetSize(fileSize, context)
        )
    }
    InfoRow(
        label = stringResource(R.string.github_apk_install_reference_signature),
        value = archive.signatureSha256.firstOrNull()
            ?.let { sha ->
                stringResource(
                    R.string.github_apk_install_reference_signature_value,
                    sha.take(12)
                )
            }
            ?: stringResource(R.string.github_apk_info_trust_signature_unknown)
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            color = MiuixTheme.colorScheme.primary,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PendingUserActionSection(
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    SheetSectionCard {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_install_action_open_system_confirm),
            onClick = { GitHubApkInstallFlowCoordinator.launchPendingUserAction(context) },
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.SheetAction
        )
    }
}

@Composable
private fun FailureActionSection(
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    SheetSectionCard(verticalSpacing = 8.dp) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_install_action_retry),
            onClick = { GitHubApkInstallFlowCoordinator.retry(context) },
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.SheetAction
        )
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_install_action_external),
            onClick = { GitHubApkInstallFlowCoordinator.openExternalCurrent(context) },
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.SheetAction
        )
    }
}

@Composable
private fun SuccessActionSection(
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    SheetSectionCard {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.common_mark_read),
            onClick = { GitHubApkInstallFlowCoordinator.markRead(context) },
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.SheetAction
        )
    }
}

private val progressPhases = setOf(
    GitHubApkInstallPhase.Downloading,
    GitHubApkInstallPhase.Inspecting,
    GitHubApkInstallPhase.Installing
)

private fun GitHubApkInstallPhase.labelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_phase_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_phase_selecting
        GitHubApkInstallPhase.Inspecting -> R.string.github_apk_install_phase_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_phase_ready
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_phase_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_phase_pending
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_phase_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_phase_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_phase_installing
    }
}

private fun GitHubApkInstallPhase.statusColor(): Color {
    return when (this) {
        GitHubApkInstallPhase.Success -> GitHubStatusPalette.Update
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled -> GitHubStatusPalette.Error

        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.PendingUserAction -> GitHubStatusPalette.Cache

        else -> GitHubStatusPalette.Active
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun GitHubDecisionLevel.statusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

private fun GitHubDecisionLevel.labelRes(): Int {
    return when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }
}

private fun GitHubApkTrustReason.labelRes(): Int {
    return when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
        GitHubApkTrustReason.PackageMatched -> R.string.github_apk_trust_reason_package_matched
        GitHubApkTrustReason.PackageMismatch -> R.string.github_apk_trust_reason_package_mismatch
        GitHubApkTrustReason.SignatureMatched -> R.string.github_apk_trust_reason_signature_matched
        GitHubApkTrustReason.SignatureMismatch -> R.string.github_apk_trust_reason_signature_mismatch
        GitHubApkTrustReason.SignatureUnknown -> R.string.github_apk_trust_reason_signature_unknown
        GitHubApkTrustReason.VersionUpgrade -> R.string.github_apk_trust_reason_version_upgrade
        GitHubApkTrustReason.VersionDowngrade -> R.string.github_apk_trust_reason_version_downgrade
        GitHubApkTrustReason.MinSdkTooHigh -> R.string.github_apk_trust_reason_min_sdk_high
        GitHubApkTrustReason.TestOnly -> R.string.github_apk_trust_reason_test_only
        GitHubApkTrustReason.SensitivePermission -> R.string.github_apk_trust_reason_sensitive_permission
        GitHubApkTrustReason.ExportedComponent -> R.string.github_apk_trust_reason_exported_component
    }
}
