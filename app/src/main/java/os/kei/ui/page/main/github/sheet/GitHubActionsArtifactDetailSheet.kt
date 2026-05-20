@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubApkTrustReason
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtNoYear
import os.kei.ui.page.main.github.buildGitHubApkTrustSignal
import os.kei.ui.page.main.github.page.GitHubActionsArtifactDetailRequest
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet

@Composable
internal fun GitHubActionsArtifactDetailSheet(
    request: GitHubActionsArtifactDetailRequest?,
    backdrop: LayerBackdrop,
    hasToken: Boolean,
    managedInstallEnabled: Boolean,
    downloading: Boolean,
    sharing: Boolean,
    onDismissRequest: () -> Unit,
    onRefreshRun: (Long) -> Unit,
    onDownload: (Long, Long) -> Unit,
    onShare: (Long, Long) -> Unit,
) {
    val detail = request ?: return
    val run = detail.runMatch.runArtifacts.run
    val artifact = detail.artifactMatch.artifact
    val context = LocalContext.current
    val trustSignal =
        buildGitHubApkTrustSignal(
            asset =
                GitHubReleaseAssetFile(
                    name = artifact.name,
                    downloadUrl = artifact.archiveDownloadUrl,
                    sizeBytes = artifact.sizeBytes,
                    downloadCount = 0,
                    updatedAtMillis = artifact.updatedAtMillis,
                ),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
    val busy = downloading || sharing
    val canAct = hasToken && detail.runMatch.traits.completed && !artifact.expired && !busy
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_actions_artifact_detail_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.common_refresh),
                onClick = { onRefreshRun(run.id) },
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = artifact.name,
                badgeLabel = stringResource(trustSignal.level.labelRes()),
                badgeColor = trustSignal.level.statusColor(),
            ) {
                GitHubDecisionDetailTextLine(
                    stringResource(
                        R.string.github_actions_artifact_detail_score,
                        detail.artifactMatch.score,
                    ),
                )
                GitHubDecisionDetailTextLine(
                    stringResource(
                        R.string.github_actions_artifact_detail_run,
                        run.displayName,
                        run.headBranch.ifBlank { stringResource(R.string.common_unknown) },
                    ),
                )
                GitHubDecisionDetailTextLine(
                    artifact.sizeBytes
                        .takeIf { it > 0L }
                        ?.let { formatAssetSize(it, context) }
                        ?: stringResource(R.string.common_unknown),
                )
                artifact.expiresAtMillis?.let { expiresAt ->
                    GitHubDecisionDetailTextLine(
                        stringResource(
                            R.string.github_actions_artifact_detail_expires_at,
                            formatReleaseUpdatedAtNoYear(expiresAt)
                                ?: stringResource(R.string.common_unknown),
                        ),
                    )
                }
                artifact.workflowRunHeadSha
                    .ifBlank { run.headSha }
                    .takeIf { it.isNotBlank() }
                    ?.let { sha ->
                        GitHubDecisionDetailTextLine(
                            stringResource(
                                R.string.github_actions_artifact_detail_commit,
                                sha.take(12),
                            ),
                        )
                    }
                artifact.digest.takeIf { it.isNotBlank() }?.let { digest ->
                    GitHubDecisionDetailTextLine(
                        stringResource(
                            R.string.github_actions_artifact_detail_digest,
                            digest,
                        ),
                        maxLines = 2,
                    )
                }
            }
            TrustReasonSection(
                reasons = trustSignal.reasons,
                context = context,
            )
            ArtifactSelectorReasonSection(detail.artifactMatch.reasons)
            SheetSectionCard {
                val copyPayload =
                    buildArtifactCopyPayload(
                        runHeadSha = run.headSha,
                        artifactHeadSha = artifact.workflowRunHeadSha,
                        digest = artifact.digest,
                    )
                val copyToast =
                    stringResource(R.string.github_actions_toast_artifact_metadata_copied)
                ActionButtonRow {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.github_actions_action_copy_metadata),
                        leadingIcon = osLucideCopyIcon(),
                        enabled = copyPayload.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            copyTextToClipboard(context, "github_artifact_metadata", copyPayload)
                            context.showToast(copyToast)
                        },
                    )
                }
                ActionButtonRow {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text =
                            stringResource(
                                when {
                                    managedInstallEnabled && downloading -> R.string.github_apk_info_action_installing
                                    managedInstallEnabled -> R.string.github_page_install_status_install
                                    else -> R.string.common_download
                                },
                            ),
                        leadingIcon =
                            if (managedInstallEnabled) {
                                appLucidePackageIcon()
                            } else {
                                appLucideDownloadIcon()
                            },
                        enabled = canAct,
                        modifier = Modifier.weight(1f),
                        onClick = { onDownload(run.id, artifact.id) },
                    )
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.github_actions_action_share),
                        leadingIcon = appLucideShareIcon(),
                        enabled = canAct,
                        modifier = Modifier.weight(1f),
                        onClick = { onShare(run.id, artifact.id) },
                    )
                }
            }
        }
    }
}

private fun buildArtifactCopyPayload(
    runHeadSha: String,
    artifactHeadSha: String,
    digest: String,
): String =
    buildList {
        artifactHeadSha.ifBlank { runHeadSha }.takeIf { it.isNotBlank() }?.let { sha ->
            add("commit: $sha")
        }
        digest.takeIf { it.isNotBlank() }?.let { value ->
            add("digest: $value")
        }
    }.joinToString("\n")

@Composable
private fun TrustReasonSection(
    reasons: List<GitHubApkTrustReason>,
    context: Context,
) {
    SheetSectionTitle(stringResource(R.string.github_apk_trust_detail_reason_title))
    SheetSectionCard {
        if (reasons.isEmpty()) {
            GitHubDecisionDetailTextLine(stringResource(R.string.github_apk_trust_detail_reason_empty))
        } else {
            reasons.forEach { reason ->
                GitHubDecisionDetailTextLine(context.getString(reason.labelRes()))
            }
        }
    }
}

@Composable
private fun ArtifactSelectorReasonSection(reasons: List<String>) {
    SheetSectionTitle(stringResource(R.string.github_actions_artifact_detail_selector_title))
    SheetSectionCard {
        if (reasons.isEmpty()) {
            GitHubDecisionDetailTextLine(stringResource(R.string.github_actions_artifact_detail_selector_empty))
        } else {
            reasons.take(6).forEach { reason ->
                GitHubDecisionDetailTextLine(reason)
            }
        }
    }
}

@Composable
private fun ActionButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun GitHubDecisionLevel.statusColor(): Color =
    when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }

private fun GitHubDecisionLevel.labelRes(): Int =
    when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }

private fun GitHubApkTrustReason.labelRes(): Int =
    when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
    }
