package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.ui.page.main.github.GitHubStatusPalette
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
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_quality_likely_android),
                value = summary.likelyAndroidCount,
                color = GitHubStatusPalette.Update
            )
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_quality_needs_review),
                value = summary.needsReviewCount,
                color = GitHubStatusPalette.Active
            )
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_quality_other_platform),
                value = summary.otherPlatformCount,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_quality_archived_or_fork),
                value = summary.archivedOrForkCount,
                color = GitHubStatusPalette.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_apk_confirm_has_apk),
                value = summary.hasApkCount,
                color = GitHubStatusPalette.Update
            )
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_apk_confirm_no_apk),
                value = summary.noApkCount,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            StarImportConfirmMetricRow(
                label = stringResource(R.string.github_star_import_apk_confirm_unverified),
                value = summary.unverifiedCount,
                color = GitHubStatusPalette.Active
            )
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
private fun StarImportConfirmMetricRow(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackground
        )
        StatusPill(
            label = value.toString(),
            color = color
        )
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
