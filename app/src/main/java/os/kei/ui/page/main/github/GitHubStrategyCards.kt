@file:Suppress("FunctionName")

package os.kei.ui.page.main.github

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SheetChoiceCard
import os.kei.ui.page.main.widget.sheet.SheetExpandableCard
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubStrategyGuideCard(
    guide: GitHubStrategyGuide,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val accent = guide.option.accentColor()
    SheetChoiceCard(
        title = guide.option.label,
        summary = guide.summary,
        selected = selected,
        onSelect = onSelect,
        accentColor = accent,
        modifier = Modifier.fillMaxWidth(),
        details = {
            Text(
                text = stringResource(R.string.github_strategy_guide_pros, guide.pros.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_cons, guide.cons.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_requirement, guide.requirement),
                color = accent,
                fontWeight = FontWeight.Medium,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
            )
        },
    )
}

@Composable
internal fun GitHubActionsStrategyGuideCard(
    guide: GitHubActionsStrategyGuide,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val accent = guide.option.accentColor()
    SheetChoiceCard(
        title = guide.option.label,
        summary = guide.summary,
        selected = selected,
        onSelect = onSelect,
        accentColor = accent,
        modifier = Modifier.fillMaxWidth(),
        details = {
            Text(
                text = stringResource(R.string.github_strategy_guide_pros, guide.pros.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_cons, guide.cons.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_requirement, guide.requirement),
                color = accent,
                fontWeight = FontWeight.Medium,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
            )
        },
    )
}

@Composable
internal fun GitHubStrategyDraftSummaryCard(
    selectedStrategy: GitHubLookupStrategyOption,
    selectedActionsStrategy: GitHubActionsLookupStrategyOption,
    tokenInput: String,
    trackedCount: Int,
    changed: Boolean,
) {
    val accent = selectedStrategy.accentColor()
    val tokenUsed =
        selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken ||
            selectedActionsStrategy == GitHubActionsLookupStrategyOption.GitHubApiToken
    val tokenStatusLabel =
        when {
            !tokenUsed -> stringResource(R.string.common_not_used)
            tokenInput.isNotBlank() -> stringResource(R.string.common_filled)
            else -> stringResource(R.string.common_guest)
        }
    val tokenStatusColor =
        when {
            !tokenUsed -> MiuixTheme.colorScheme.onBackgroundVariant
            tokenInput.isNotBlank() -> GitHubStatusPalette.Update
            else -> GitHubStatusPalette.PreRelease
        }

    SheetSummaryCard(
        title = stringResource(R.string.github_strategy_card_title_draft),
        accentColor = MiuixTheme.colorScheme.onBackground,
        badgeLabel =
            if (changed) {
                stringResource(R.string.common_pending_save)
            } else {
                stringResource(R.string.github_strategy_badge_same)
            },
        badgeColor = if (changed) accent else MiuixTheme.colorScheme.onBackgroundVariant,
        containerColor =
            if (changed) {
                MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
            } else {
                null
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_release_option),
            value = selectedStrategy.label,
            valueColor = accent,
            emphasized = true,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_actions_option),
            value = selectedActionsStrategy.label,
            valueColor = selectedActionsStrategy.accentColor(),
            emphasized = true,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_token),
            value = tokenStatusLabel,
            valueColor = tokenStatusColor,
            emphasized = tokenUsed,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_impact),
            value =
                if (trackedCount > 0) {
                    stringResource(R.string.github_strategy_impact_recheck_count, trackedCount)
                } else {
                    stringResource(R.string.github_strategy_impact_no_track)
                },
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp,
        )
    }
}

@Composable
internal fun GitHubRecommendedTokenGuideCard(
    guide: GitHubRecommendedTokenGuide,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    SheetExpandableCard(
        title = stringResource(R.string.github_strategy_card_title_recommended),
        collapsedSummary = guide.collapsedSummary,
        expandedSummary = guide.summary,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        accentColor = GitHubStatusPalette.Update,
        badgeLabel = stringResource(R.string.github_strategy_badge_least_privilege),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val accent = GitHubStatusPalette.Update
        guide.fields.forEach { field ->
            GitHubCompactInfoRow(
                label = field.label,
                value = field.value,
                valueColor = if (field.emphasized) accent else MiuixTheme.colorScheme.onBackground,
                emphasized = field.emphasized,
                titleMinWidth = 52.dp,
            )
        }
        guide.notes.forEach { note ->
            Text(
                text = note,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }
    }
}
