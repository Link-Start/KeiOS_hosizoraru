package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun GitHubStarImportConfirmDialog(
    candidates: List<GitHubRepositoryImportCandidate>,
    verificationStates: Map<String, StarImportApkVerificationUiState>,
    importing: Boolean,
    onDismissRequest: () -> Unit,
    onConfirmImport: () -> Unit
) {
    if (candidates.isEmpty()) return
    val summary = buildStarImportConfirmSummary(candidates, verificationStates)
    val groups = buildStarImportConfirmGroups(candidates, verificationStates)
    val expandedGroups = remember(candidates, verificationStates) {
        mutableStateMapOf<StarImportConfirmGroupKey, Boolean>().apply {
            groups.forEach { group -> put(group.key, group.initiallyExpanded) }
        }
    }
    WindowDialog(
        show = true,
        title = stringResource(R.string.github_star_import_confirm_title),
        summary = stringResource(
            R.string.github_star_import_confirm_summary_format,
            candidates.size,
            summary.hasApkCount,
            summary.unverifiedCount
        ),
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))
            groups.forEach { group ->
                StarImportConfirmGroupCard(
                    group = group,
                    expanded = expandedGroups[group.key] == true,
                    onExpandedChange = { expanded -> expandedGroups[group.key] = expanded }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (summary.riskyCount > 0) {
                Text(
                    text = stringResource(
                        R.string.github_star_import_confirm_risky_format,
                        summary.riskyCount
                    ),
                    color = GitHubStatusPalette.Error
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                    enabled = !importing
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = if (importing) {
                        stringResource(R.string.github_star_import_status_importing)
                    } else {
                        stringResource(R.string.github_star_import_confirm_action)
                    },
                    containerColor = GitHubStatusPalette.Update,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                    variant = GlassVariant.SheetPrimaryAction,
                    onClick = onConfirmImport,
                    enabled = !importing
                )
            }
        }
    }
}

@Composable
internal fun GitHubStarImportExitConfirmDialog(
    show: Boolean,
    selectedCount: Int,
    onDismissRequest: () -> Unit,
    onConfirmExit: () -> Unit
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = stringResource(R.string.github_star_import_exit_confirm_title),
        summary = stringResource(
            R.string.github_star_import_exit_confirm_summary_format,
            selectedCount
        ),
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_star_import_exit_confirm_keep),
                    onClick = onDismissRequest
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_star_import_exit_confirm_action),
                    containerColor = GitHubStatusPalette.Error,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                    variant = GlassVariant.SheetPrimaryAction,
                    onClick = onConfirmExit
                )
            }
        }
    }
}

@Composable
private fun StarImportConfirmGroupCard(
    group: StarImportConfirmGroup,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    AppSurfaceCard(
        containerColor = MiuixTheme.colorScheme.surfaceContainer,
        borderColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.16f),
        onClick = { onExpandedChange(!expanded) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(group.titleRes),
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        label = group.candidates.size.toString(),
                        color = group.color
                    )
                    Text(
                        text = stringResource(
                            if (expanded) {
                                R.string.github_star_import_confirm_group_collapse
                            } else {
                                R.string.github_star_import_confirm_group_expand
                            }
                        ),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight
                    )
                }
            }
            Text(
                text = stringResource(group.summaryRes),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
            if (expanded) {
                group.candidates.take(STAR_IMPORT_GROUP_PREVIEW_LIMIT).forEach { candidate ->
                    Text(
                        text = candidate.repository.fullName,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val hiddenCount = group.candidates.size - STAR_IMPORT_GROUP_PREVIEW_LIMIT
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.github_star_import_confirm_group_more,
                            hiddenCount
                        ),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight
                    )
                }
            }
        }
    }
}

