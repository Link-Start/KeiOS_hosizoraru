package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val StarImportChipHorizontalPadding = 12.dp
private val StarImportChipVerticalPadding = 6.dp
private val StarImportChipMinHeight = 38.dp
private val StarImportStatusChipMinHeight = 30.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StarImportListControlCard(
    filterInput: String,
    viewFilter: StarImportViewFilter,
    qualityFilters: Set<GitHubStarImportQuality>,
    conflictStrategy: StarImportConflictStrategy,
    qualityFilterCounts: Map<GitHubStarImportQuality, Int>,
    filteredCount: Int,
    visibleImportableCount: Int,
    visibleRecommendedCount: Int,
    visibleVerifiedApkCount: Int,
    selectedCount: Int,
    verifiedApkCount: Int,
    checkingCount: Int,
    verifySelectedEnabled: Boolean,
    verifyVisibleEnabled: Boolean,
    importEnabled: Boolean,
    importing: Boolean,
    onFilterInputChange: (String) -> Unit,
    onViewFilterChange: (StarImportViewFilter) -> Unit,
    onQualityFilterToggle: (GitHubStarImportQuality) -> Unit,
    onConflictStrategyChange: (StarImportConflictStrategy) -> Unit,
    onVerifySelected: () -> Unit,
    onVerifyVisible: () -> Unit,
    onSelectRecommendedVisible: () -> Unit,
    onSelectVerifiedVisible: () -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onImport: () -> Unit
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val canSelectPrimary = visibleRecommendedCount > 0 || visibleImportableCount > 0
    val canVerifyPrimary = verifySelectedEnabled || verifyVisibleEnabled
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_controls_title),
        subtitle = stringResource(
            R.string.github_star_import_controls_summary_format,
            filteredCount,
            selectedCount
        ),
        sectionIcon = appLucideListIcon(),
        showIndication = false,
        headerEndActions = {
            StatusPill(
                label = stringResource(
                    R.string.github_star_import_verified_count_pill,
                    verifiedApkCount
                ),
                color = if (verifiedApkCount > 0) {
                    GitHubStatusPalette.Update
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
                size = AppStatusPillSize.Compact
            )
            AppLiquidIconButton(
                backdrop = null,
                icon = appLucideFilterIcon(),
                contentDescription = stringResource(R.string.github_star_import_filter_options),
                onClick = { advancedExpanded = !advancedExpanded },
                width = 36.dp,
                height = 36.dp,
                modifier = Modifier.size(36.dp),
                variant = if (advancedExpanded) GlassVariant.SheetAction else GlassVariant.Content,
                iconTint = if (advancedExpanded) {
                    GitHubStatusPalette.Update
                } else {
                    MiuixTheme.colorScheme.primary
                }
            )
        }
    ) {
        AppLiquidSearchField(
            value = filterInput,
            onValueChange = onFilterInputChange,
            label = stringResource(R.string.github_star_import_filter_label),
            backdrop = null,
            variant = GlassVariant.Content,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        StarImportViewFilterRow(
            selected = viewFilter,
            onSelectedChange = onViewFilterChange
        )
        StarImportActiveFilterPills(
            viewFilter = viewFilter,
            qualityFilters = qualityFilters,
            conflictStrategy = conflictStrategy,
            qualityFilterCounts = qualityFilterCounts,
            selectedCount = selectedCount,
            checkingCount = checkingCount,
            visibleVerifiedApkCount = visibleVerifiedApkCount
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidTextButton(
                backdrop = null,
                text = if (importing) {
                    stringResource(R.string.github_star_import_status_importing)
                } else {
                    stringResource(R.string.github_star_import_action_import_selected)
                },
                onClick = onImport,
                modifier = Modifier.weight(1.28f),
                enabled = importEnabled,
                variant = GlassVariant.SheetAction,
                leadingIcon = appLucideConfirmIcon(),
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_select_short),
                onClick = {
                    if (visibleRecommendedCount > 0) {
                        onSelectRecommendedVisible()
                    } else {
                        onSelectVisible()
                    }
                },
                modifier = Modifier.weight(0.78f),
                enabled = canSelectPrimary && !importing,
                variant = GlassVariant.Content,
                leadingIcon = appLucideConfirmIcon(),
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_verify_short),
                onClick = {
                    if (verifySelectedEnabled) {
                        onVerifySelected()
                    } else {
                        onVerifyVisible()
                    }
                },
                modifier = Modifier.weight(0.78f),
                enabled = canVerifyPrimary,
                variant = GlassVariant.Content,
                leadingIcon = appLucideRefreshIcon(),
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
        if (advancedExpanded) {
            StarImportAdvancedControls(
                qualityFilters = qualityFilters,
                qualityFilterCounts = qualityFilterCounts,
                conflictStrategy = conflictStrategy,
                visibleImportableCount = visibleImportableCount,
                visibleVerifiedApkCount = visibleVerifiedApkCount,
                selectedCount = selectedCount,
                verifySelectedEnabled = verifySelectedEnabled,
                verifyVisibleEnabled = verifyVisibleEnabled,
                importing = importing,
                onQualityFilterToggle = onQualityFilterToggle,
                onConflictStrategyChange = onConflictStrategyChange,
                onVerifySelected = onVerifySelected,
                onVerifyVisible = onVerifyVisible,
                onSelectRecommendedVisible = onSelectRecommendedVisible,
                onSelectVerifiedVisible = onSelectVerifiedVisible,
                onSelectVisible = onSelectVisible,
                onClearSelection = onClearSelection
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarImportViewFilterRow(
    selected: StarImportViewFilter,
    onSelectedChange: (StarImportViewFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StarImportViewFilter.entries.forEach { filter ->
            StarImportFilterButton(
                filter = filter,
                selected = selected == filter,
                onClick = onSelectedChange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarImportActiveFilterPills(
    viewFilter: StarImportViewFilter,
    qualityFilters: Set<GitHubStarImportQuality>,
    conflictStrategy: StarImportConflictStrategy,
    qualityFilterCounts: Map<GitHubStarImportQuality, Int>,
    selectedCount: Int,
    checkingCount: Int,
    visibleVerifiedApkCount: Int
) {
    val activeQualities = qualityFilters.ifEmpty { GitHubStarImportQuality.entries.toSet() }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StarImportActiveStatusPill(
            label = stringResource(viewFilter.labelRes),
            color = GitHubStatusPalette.Active
        )
        StarImportActiveStatusPill(
            label = stringResource(conflictStrategy.labelRes),
            color = MiuixTheme.colorScheme.primary
        )
        activeQualities.take(2).forEach { quality ->
            StarImportActiveStatusPill(
                label = stringResource(
                    R.string.github_star_import_quality_chip_format,
                    stringResource(quality.labelRes()),
                    qualityFilterCounts[quality] ?: 0
                ),
                color = starImportQualityColor(quality)
            )
        }
        if (activeQualities.size > 2) {
            StarImportActiveStatusPill(
                label = stringResource(
                    R.string.github_star_import_filter_more_count,
                    activeQualities.size - 2
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
        if (selectedCount > 0) {
            StarImportActiveStatusPill(
                label = stringResource(
                    R.string.github_star_import_selected_count_pill,
                    selectedCount
                ),
                color = GitHubStatusPalette.Update
            )
        }
        if (visibleVerifiedApkCount > 0) {
            StarImportActiveStatusPill(
                label = stringResource(
                    R.string.github_star_import_visible_verified_pill,
                    visibleVerifiedApkCount
                ),
                color = GitHubStatusPalette.Update
            )
        }
        if (checkingCount > 0) {
            StarImportActiveStatusPill(
                label = stringResource(
                    R.string.github_star_import_checking_count_pill,
                    checkingCount
                ),
                color = GitHubStatusPalette.Active
            )
        }
    }
}

@Composable
private fun StarImportActiveStatusPill(
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    StatusPill(
        label = label,
        color = color,
        modifier = Modifier.defaultMinSize(minHeight = StarImportStatusChipMinHeight),
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarImportAdvancedControls(
    qualityFilters: Set<GitHubStarImportQuality>,
    qualityFilterCounts: Map<GitHubStarImportQuality, Int>,
    conflictStrategy: StarImportConflictStrategy,
    visibleImportableCount: Int,
    visibleVerifiedApkCount: Int,
    selectedCount: Int,
    verifySelectedEnabled: Boolean,
    verifyVisibleEnabled: Boolean,
    importing: Boolean,
    onQualityFilterToggle: (GitHubStarImportQuality) -> Unit,
    onConflictStrategyChange: (StarImportConflictStrategy) -> Unit,
    onVerifySelected: () -> Unit,
    onVerifyVisible: () -> Unit,
    onSelectRecommendedVisible: () -> Unit,
    onSelectVerifiedVisible: () -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.github_star_import_quality_filter_label),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GitHubStarImportQuality.entries.forEach { quality ->
                val selected = quality in qualityFilters
                AppLiquidTextButton(
                    backdrop = null,
                    text = stringResource(
                        R.string.github_star_import_quality_chip_format,
                        stringResource(quality.labelRes()),
                        qualityFilterCounts[quality] ?: 0
                    ),
                    onClick = { onQualityFilterToggle(quality) },
                    textColor = if (selected) {
                        starImportQualityColor(quality)
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                    containerColor = if (selected) starImportQualityColor(quality) else null,
                    leadingIcon = if (selected) appLucideConfirmIcon() else null,
                    iconTint = starImportQualityColor(quality),
                    variant = if (selected) GlassVariant.SheetAction else GlassVariant.Content,
                    minHeight = StarImportChipMinHeight,
                    horizontalPadding = StarImportChipHorizontalPadding,
                    verticalPadding = StarImportChipVerticalPadding,
                    textSize = AppTypographyTokens.Body.fontSize,
                    textLineHeight = AppTypographyTokens.Body.lineHeight,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportConflictStrategy.entries.forEach { strategy ->
                AppLiquidTextButton(
                    backdrop = null,
                    text = stringResource(strategy.labelRes),
                    onClick = { onConflictStrategyChange(strategy) },
                    modifier = Modifier.weight(1f),
                    variant = if (strategy == conflictStrategy) {
                        GlassVariant.SheetAction
                    } else {
                        GlassVariant.Content
                    },
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_select_recommended),
                enabled = visibleImportableCount > 0 && !importing,
                onClick = onSelectRecommendedVisible,
                modifier = Modifier.weight(1f)
            )
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_select_verified),
                enabled = visibleVerifiedApkCount > 0 && !importing,
                onClick = onSelectVerifiedVisible,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_select_visible),
                enabled = visibleImportableCount > 0 && !importing,
                onClick = onSelectVisible,
                modifier = Modifier.weight(1f)
            )
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_clear_selection),
                enabled = selectedCount > 0 && !importing,
                onClick = onClearSelection,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_verify_selected),
                enabled = verifySelectedEnabled,
                onClick = onVerifySelected,
                modifier = Modifier.weight(1f)
            )
            StarImportCompactAction(
                text = stringResource(R.string.github_star_import_action_verify_visible),
                enabled = verifyVisibleEnabled,
                onClick = onVerifyVisible,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StarImportCompactAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppLiquidTextButton(
        backdrop = null,
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = GlassVariant.Content,
        minHeight = StarImportChipMinHeight,
        horizontalPadding = StarImportChipHorizontalPadding,
        verticalPadding = StarImportChipVerticalPadding,
        textSize = AppTypographyTokens.Body.fontSize,
        textLineHeight = AppTypographyTokens.Body.lineHeight,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StarImportFilterButton(
    filter: StarImportViewFilter,
    selected: Boolean,
    onClick: (StarImportViewFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    AppLiquidTextButton(
        backdrop = null,
        text = stringResource(filter.labelRes),
        onClick = { onClick(filter) },
        modifier = modifier,
        textColor = if (selected) GitHubStatusPalette.Update else MiuixTheme.colorScheme.primary,
        containerColor = if (selected) GitHubStatusPalette.Update else null,
        leadingIcon = if (selected) appLucideConfirmIcon() else null,
        iconTint = GitHubStatusPalette.Update,
        variant = if (selected) GlassVariant.SheetAction else GlassVariant.Content,
        minHeight = StarImportChipMinHeight,
        horizontalPadding = StarImportChipHorizontalPadding,
        verticalPadding = StarImportChipVerticalPadding,
        textSize = AppTypographyTokens.Body.fontSize,
        textLineHeight = AppTypographyTokens.Body.lineHeight,
        textMaxLines = 1,
        textOverflow = TextOverflow.Clip
    )
}
