package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun StarImportSourceCard(
    source: StarImportUiSource,
    tokenAvailable: Boolean,
    usernameInput: String,
    listUrlInput: String,
    loading: Boolean,
    importing: Boolean,
    sourceReady: Boolean,
    onSourceChange: (StarImportUiSource) -> Unit,
    onUsernameInputChange: (String) -> Unit,
    onListUrlInputChange: (String) -> Unit,
    onLoadPreview: () -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_source_title),
        subtitle = stringResource(R.string.github_star_import_source_summary),
        sectionIcon = appLucideHeartIcon(),
        showIndication = false,
        headerEndActions = {
            StatusPill(
                label = stringResource(source.labelRes),
                color = GitHubStatusPalette.Update
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportSourceButton(
                source = StarImportUiSource.MyStars,
                selected = source == StarImportUiSource.MyStars,
                onClick = { onSourceChange(StarImportUiSource.MyStars) },
                modifier = Modifier.weight(1f)
            )
            StarImportSourceButton(
                source = StarImportUiSource.PublicUser,
                selected = source == StarImportUiSource.PublicUser,
                onClick = { onSourceChange(StarImportUiSource.PublicUser) },
                modifier = Modifier.weight(1f)
            )
            StarImportSourceButton(
                source = StarImportUiSource.ListUrl,
                selected = source == StarImportUiSource.ListUrl,
                onClick = { onSourceChange(StarImportUiSource.ListUrl) },
                modifier = Modifier.weight(1f)
            )
        }
        when (source) {
            StarImportUiSource.MyStars -> {
                StarImportInfoLine(
                    label = stringResource(R.string.github_star_import_token_label),
                    value = stringResource(
                        if (tokenAvailable) {
                            R.string.github_star_import_token_ready
                        } else {
                            R.string.github_star_import_token_missing
                        }
                    ),
                    color = if (tokenAvailable) GitHubStatusPalette.Update else GitHubStatusPalette.Error
                )
            }

            StarImportUiSource.PublicUser -> {
                AppLiquidSearchField(
                    value = usernameInput,
                    onValueChange = onUsernameInputChange,
                    label = stringResource(R.string.github_star_import_username_label),
                    backdrop = null,
                    variant = GlassVariant.Content,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }

            StarImportUiSource.ListUrl -> {
                AppLiquidSearchField(
                    value = listUrlInput,
                    onValueChange = onListUrlInputChange,
                    label = stringResource(R.string.github_star_import_list_url_label),
                    backdrop = null,
                    variant = GlassVariant.Content,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_requirement_label),
            value = stringResource(source.requirementMessageRes),
            color = if (sourceReady) {
                GitHubStatusPalette.Update
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            }
        )
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_sample_label),
            value = stringResource(source.sampleRes),
            color = MiuixTheme.colorScheme.onBackground
        )
        AppLiquidTextButton(
            backdrop = null,
            text = if (loading) {
                stringResource(R.string.github_star_import_action_loading)
            } else {
                stringResource(R.string.github_star_import_action_load)
            },
            onClick = onLoadPreview,
            enabled = sourceReady && !loading && !importing,
            variant = GlassVariant.SheetAction,
            leadingIcon = appLucideListIcon(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StarImportSourceButton(
    source: StarImportUiSource,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = GitHubStatusPalette.Update
    AppLiquidTextButton(
        backdrop = null,
        text = stringResource(source.labelRes),
        onClick = onClick,
        modifier = modifier,
        textColor = if (selected) activeColor else MiuixTheme.colorScheme.primary,
        containerColor = if (selected) activeColor else null,
        leadingIcon = if (selected) appLucideConfirmIcon() else null,
        iconTint = activeColor,
        variant = if (selected) GlassVariant.SheetAction else GlassVariant.Content,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun StarImportStatusCard(
    preview: GitHubStarredRepositoryImportPreview?,
    loading: Boolean,
    loadingProgress: Float,
    loadingPhase: String,
    importing: Boolean,
    error: String?,
    selectedCount: Int,
    discoveredListCount: Int
) {
    val title = when {
        loading -> stringResource(R.string.github_star_import_status_loading)
        importing -> stringResource(R.string.github_star_import_status_importing)
        error != null -> stringResource(R.string.github_star_import_status_error)
        preview != null -> stringResource(R.string.github_star_import_status_ready)
        else -> stringResource(R.string.github_star_import_status_waiting)
    }
    val subtitle = when {
        error != null -> error
        preview != null -> stringResource(
            R.string.github_star_import_status_preview_format,
            preview.totalFetchedCount,
            preview.importableCount,
            preview.alreadyTrackedCount,
            selectedCount
        )

        discoveredListCount > 0 -> stringResource(
            R.string.github_star_import_status_lists_ready_format,
            discoveredListCount
        )

        else -> stringResource(R.string.github_star_import_status_waiting_summary)
    }
    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        sectionIcon = appLucideListIcon(),
        showIndication = false,
        headerEndActions = {
            StatusPill(
                label = when {
                    error != null -> stringResource(R.string.common_status_failed)
                    loading || importing -> stringResource(R.string.common_status_running)
                    preview != null -> stringResource(R.string.common_available)
                    else -> stringResource(R.string.common_not_loaded)
                },
                color = when {
                    error != null -> GitHubStatusPalette.Error
                    loading || importing -> GitHubStatusPalette.Active
                    preview != null -> GitHubStatusPalette.Update
                    else -> MiuixTheme.colorScheme.onBackgroundVariant
                }
            )
        }
    ) {
        if (loading) {
            LiquidLinearProgressBar(
                progress = { loadingProgress.coerceIn(0f, 1f) },
                activeColor = GitHubStatusPalette.Active,
                contentDescription = loadingPhase.ifBlank {
                    stringResource(R.string.github_star_import_status_loading)
                }
            )
            if (loadingPhase.isNotBlank()) {
                Text(
                    text = loadingPhase,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (preview != null) {
            StarImportInfoLine(
                label = stringResource(R.string.github_star_import_source_label),
                value = preview.sourceLabel,
                color = MiuixTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
internal fun StarImportStarListPickerCard(
    lists: List<GitHubStarListSummary>,
    loading: Boolean,
    onSelect: (GitHubStarListSummary) -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_lists_title),
        subtitle = stringResource(R.string.github_star_import_lists_summary_format, lists.size),
        sectionIcon = appLucideListIcon(),
        showIndication = false
    ) {
        lists.forEach { list ->
            StarImportListChoiceButton(
                list = list,
                enabled = !loading,
                onClick = { onSelect(list) }
            )
        }
    }
}

@Composable
private fun StarImportListChoiceButton(
    list: GitHubStarListSummary,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AppLiquidTextButton(
        backdrop = null,
        text = if (list.repositoryCount >= 0) {
            stringResource(
                R.string.github_star_import_list_choice_format,
                list.name,
                list.repositoryCount
            )
        } else {
            list.name
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        variant = GlassVariant.Content,
        leadingIcon = appLucideListIcon(),
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

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
                label = stringResource(R.string.github_star_import_action_import_selected),
                color = if (importEnabled) {
                    GitHubStatusPalette.Update
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                }
            )
        }
    ) {
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_verification_overview_label),
            value = stringResource(
                R.string.github_star_import_verification_overview_format,
                verifiedApkCount,
                checkingCount
            ),
            color = if (verifiedApkCount > 0) {
                GitHubStatusPalette.Update
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            }
        )
        AppLiquidSearchField(
            value = filterInput,
            onValueChange = onFilterInputChange,
            label = stringResource(R.string.github_star_import_filter_label),
            backdrop = null,
            variant = GlassVariant.Content,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        StarImportQualityFilterRow(
            qualityFilters = qualityFilters,
            qualityFilterCounts = qualityFilterCounts,
            onQualityFilterToggle = onQualityFilterToggle
        )
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
            StarImportFilterButton(
                filter = StarImportViewFilter.All,
                selected = viewFilter == StarImportViewFilter.All,
                onClick = onViewFilterChange,
                modifier = Modifier.weight(1f)
            )
            StarImportFilterButton(
                filter = StarImportViewFilter.Importable,
                selected = viewFilter == StarImportViewFilter.Importable,
                onClick = onViewFilterChange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportFilterButton(
                filter = StarImportViewFilter.VerifiedApk,
                selected = viewFilter == StarImportViewFilter.VerifiedApk,
                onClick = onViewFilterChange,
                modifier = Modifier.weight(1f)
            )
            StarImportFilterButton(
                filter = StarImportViewFilter.Selected,
                selected = viewFilter == StarImportViewFilter.Selected,
                onClick = onViewFilterChange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StarImportFilterButton(
                filter = StarImportViewFilter.Tracked,
                selected = viewFilter == StarImportViewFilter.Tracked,
                onClick = onViewFilterChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_select_recommended),
                onClick = onSelectRecommendedVisible,
                modifier = Modifier.weight(1f),
                enabled = visibleRecommendedCount > 0 && !importing,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_select_verified),
                onClick = onSelectVerifiedVisible,
                modifier = Modifier.weight(1f),
                enabled = visibleVerifiedApkCount > 0 && !importing,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_select_visible),
                onClick = onSelectVisible,
                modifier = Modifier.weight(1f),
                enabled = visibleImportableCount > 0 && !importing,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_verify_visible),
                onClick = onVerifyVisible,
                modifier = Modifier.weight(1f),
                enabled = verifyVisibleEnabled,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_verify_selected),
                onClick = onVerifySelected,
                modifier = Modifier.weight(1f),
                enabled = verifySelectedEnabled,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_star_import_action_clear_selection),
                onClick = onClearSelection,
                modifier = Modifier.weight(1f),
                enabled = selectedCount > 0 && !importing,
                variant = GlassVariant.Content,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
        AppLiquidTextButton(
            backdrop = null,
            text = if (importing) {
                stringResource(R.string.github_star_import_status_importing)
            } else {
                stringResource(R.string.github_star_import_action_import_selected)
            },
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            enabled = importEnabled,
            variant = GlassVariant.SheetAction,
            leadingIcon = appLucideConfirmIcon()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarImportQualityFilterRow(
    qualityFilters: Set<GitHubStarImportQuality>,
    qualityFilterCounts: Map<GitHubStarImportQuality, Int>,
    onQualityFilterToggle: (GitHubStarImportQuality) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.github_star_import_quality_filter_label),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun StarImportEmptyCard() {
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_empty_title),
        subtitle = stringResource(R.string.github_star_import_empty_subtitle),
        sectionIcon = appLucideInfoIcon(),
        showIndication = false
    ) {}
}

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
    AppFeatureCard(
        title = candidate.repository.fullName,
        subtitle = candidate.repository.description.ifBlank {
            stringResource(R.string.github_star_import_candidate_no_description)
        },
        sectionIcon = appLucideHeartIcon(),
        titleColor = accent,
        onClick = if (disabled) null else onToggle,
        showIndication = !disabled,
        headerEndActions = {
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
                }
            )
        }
    ) {
        Text(
            text = stringResource(
                R.string.github_star_import_quality_line_format,
                stringResource(quality.labelRes()),
                stringResource(quality.summaryRes())
            ),
            color = starImportQualityColor(quality),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        StarImportApkVerificationLine(state = apkVerificationState)
        Text(
            text = stringResource(
                R.string.github_star_import_candidate_meta_format,
                candidate.repository.language.ifBlank { stringResource(R.string.common_not_used) },
                candidate.repository.starCount.formatStarCount(),
                candidate.score
            ),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = candidate.trackedApp.repoUrl,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StarImportApkVerificationLine(state: StarImportApkVerificationUiState?) {
    val verification = state?.verification
    val text = when {
        state?.checking == true -> stringResource(R.string.github_star_import_apk_checking)
        verification == null -> stringResource(R.string.github_star_import_apk_not_checked)
        verification.status == GitHubStarImportApkVerificationStatus.HasApk &&
                verification.packageName.isNotBlank() -> stringResource(
            R.string.github_star_import_apk_has_apk_with_package_format,
            verification.apkAssetCount,
            verification.sampleAssetName.ifBlank { verification.releaseTag },
            verification.packageName
        )

        verification.status == GitHubStarImportApkVerificationStatus.HasApk ->
            stringResource(
                R.string.github_star_import_apk_has_apk_format,
                verification.apkAssetCount,
                verification.sampleAssetName.ifBlank { verification.releaseTag }
            )

        verification.status == GitHubStarImportApkVerificationStatus.NoApk -> stringResource(
            R.string.github_star_import_apk_no_apk_format,
            verification.releaseTag.ifBlank { stringResource(R.string.common_not_used) }
        )

        else -> stringResource(
            R.string.github_star_import_apk_failed_format,
            verification.errorMessage.ifBlank { stringResource(R.string.common_status_failed) }
        )
    }
    val color = when {
        state?.checking == true -> GitHubStatusPalette.Active
        verification?.status == GitHubStarImportApkVerificationStatus.HasApk -> GitHubStatusPalette.Update
        verification?.status == GitHubStarImportApkVerificationStatus.NoApk -> MiuixTheme.colorScheme.onBackgroundVariant
        verification?.status == GitHubStarImportApkVerificationStatus.Failed -> GitHubStatusPalette.Error
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    Text(
        text = text,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
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

@Composable
private fun StarImportInfoLine(
    label: String,
    value: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
