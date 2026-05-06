package os.kei.ui.page.main.github.sheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubApkTrustReason
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.GitHubRepositoryHealthReason
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtNoYear
import os.kei.ui.page.main.github.buildGitHubApkTrustSignal
import os.kei.ui.page.main.github.buildGitHubReleaseNotesDetailLines
import os.kei.ui.page.main.github.buildGitHubRepositoryHealth
import os.kei.ui.page.main.github.buildGitHubRepositoryHealthImpactLines
import os.kei.ui.page.main.github.page.GitHubActionsArtifactDetailRequest
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailRequest
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubDecisionAssistDetailSheet(
    request: GitHubDecisionAssistDetailRequest?,
    backdrop: LayerBackdrop,
    versionState: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    assetLoading: Boolean,
    assetError: String,
    onDismissRequest: () -> Unit,
    onRefreshHealth: (GitHubTrackedApp) -> Unit,
    onRefreshReleaseNotes: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onOpenExternalUrl: (String, String) -> Unit
) {
    val detail = request ?: return
    val title = when (detail.type) {
        GitHubDecisionAssistDetailType.RepositoryHealth -> R.string.github_health_detail_title
        GitHubDecisionAssistDetailType.ReleaseNotes -> R.string.github_release_notes_detail_title
    }
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.common_refresh),
                onClick = {
                    when (detail.type) {
                        GitHubDecisionAssistDetailType.RepositoryHealth -> onRefreshHealth(detail.item)
                        GitHubDecisionAssistDetailType.ReleaseNotes -> {
                            onRefreshReleaseNotes(detail.item, versionState)
                        }
                    }
                }
            )
        }
    ) {
        when (detail.type) {
            GitHubDecisionAssistDetailType.RepositoryHealth -> GitHubHealthDetailContent(
                item = detail.item,
                state = versionState
            )

            GitHubDecisionAssistDetailType.ReleaseNotes -> GitHubReleaseNotesDetailContent(
                item = detail.item,
                state = versionState,
                assetBundle = assetBundle,
                assetLoading = assetLoading,
                assetError = assetError,
                backdrop = backdrop,
                onOpenExternalUrl = onOpenExternalUrl
            )
        }
    }
}

