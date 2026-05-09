package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StarImportCandidateCard(
    candidate: GitHubRepositoryImportCandidate,
    selected: Boolean,
    trackedSelectable: Boolean,
    apkVerificationState: StarImportApkVerificationUiState?,
    onToggle: () -> Unit
) {
    val disabled = candidate.alreadyTracked && !trackedSelectable
    val quality = GitHubStarImportClassifier.classify(candidate)
    val accent = when {
        disabled -> MiuixTheme.colorScheme.onBackgroundVariant
        selected -> GitHubStatusPalette.Update
        quality == GitHubStarImportQuality.LikelyAndroid -> GitHubStatusPalette.Active
        quality == GitHubStarImportQuality.OtherPlatform -> MiuixTheme.colorScheme.onBackgroundVariant
        else -> MiuixTheme.colorScheme.primary
    }
    AppSurfaceCard(
        onClick = if (disabled) null else onToggle,
        showIndication = !disabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = candidate.repository.fullName,
                        color = accent,
                        fontSize = AppTypographyTokens.Body.fontSize,
                        lineHeight = AppTypographyTokens.Body.lineHeight,
                        fontWeight = AppTypographyTokens.Body.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = candidate.repository.description.ifBlank {
                            stringResource(R.string.github_star_import_candidate_no_description)
                        },
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Caption.fontSize,
                        lineHeight = AppTypographyTokens.Caption.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    label = when {
                        disabled -> stringResource(R.string.github_star_import_candidate_tracked)
                        selected -> stringResource(R.string.github_star_import_candidate_selected)
                        else -> stringResource(R.string.github_star_import_candidate_optional)
                    },
                    color = when {
                        disabled -> MiuixTheme.colorScheme.onBackgroundVariant
                        selected -> GitHubStatusPalette.Update
                        else -> GitHubStatusPalette.Active
                    },
                    size = AppStatusPillSize.Compact
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    label = stringResource(quality.labelRes()),
                    color = starImportQualityColor(quality),
                    size = AppStatusPillSize.Compact
                )
                if (candidate.repository.starCount > 0) {
                    StatusPill(
                        label = stringResource(
                            R.string.github_star_import_candidate_stars_pill,
                            candidate.repository.starCount.formatStarCount()
                        ),
                        color = MiuixTheme.colorScheme.primary,
                        size = AppStatusPillSize.Compact
                    )
                }
                when {
                    candidate.repository.archived -> StatusPill(
                        label = stringResource(R.string.github_star_import_candidate_archived_pill),
                        color = GitHubStatusPalette.Error,
                        size = AppStatusPillSize.Compact
                    )

                    candidate.repository.fork -> StatusPill(
                        label = stringResource(R.string.github_star_import_candidate_fork_pill),
                        color = GitHubStatusPalette.PreRelease,
                        size = AppStatusPillSize.Compact
                    )
                }
                candidate.repository.language.takeIf { it.isNotBlank() }?.let { language ->
                    StatusPill(
                        label = language,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        size = AppStatusPillSize.Compact
                    )
                }
                StarImportApkVerificationPill(state = apkVerificationState)
                candidate.starImportPackageName(apkVerificationState)?.let { packageName ->
                    StatusPill(
                        label = packageName,
                        color = GitHubStatusPalette.Active,
                        size = AppStatusPillSize.Compact
                    )
                }
            }
        }
    }
}

@Composable
private fun StarImportApkVerificationPill(state: StarImportApkVerificationUiState?) {
    val verification = state?.verification
    val label = when {
        state?.checking == true -> stringResource(R.string.github_star_import_apk_pill_checking)
        verification == null -> stringResource(R.string.github_star_import_apk_pill_unchecked)
        verification.status == GitHubStarImportApkVerificationStatus.HasApk ->
            stringResource(R.string.github_star_import_apk_pill_count, verification.apkAssetCount)

        verification.status == GitHubStarImportApkVerificationStatus.NoApk ->
            stringResource(R.string.github_star_import_apk_pill_none)

        else -> stringResource(R.string.github_star_import_apk_pill_failed)
    }
    val color = when {
        state?.checking == true -> GitHubStatusPalette.Active
        verification?.status == GitHubStarImportApkVerificationStatus.HasApk -> GitHubStatusPalette.Update
        verification?.status == GitHubStarImportApkVerificationStatus.Failed -> GitHubStatusPalette.Error
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    StatusPill(
        label = label,
        color = color,
        size = AppStatusPillSize.Compact
    )
}

@Composable
internal fun starImportQualityColor(quality: GitHubStarImportQuality): Color {
    return when (quality) {
        GitHubStarImportQuality.LikelyAndroid -> GitHubStatusPalette.Update
        GitHubStarImportQuality.NeedsReview -> GitHubStatusPalette.Active
        GitHubStarImportQuality.OtherPlatform -> MiuixTheme.colorScheme.onBackgroundVariant
        GitHubStarImportQuality.ArchivedOrFork -> GitHubStatusPalette.Error
    }
}

private fun GitHubRepositoryImportCandidate.starImportPackageName(
    state: StarImportApkVerificationUiState?
): String? {
    val verifiedPackage = state
        ?.verification
        ?.takeIf { it.status == GitHubStarImportApkVerificationStatus.HasApk }
        ?.packageName
        ?.trim()
        .orEmpty()
    return trackedApp.packageName.trim()
        .ifBlank { verifiedPackage }
        .takeIf { it.isNotBlank() }
}
