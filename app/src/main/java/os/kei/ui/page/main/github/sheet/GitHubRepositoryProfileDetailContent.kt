package os.kei.ui.page.main.github.sheet

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubRepositoryHealth
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtNoYear
import os.kei.ui.page.main.github.buildGitHubRepositoryHealth
import os.kei.ui.page.main.github.buildGitHubRepositoryHealthImpactLines
import os.kei.ui.page.main.github.labelRes
import os.kei.ui.page.main.github.profile.GitHubRepositoryProfileSourceUiRow
import os.kei.ui.page.main.github.profile.GitHubRepositoryProfileUiMapper
import os.kei.ui.page.main.github.profile.GitHubRepositoryProfileUiRow
import os.kei.ui.page.main.github.profile.GitHubRepositoryProfileUiSection
import os.kei.ui.page.main.github.profile.GitHubRepositoryProfileUiSummary
import os.kei.ui.page.main.github.profile.gitHubRepositoryProfileUiText
import os.kei.ui.page.main.github.repositoryHealthLabelRes
import os.kei.ui.page.main.github.repositoryHealthStatusColor
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubHealthDetailContent(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    refreshing: Boolean = false
) {
    val context = LocalContext.current
    val health = buildGitHubRepositoryHealth(item, state)
    val profileUi = GitHubRepositoryProfileUiMapper.build(state.repositoryProfile)
    SheetContentColumn(verticalSpacing = 10.dp) {
        RepositoryProfileOverviewCard(
            item = item,
            state = state,
            health = health,
            profileUi = profileUi,
            refreshing = refreshing
        )
        SheetSectionTitle(stringResource(R.string.github_health_detail_diagnosis_title))
        GitHubHealthDiagnosisCard(health = health, context = context)
        SheetSectionTitle(stringResource(R.string.github_health_detail_profile_title))
        if (profileUi == null) {
            SheetSectionCard {
                SheetDescriptionText(stringResource(R.string.github_health_detail_profile_empty))
            }
        } else {
            profileUi.sections.forEach { section ->
                ProfileSignalSection(section)
            }
            SourceAvailabilitySection(profileUi.sourceRows)
        }
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
internal fun RepositoryProfileOverviewCard(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    health: GitHubRepositoryHealth,
    profileUi: GitHubRepositoryProfileUiSummary?,
    refreshing: Boolean = false
) {
    val levelLabel = stringResource(health.level.repositoryHealthLabelRes())
    SheetSummaryCard(
        title = stringResource(R.string.github_health_detail_summary),
        badgeLabel = stringResource(
            R.string.github_health_score_level_value,
            health.score,
            levelLabel
        ),
        badgeColor = health.level.repositoryHealthStatusColor()
    ) {
        DetailInfoRow(
            label = stringResource(R.string.github_release_notes_detail_repo),
            value = profileUi?.ownerRepo ?: "${item.owner}/${item.repo}"
        )
        DetailInfoRow(
            label = stringResource(R.string.github_item_label_local_version),
            value = state.localVersion.ifBlank { stringResource(R.string.common_unknown) }
        )
        val latestRelease = state.latestStableRawTag
            .ifBlank { state.latestPreRawTag }
            .ifBlank { stringResource(R.string.common_unknown) }
        DetailInfoRow(
            label = stringResource(R.string.github_profile_label_latest_release),
            value = latestRelease
        )
        profileUi?.fetchedAtMillis
            ?.takeIf { it > 0L }
            ?.let { fetchedAt ->
                DetailInfoRow(
                    label = stringResource(R.string.github_profile_section_sources),
                    value = if (refreshing) {
                        stringResource(R.string.common_loading)
                    } else {
                        formatReleaseUpdatedAtNoYear(fetchedAt)
                            ?: stringResource(R.string.common_unknown)
                    }
                )
            }
    }
}

@Composable
internal fun ProfileSignalSection(section: GitHubRepositoryProfileUiSection) {
    SheetSectionTitle(stringResource(section.titleRes))
    SheetSectionCard(
        verticalSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        section.rows.forEach { row ->
            ProfileSignalRow(row)
        }
    }
}

@Composable
private fun ProfileSignalRow(row: GitHubRepositoryProfileUiRow) {
    val rowColor = row.level?.repositoryHealthStatusColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(0.44f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(row.labelRes),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ProfileMetaLine(
                sourceLabelRes = row.sourceLabelRes,
                confidenceLabelRes = row.confidenceLabelRes,
                fetchedAtMillis = row.fetchedAtMillis
            )
        }
        Row(
            modifier = Modifier.weight(0.56f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gitHubRepositoryProfileUiText(row.value),
                color = rowColor ?: MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = if (rowColor != null) AppTypographyTokens.BodyEmphasis.fontWeight else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            row.level?.let { level ->
                StatusPill(
                    label = stringResource(level.repositoryHealthLabelRes()),
                    color = level.repositoryHealthStatusColor(),
                    size = AppStatusPillSize.Compact,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileMetaLine(
    sourceLabelRes: Int,
    confidenceLabelRes: Int,
    fetchedAtMillis: Long
) {
    val parts = buildList {
        if (sourceLabelRes != 0) {
            add(stringResource(sourceLabelRes))
        }
        if (confidenceLabelRes != 0) {
            add(stringResource(confidenceLabelRes))
        }
        formatReleaseUpdatedAtNoYear(fetchedAtMillis)?.let(::add)
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.72f),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun SourceAvailabilitySection(rows: List<GitHubRepositoryProfileSourceUiRow>) {
    SheetSectionTitle(stringResource(R.string.github_profile_section_sources))
    SheetSectionCard(
        verticalSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (rows.isEmpty()) {
            SheetDescriptionText(stringResource(R.string.github_health_detail_profile_empty))
        } else {
            rows.forEach { row ->
                SourceAvailabilityRow(row)
            }
        }
    }
}

@Composable
private fun SourceAvailabilityRow(row: GitHubRepositoryProfileSourceUiRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = stringResource(row.sourceLabelRes),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SourceAvailabilityMeta(row)
            row.message.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        StatusPill(
            label = stringResource(row.statusLabelRes),
            color = row.level.repositoryHealthStatusColor(),
            size = AppStatusPillSize.Compact,
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SourceAvailabilityMeta(row: GitHubRepositoryProfileSourceUiRow) {
    val parts = buildList {
        row.elapsed?.let { add(gitHubRepositoryProfileUiText(it)) }
        if (row.fromCache) {
            add(stringResource(R.string.github_profile_source_from_cache))
        }
        if (row.required) {
            add(stringResource(R.string.github_profile_source_required))
        }
        formatReleaseUpdatedAtNoYear(row.fetchedAtMillis)?.let(::add)
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.72f),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
