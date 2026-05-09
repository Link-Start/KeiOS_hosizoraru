package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RepositoryScanCandidateList(
    candidates: List<GitHubPackageRepositoryScanCandidate>,
    selectedRepoUrl: String,
    onCandidateClick: (GitHubPackageRepositoryScanCandidate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_section_repo_candidates))
            Text(
                text = stringResource(
                    R.string.github_track_sheet_repo_candidate_count_format,
                    candidates.size
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_repo_candidates_summary)
        )
        candidates.forEachIndexed { index, candidate ->
            RepositoryScanCandidateRow(
                candidate = candidate,
                recommended = index == 0,
                selected = candidate.trackedApp.repoUrl.sameRepoUrlAs(selectedRepoUrl),
                onClick = { onCandidateClick(candidate) }
            )
        }
    }
}

@Composable
private fun RepositoryScanCandidateRow(
    candidate: GitHubPackageRepositoryScanCandidate,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = when {
        selected -> GitHubStatusPalette.Update
        recommended -> GitHubStatusPalette.Active
        else -> MiuixTheme.colorScheme.primary
    }
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(12.dp)
    val metaText = candidate.repositoryCandidateMetaText()
    val starCountText = candidate.repository.starCount.takeIf { it > 0 }?.formatCompactCount()
    val starLabel = starCountText?.let {
        stringResource(R.string.github_track_sheet_repo_candidate_stars_format, it)
    }
    SheetControlRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = if (isDark) 0.08f else 0.1f))
            .border(
                width = 0.8.dp,
                color = accent.copy(alpha = if (selected || recommended) 0.34f else 0.18f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        minHeight = 68.dp,
        labelContent = {
            Text(
                text = candidate.repository.fullName,
                color = if (selected) {
                    GitHubStatusPalette.Update
                } else {
                    MiuixTheme.colorScheme.onBackground
                },
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = candidate.repository.description.ifBlank { candidate.trackedApp.repoUrl },
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f),
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                selected -> StatusPill(
                    label = stringResource(R.string.github_track_sheet_repo_candidate_selected),
                    color = GitHubStatusPalette.Update,
                    size = AppStatusPillSize.Compact
                )

                recommended -> StatusPill(
                    label = stringResource(R.string.github_track_sheet_repo_candidate_recommended),
                    color = GitHubStatusPalette.Active,
                    size = AppStatusPillSize.Compact
                )
            }
            if (starLabel != null) {
                StatusPill(
                    label = starLabel,
                    color = MiuixTheme.colorScheme.primary,
                    size = AppStatusPillSize.Compact
                )
            }
            if (candidate.repository.archived) {
                StatusPill(
                    label = stringResource(R.string.github_track_sheet_repo_candidate_archived),
                    color = GitHubStatusPalette.Error,
                    size = AppStatusPillSize.Compact
                )
            } else if (candidate.repository.fork) {
                StatusPill(
                    label = stringResource(R.string.github_track_sheet_repo_candidate_fork),
                    color = GitHubStatusPalette.PreRelease,
                    size = AppStatusPillSize.Compact
                )
            }
        }
    }
}

@Composable
private fun GitHubPackageRepositoryScanCandidate.repositoryCandidateMetaText(): String {
    return listOf(
        releaseTag.ifBlank { null },
        assetName.ifBlank { null }
    ).filterNotNull().joinToString(" · ")
}

private fun Int.formatCompactCount(): String {
    return when {
        this >= 100_000 -> "${this / 1000}k"
        this >= 10_000 -> "${(this / 1000.0).formatOneDecimal()}k"
        this >= 1_000 -> "${(this / 1000.0).formatOneDecimal()}k"
        else -> toString()
    }
}

private fun Double.formatOneDecimal(): String {
    val value = kotlin.math.floor(this * 10.0) / 10.0
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

private fun String.sameRepoUrlAs(other: String): Boolean {
    return trim().trimEnd('/').equals(other.trim().trimEnd('/'), ignoreCase = true)
}
