package os.kei.ui.page.main.github.sheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
import os.kei.ui.page.main.github.GitHubRepositoryHealth
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
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.markdown.AppMarkdownContent
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
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
    onRefreshReleaseNotes: (GitHubTrackedApp, VersionCheckUi) -> Unit
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
        SheetSectionTitle(stringResource(R.string.github_health_detail_diagnosis_title))
        GitHubHealthDiagnosisCard(health = health, context = context)
        SheetSectionTitle(stringResource(R.string.github_health_detail_rule_title))
        SheetSectionCard(
            verticalSpacing = 4.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            DetailTextLine(
                text = stringResource(R.string.github_health_detail_rule_check),
                maxLines = Int.MAX_VALUE
            )
            DetailTextLine(
                text = stringResource(R.string.github_health_detail_rule_release),
                maxLines = Int.MAX_VALUE
            )
            DetailTextLine(
                text = stringResource(R.string.github_health_detail_rule_package),
                maxLines = Int.MAX_VALUE
            )
            DetailTextLine(
                text = stringResource(R.string.github_health_detail_rule_scope),
                maxLines = Int.MAX_VALUE
            )
        }
    }
}

@Composable
private fun GitHubHealthDiagnosisCard(
    health: GitHubRepositoryHealth,
    context: Context
) {
    val impactLines = buildGitHubRepositoryHealthImpactLines(health)
    SheetSectionCard(
        verticalSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (impactLines.isEmpty()) {
            DetailTextLine(stringResource(R.string.github_health_detail_diagnosis_empty))
        } else {
            impactLines.forEach { (reason, impact) ->
                GitHubHealthDiagnosisLine(
                    impact = impact,
                    reason = context.getString(reason.labelRes())
                )
            }
        }
    }
}

@Composable
private fun GitHubHealthDiagnosisLine(
    impact: Int,
    reason: String
) {
    val impactColor = when {
        impact > 0 -> GitHubStatusPalette.Update
        impact < 0 -> GitHubStatusPalette.Error
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            label = if (impact > 0) "+$impact" else impact.toString(),
            color = impactColor,
            size = AppStatusPillSize.Compact,
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
        )
        DetailTextLine(
            text = reason,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GitHubReleaseNotesDetailContent(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    assetLoading: Boolean,
    assetError: String
) {
    val lines = buildGitHubReleaseNotesDetailLines(
        item = item,
        state = state,
        assetBundle = assetBundle
    )
    val rawMarkdown = assetBundle?.releaseNotesBody.orEmpty()
    SheetContentColumn(verticalSpacing = 10.dp) {
        SheetSummaryCard(
            title = assetBundle?.releaseName?.takeIf { it.isNotBlank() }
                ?: state.latestStableName.ifBlank { state.latestPreName.ifBlank { item.repo } },
            badgeLabel = assetBundle?.tagName?.takeIf { it.isNotBlank() }
                ?: state.latestStableRawTag.ifBlank { state.latestPreRawTag.ifBlank { null } },
            badgeColor = GitHubStatusPalette.Active
        ) {
            DetailInfoRow(
                label = stringResource(R.string.github_release_notes_detail_repo),
                value = "${item.owner}/${item.repo}"
            )
            DetailInfoRow(
                label = stringResource(R.string.github_release_notes_detail_source),
                value = releaseNotesSourceLabel(assetBundle?.fetchSource.orEmpty())
            )
            when {
                assetError.isNotBlank() -> DetailTextLine(assetError)
                assetLoading -> DetailTextLine(
                    stringResource(R.string.github_release_notes_detail_refreshing)
                )
            }
        }
        SheetSectionTitle(stringResource(R.string.github_release_notes_detail_body_title))
        SheetSectionCard(verticalSpacing = 10.dp) {
            if (rawMarkdown.isNotBlank()) {
                CompositionLocalProvider(LocalTextCopyExpandedOverride provides true) {
                    AppMarkdownContent(
                        markdown = rawMarkdown,
                        titleColor = MiuixTheme.colorScheme.onBackground,
                        subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
                        accentColor = MiuixTheme.colorScheme.primary,
                        codeContainerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
                        preserveLineBreaks = true
                    )
                }
            } else if (lines.isEmpty()) {
                SheetDescriptionText(stringResource(R.string.github_release_notes_detail_empty))
            } else {
                CompositionLocalProvider(LocalTextCopyExpandedOverride provides true) {
                    lines.forEachIndexed { index, line ->
                        DetailTextLine(
                            text = if (index == 0) line else "• $line",
                            maxLines = Int.MAX_VALUE,
                            accent = index == 0
                        )
                    }
                }
            }
        }
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

@Composable
private fun DetailInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.28f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.72f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
    accent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = if (accent) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = if (accent) AppTypographyTokens.Body.fontSize else AppTypographyTokens.Supporting.fontSize,
        lineHeight = if (accent) AppTypographyTokens.Body.lineHeight else AppTypographyTokens.Supporting.lineHeight,
        fontWeight = if (accent) AppTypographyTokens.BodyEmphasis.fontWeight else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun releaseNotesSourceLabel(source: String): String {
    return when (source) {
        "api" -> stringResource(R.string.github_release_notes_source_api)
        "html" -> stringResource(R.string.github_release_notes_source_atom)
        else -> stringResource(R.string.common_unknown)
    }
}

@Composable
private fun ActionButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
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
        GitHubRepositoryHealthReason.RepositoryArchived -> R.string.github_health_reason_repository_archived
        GitHubRepositoryHealthReason.RepositoryDisabled -> R.string.github_health_reason_repository_disabled
        GitHubRepositoryHealthReason.RepositoryFork -> R.string.github_health_reason_repository_fork
        GitHubRepositoryHealthReason.ForkUpstreamArchived -> R.string.github_health_reason_fork_upstream_archived
        GitHubRepositoryHealthReason.ForkBehindUpstream -> R.string.github_health_reason_fork_behind_upstream
        GitHubRepositoryHealthReason.ForkMaintainedIndependently -> R.string.github_health_reason_fork_independent
        GitHubRepositoryHealthReason.ForkTracksUpstream -> R.string.github_health_reason_fork_tracks_upstream
        GitHubRepositoryHealthReason.StaleRepositoryActivity -> R.string.github_health_reason_stale_repository_activity
        GitHubRepositoryHealthReason.StaleRelease -> R.string.github_health_reason_stale_release
        GitHubRepositoryHealthReason.ActionsHealthy -> R.string.github_health_reason_actions_healthy
        GitHubRepositoryHealthReason.ActionsFailing -> R.string.github_health_reason_actions_failing
        GitHubRepositoryHealthReason.AndroidAssetsDetected -> R.string.github_health_reason_android_assets_detected
        GitHubRepositoryHealthReason.MissingAndroidAssets -> R.string.github_health_reason_missing_android_assets
        GitHubRepositoryHealthReason.CommunityProfileComplete -> R.string.github_health_reason_community_complete
        GitHubRepositoryHealthReason.MissingReadme -> R.string.github_health_reason_missing_readme
        GitHubRepositoryHealthReason.MissingLicense -> R.string.github_health_reason_missing_license
        GitHubRepositoryHealthReason.LocalPackageMatched -> R.string.github_health_reason_local_package_matched
        GitHubRepositoryHealthReason.LocalPackageMismatch -> R.string.github_health_reason_local_package_mismatch
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