private fun buildStarImportConfirmSummary(
    candidates: List<GitHubRepositoryImportCandidate>,
    verificationStates: Map<String, StarImportApkVerificationUiState>
): StarImportConfirmSummary {
    val qualityCounts = candidates.groupingBy { candidate ->
        GitHubStarImportClassifier.classify(candidate)
    }.eachCount()
    val verificationCounts = candidates.groupingBy { candidate ->
        verificationStates[candidate.trackedApp.id]?.verification?.status
    }.eachCount()
    val otherPlatformCount = qualityCounts[GitHubStarImportQuality.OtherPlatform] ?: 0
    val archivedOrForkCount = qualityCounts[GitHubStarImportQuality.ArchivedOrFork] ?: 0
    val noApkCount = verificationCounts[GitHubStarImportApkVerificationStatus.NoApk] ?: 0
    val failedCount = verificationCounts[GitHubStarImportApkVerificationStatus.Failed] ?: 0
    return StarImportConfirmSummary(
        likelyAndroidCount = qualityCounts[GitHubStarImportQuality.LikelyAndroid] ?: 0,
        needsReviewCount = qualityCounts[GitHubStarImportQuality.NeedsReview] ?: 0,
        otherPlatformCount = otherPlatformCount,
        archivedOrForkCount = archivedOrForkCount,
        hasApkCount = verificationCounts[GitHubStarImportApkVerificationStatus.HasApk] ?: 0,
        noApkCount = noApkCount,
        unverifiedCount = verificationCounts[null] ?: 0,
        riskyCount = otherPlatformCount + archivedOrForkCount + noApkCount + failedCount
    )
}

private fun buildStarImportConfirmGroups(
    candidates: List<GitHubRepositoryImportCandidate>,
    verificationStates: Map<String, StarImportApkVerificationUiState>
): List<StarImportConfirmGroup> {
    val grouped = candidates.groupBy { candidate ->
        val verificationStatus = verificationStates[candidate.trackedApp.id]?.verification?.status
        val quality = GitHubStarImportClassifier.classify(candidate)
        when {
            verificationStatus == GitHubStarImportApkVerificationStatus.HasApk ->
                StarImportConfirmGroupKey.VerifiedApk

            verificationStatus == GitHubStarImportApkVerificationStatus.NoApk ||
                    verificationStatus == GitHubStarImportApkVerificationStatus.Failed ->
                StarImportConfirmGroupKey.NoApkOrFailed

            quality == GitHubStarImportQuality.OtherPlatform ||
                    quality == GitHubStarImportQuality.ArchivedOrFork ->
                StarImportConfirmGroupKey.OtherPlatformOrArchived

            else -> StarImportConfirmGroupKey.Unverified
        }
    }
    return StarImportConfirmGroupKey.entries.mapNotNull { key ->
        val items = grouped[key].orEmpty()
        if (items.isEmpty()) return@mapNotNull null
        StarImportConfirmGroup(
            key = key,
            candidates = items.sortedBy { it.repository.fullName.lowercase() },
            titleRes = key.titleRes,
            summaryRes = key.summaryRes,
            color = key.color,
            initiallyExpanded = key.initiallyExpanded
        )
    }
}

private enum class StarImportConfirmGroupKey(
    val titleRes: Int,
    val summaryRes: Int,
    val color: androidx.compose.ui.graphics.Color,
    val initiallyExpanded: Boolean
) {
    NoApkOrFailed(
        titleRes = R.string.github_star_import_confirm_group_no_apk,
        summaryRes = R.string.github_star_import_confirm_group_no_apk_summary,
        color = GitHubStatusPalette.Error,
        initiallyExpanded = true
    ),
    OtherPlatformOrArchived(
        titleRes = R.string.github_star_import_confirm_group_other,
        summaryRes = R.string.github_star_import_confirm_group_other_summary,
        color = GitHubStatusPalette.Cache,
        initiallyExpanded = true
    ),
    Unverified(
        titleRes = R.string.github_star_import_confirm_group_unverified,
        summaryRes = R.string.github_star_import_confirm_group_unverified_summary,
        color = GitHubStatusPalette.Active,
        initiallyExpanded = false
    ),
    VerifiedApk(
        titleRes = R.string.github_star_import_confirm_group_verified,
        summaryRes = R.string.github_star_import_confirm_group_verified_summary,
        color = GitHubStatusPalette.Update,
        initiallyExpanded = false
    )
}

private data class StarImportConfirmGroup(
    val key: StarImportConfirmGroupKey,
    val candidates: List<GitHubRepositoryImportCandidate>,
    val titleRes: Int,
    val summaryRes: Int,
    val color: androidx.compose.ui.graphics.Color,
    val initiallyExpanded: Boolean
)

private data class StarImportConfirmSummary(
    val likelyAndroidCount: Int,
    val needsReviewCount: Int,
    val otherPlatformCount: Int,
    val archivedOrForkCount: Int,
    val hasApkCount: Int,
    val noApkCount: Int,
    val unverifiedCount: Int,
    val riskyCount: Int
)

private const val STAR_IMPORT_GROUP_PREVIEW_LIMIT = 6