@Composable
internal fun GitHubActionsArtifactDetailSheet(
    request: GitHubActionsArtifactDetailRequest?,
    backdrop: LayerBackdrop,
    hasToken: Boolean,
    downloading: Boolean,
    sharing: Boolean,
    onDismissRequest: () -> Unit,
    onRefreshRun: (Long) -> Unit,
    onDownload: (Long, Long) -> Unit,
    onShare: (Long, Long) -> Unit
) {
    val detail = request ?: return
    val run = detail.runMatch.runArtifacts.run
    val artifact = detail.artifactMatch.artifact
    val context = LocalContext.current
    val trustSignal = buildGitHubApkTrustSignal(
        asset = GitHubReleaseAssetFile(
            name = artifact.name,
            downloadUrl = artifact.archiveDownloadUrl,
            sizeBytes = artifact.sizeBytes,
            downloadCount = 0,
            updatedAtMillis = artifact.updatedAtMillis
        ),
        supportedAbis = Build.SUPPORTED_ABIS.toList()
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
                onClick = onDismissRequest
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.common_refresh),
                onClick = { onRefreshRun(run.id) }
            )
        }
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = artifact.name,
                badgeLabel = stringResource(trustSignal.level.labelRes()),
                badgeColor = trustSignal.level.statusColor()
            ) {
                DetailTextLine(
                    stringResource(
                        R.string.github_actions_artifact_detail_score,
                        detail.artifactMatch.score
                    )
                )
                DetailTextLine(
                    stringResource(
                        R.string.github_actions_artifact_detail_run,
                        run.displayName,
                        run.headBranch.ifBlank { stringResource(R.string.common_unknown) }
                    )
                )
                DetailTextLine(
                    artifact.sizeBytes.takeIf { it > 0L }
                        ?.let { formatAssetSize(it, context) }
                        ?: stringResource(R.string.common_unknown)
                )
                artifact.expiresAtMillis?.let { expiresAt ->
                    DetailTextLine(
                        stringResource(
                            R.string.github_actions_artifact_detail_expires_at,
                            formatReleaseUpdatedAtNoYear(expiresAt)
                                ?: stringResource(R.string.common_unknown)
                        )
                    )
                }
                artifact.workflowRunHeadSha.ifBlank { run.headSha }.takeIf { it.isNotBlank() }
                    ?.let { sha ->
                        DetailTextLine(
                            stringResource(
                                R.string.github_actions_artifact_detail_commit,
                                sha.take(12)
                            )
                        )
                    }
                artifact.digest.takeIf { it.isNotBlank() }?.let { digest ->
                    DetailTextLine(
                        stringResource(
                            R.string.github_actions_artifact_detail_digest,
                            digest
                        ),
                        maxLines = 2
                    )
                }
            }
            TrustReasonSection(
                reasons = trustSignal.reasons,
                context = context
            )
            ArtifactSelectorReasonSection(detail.artifactMatch.reasons)
            SheetSectionCard {
                val copyPayload = buildArtifactCopyPayload(
                    runHeadSha = run.headSha,
                    artifactHeadSha = artifact.workflowRunHeadSha,
                    digest = artifact.digest
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
                            Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                ActionButtonRow {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.common_download),
                        leadingIcon = appLucideDownloadIcon(),
                        enabled = canAct,
                        modifier = Modifier.weight(1f),
                        onClick = { onDownload(run.id, artifact.id) }
                    )
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.github_actions_action_share),
                        leadingIcon = appLucideShareIcon(),
                        enabled = canAct,
                        modifier = Modifier.weight(1f),
                        onClick = { onShare(run.id, artifact.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubHealthDetailContent(
    item: GitHubTrackedApp,
    state: VersionCheckUi
) {
    val context = LocalContext.current
    val health = buildGitHubRepositoryHealth(item, state)
    SheetContentColumn(verticalSpacing = 10.dp) {
        SheetSummaryCard(
            title = stringResource(R.string.github_health_detail_summary),
            badgeLabel = stringResource(R.string.github_health_score_value, health.score),
            badgeColor = health.level.statusColor()
        ) {
            DetailTextLine("${item.owner}/${item.repo}")
            DetailTextLine(
                stringResource(
                    R.string.github_health_detail_current_version,
                    state.localVersion.ifBlank { stringResource(R.string.common_unknown) }
                )
            )
        }
        SheetSectionTitle(stringResource(R.string.github_health_detail_reason_title))
        SheetSectionCard {
            if (health.reasons.isEmpty()) {
                DetailTextLine(stringResource(R.string.github_health_detail_reason_empty))
            } else {
                health.reasons.forEach { reason ->
                    DetailTextLine(context.getString(reason.labelRes()))
                }
            }
        }
        SheetSectionTitle(stringResource(R.string.github_health_detail_impact_title))
        SheetSectionCard {
            val impactLines = buildGitHubRepositoryHealthImpactLines(health)
            if (impactLines.isEmpty()) {
                DetailTextLine(stringResource(R.string.github_health_detail_impact_empty))
            } else {
                impactLines.forEach { (reason, impact) ->
                    DetailTextLine(
                        stringResource(
                            R.string.github_health_detail_impact_line,
                            if (impact > 0) "+$impact" else impact.toString(),
                            context.getString(reason.labelRes())
                        )
                    )
                }
            }
        }
        SheetSectionTitle(stringResource(R.string.github_health_detail_rule_title))
        SheetSectionCard {
            SheetDescriptionText(stringResource(R.string.github_health_detail_rule_check))
            SheetDescriptionText(stringResource(R.string.github_health_detail_rule_release))
            SheetDescriptionText(stringResource(R.string.github_health_detail_rule_package))
        }
    }
}

@Composable
private fun GitHubReleaseNotesDetailContent(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    assetLoading: Boolean,
    assetError: String,
    backdrop: LayerBackdrop,
    onOpenExternalUrl: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showRawMarkdown by remember(item.id, assetBundle?.tagName) { mutableStateOf(false) }
    val lines = buildGitHubReleaseNotesDetailLines(
        item = item,
        state = state,
        assetBundle = assetBundle
    )
    val rawMarkdown = assetBundle?.releaseNotesBody.orEmpty()
    val releaseUrl = assetBundle?.htmlUrl?.takeIf { it.isNotBlank() }
        ?: buildFallbackReleaseUrl(item, state)
    val copyText = rawMarkdown.ifBlank { lines.joinToString("\n") }
    SheetContentColumn(verticalSpacing = 10.dp) {
        SheetSummaryCard(
            title = assetBundle?.releaseName?.takeIf { it.isNotBlank() }
                ?: state.latestStableName.ifBlank { state.latestPreName.ifBlank { item.repo } },
            badgeLabel = assetBundle?.tagName?.takeIf { it.isNotBlank() }
                ?: state.latestStableRawTag.ifBlank { state.latestPreRawTag.ifBlank { null } },
            badgeColor = GitHubStatusPalette.Active
        ) {
            DetailTextLine("${item.owner}/${item.repo}")
            DetailTextLine(
                if (assetError.isNotBlank()) {
                    assetError
                } else if (assetLoading) {
                    stringResource(R.string.github_release_notes_detail_refreshing)
                } else {
                    stringResource(R.string.github_release_notes_detail_refresh_hint)
                }
            )
        }
        SheetSectionCard {
            ActionButtonRow {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(
                        if (showRawMarkdown) {
                            R.string.github_release_notes_action_preview
                        } else {
                            R.string.github_release_notes_action_raw
                        }
                    ),
                    enabled = rawMarkdown.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = { showRawMarkdown = !showRawMarkdown }
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.common_copy),
                    leadingIcon = osLucideCopyIcon(),
                    enabled = copyText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        copyTextToClipboard(context, "github_release_notes", copyText)
                        Toast.makeText(
                            context,
                            context.getString(R.string.github_release_notes_toast_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            ActionButtonRow {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.github_release_notes_action_open_release),
                    leadingIcon = appLucideExternalLinkIcon(),
                    enabled = releaseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onOpenExternalUrl(
                            releaseUrl,
                            context.getString(R.string.github_error_open_link)
                        )
                    }
                )
            }
        }
        SheetSectionTitle(stringResource(R.string.github_release_notes_detail_body_title))
        SheetSectionCard(verticalSpacing = 10.dp) {
            if (showRawMarkdown && rawMarkdown.isNotBlank()) {
                DetailTextLine(
                    text = rawMarkdown,
                    maxLines = 18,
                    accent = true
                )
            } else if (lines.isEmpty()) {
                SheetDescriptionText(stringResource(R.string.github_release_notes_detail_empty))
            } else {
                lines.forEachIndexed { index, line ->
                    DetailTextLine(
                        text = if (index == 0) line else "• $line",
                        maxLines = if (index == 0) 4 else 8,
                        accent = index == 0
                    )
                }
            }
        }
    }
}

private fun buildFallbackReleaseUrl(
    item: GitHubTrackedApp,
    state: VersionCheckUi
): String {
    val tag = state.latestStableRawTag.ifBlank { state.latestPreRawTag }
    return if (tag.isBlank()) {
        "https://github.com/${item.owner}/${item.repo}/releases"
    } else {
        "https://github.com/${item.owner}/${item.repo}/releases/tag/$tag"
    }
}

private fun buildArtifactCopyPayload(
    runHeadSha: String,
    artifactHeadSha: String,
    digest: String
): String {
    return buildList {
        artifactHeadSha.ifBlank { runHeadSha }.takeIf { it.isNotBlank() }?.let { sha ->
            add("commit: $sha")
        }
        digest.takeIf { it.isNotBlank() }?.let { value ->
            add("digest: $value")
        }
    }.joinToString("\n")
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun TrustReasonSection(
    reasons: List<GitHubApkTrustReason>,
    context: Context
) {
    SheetSectionTitle(stringResource(R.string.github_apk_trust_detail_reason_title))
    SheetSectionCard {
        if (reasons.isEmpty()) {
            DetailTextLine(stringResource(R.string.github_apk_trust_detail_reason_empty))
        } else {
            reasons.forEach { reason ->
                DetailTextLine(context.getString(reason.labelRes()))
            }
        }
    }
}

@Composable
private fun ArtifactSelectorReasonSection(reasons: List<String>) {
    SheetSectionTitle(stringResource(R.string.github_actions_artifact_detail_selector_title))
    SheetSectionCard {
        if (reasons.isEmpty()) {
            DetailTextLine(stringResource(R.string.github_actions_artifact_detail_selector_empty))
        } else {
            reasons.take(6).forEach { reason ->
                DetailTextLine(reason)
            }
        }
    }
}

@Composable
private fun DetailTextLine(
    text: String,
    maxLines: Int = 3,
    accent: Boolean = false
) {
    Text(
        text = text,
        color = if (accent) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = if (accent) AppTypographyTokens.Body.fontSize else AppTypographyTokens.Supporting.fontSize,
        lineHeight = if (accent) AppTypographyTokens.Body.lineHeight else AppTypographyTokens.Supporting.lineHeight,
        fontWeight = if (accent) AppTypographyTokens.BodyEmphasis.fontWeight else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ActionButtonRow(content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

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

private fun GitHubRepositoryHealthReason.labelRes(): Int {
    return when (this) {
        GitHubRepositoryHealthReason.UpdateAvailable -> R.string.github_health_reason_update_available
        GitHubRepositoryHealthReason.PreReleaseRecommended -> R.string.github_health_reason_prerelease
        GitHubRepositoryHealthReason.CheckFailed -> R.string.github_health_reason_check_failed
        GitHubRepositoryHealthReason.MissingPackageName -> R.string.github_health_reason_missing_package
        GitHubRepositoryHealthReason.MissingStableRelease -> R.string.github_health_reason_missing_stable
        GitHubRepositoryHealthReason.LocalMissing -> R.string.github_health_reason_local_missing
        GitHubRepositoryHealthReason.StableDetected -> R.string.github_health_reason_stable_detected
        GitHubRepositoryHealthReason.FreshRelease -> R.string.github_health_reason_fresh_release
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
    }
}
