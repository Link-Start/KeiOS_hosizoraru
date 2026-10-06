@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.feature.github.model.FdroidAppSearchCandidate
import os.kei.feature.github.model.FdroidAppSearchFailure
import os.kei.feature.github.model.FdroidAppSearchRepoReport
import os.kei.feature.github.model.FdroidAppSearchSource
import os.kei.feature.github.model.FdroidRepositoryPreset
import os.kei.feature.github.model.FdroidRepositoryPresets
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSelectedAppCard
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetChoiceCard
import os.kei.ui.page.main.widget.sheet.SheetChoiceCardDensity
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val FdroidCandidateListMaxHeight = 360.dp

@Immutable
private data class FdroidRepoScopeOption(
    val id: String,
    val preset: FdroidRepositoryPreset? = null,
)

@Immutable
private data class FdroidCandidateRowUiState(
    val id: String,
    val candidate: FdroidAppSearchCandidate,
    val selected: Boolean,
)

@Composable
internal fun GitHubTrackEditFdroidDiscoverySection(
    backdrop: Backdrop,
    repoUrlInput: String,
    repoScopeId: String,
    appSearchQuery: String,
    packageNameInput: String,
    selectedApp: InstalledAppItem?,
    candidates: List<FdroidAppSearchCandidate>,
    searchFailures: List<FdroidAppSearchFailure>,
    searchFailuresExpanded: Boolean,
    searchRepoReports: List<FdroidAppSearchRepoReport>,
    selectedCandidate: FdroidAppSearchCandidate?,
    searching: Boolean,
    enabledCommonRepos: List<FdroidRepositoryPreset>,
    repoScopeDropdownExpanded: Boolean,
    onRepoUrlInputChange: (String) -> Unit,
    onRepoScopeIdChange: (String) -> Unit,
    onAppSearchQueryChange: (String) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onSearchByName: () -> Unit,
    onScanFromPackage: () -> Unit,
    onRetryFailures: () -> Unit,
    onSearchFailuresExpandedChange: (Boolean) -> Unit,
    onCandidateSelected: (FdroidAppSearchCandidate) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onRepoScopeDropdownExpandedChange: (Boolean) -> Unit,
) {
    val activeCommonRepos =
        remember(enabledCommonRepos) {
            enabledCommonRepos.ifEmpty {
                FdroidRepositoryPresets.commonSearchReposForIds(
                    FdroidRepositoryPresets.defaultCommonSearchRepoIds
                )
            }
        }
    val scopeOptions = remember(activeCommonRepos) {
        listOf(FdroidRepoScopeOption(FdroidRepositoryPresets.COMMON_ID)) +
            activeCommonRepos.map { preset ->
                FdroidRepoScopeOption(id = preset.id, preset = preset)
            } +
            listOf(
                FdroidRepoScopeOption(
                    id = FdroidRepositoryPresets.ARCHIVE_ID,
                    preset = FdroidRepositoryPresets.Archive,
                ),
                FdroidRepoScopeOption(FdroidRepositoryPresets.CUSTOM_ID),
            )
    }
    val scopeIndex = scopeOptions.indexOfFirst { option -> option.id == repoScopeId }
        .coerceAtLeast(0)
    val selectedScope = scopeOptions.getOrElse(scopeIndex) { scopeOptions.first() }
    val scopeLabels = scopeOptions.map { option -> option.fdroidRepoScopeLabel() }
    val customScope = selectedScope.id == FdroidRepositoryPresets.CUSTOM_ID
    val canSearchByName = !searching && appSearchQuery.isNotBlank()
    val canScanPackage =
        !searching && (packageNameInput.isNotBlank() || selectedApp?.packageName?.isNotBlank() == true)

    SheetSectionHeader(stringResource(R.string.github_track_sheet_section_fdroid_discovery))
    SheetSectionCard {
        SheetControlRow(
            label = stringResource(R.string.github_track_sheet_label_fdroid_repo_scope),
            summary = selectedScope.fdroidRepoScopeSummary(),
        ) {
            AppDropdownSelector(
                selectedText = scopeLabels.getOrElse(scopeIndex) {
                    stringResource(R.string.github_track_sheet_fdroid_repo_scope_common)
                },
                options = scopeLabels,
                selectedIndex = scopeIndex,
                expanded = repoScopeDropdownExpanded,
                onExpandedChange = onRepoScopeDropdownExpandedChange,
                onSelectedIndexChange = { index ->
                    scopeOptions.getOrNull(index)?.let { option ->
                        onRepoScopeIdChange(option.id)
                    }
                },
                backdrop = backdrop,
                popupMaxWidth = 236.dp,
                dropdownItemTextMaxLines = 1,
            )
        }
        SheetDescriptionText(
            text = selectedScope.fdroidRepoScopeDetail(
                repoUrlInput = repoUrlInput,
                activeCommonRepos = activeCommonRepos,
            ),
        )
        if (customScope) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_input_fdroid_custom_repo_title))
            AppLiquidSearchField(
                value = repoUrlInput,
                onValueChange = onRepoUrlInputChange,
                label = stringResource(R.string.github_track_sheet_input_fdroid_custom_repo),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true,
            )
        }
    }

    SheetSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_input_fdroid_app_name_title))
            AppLiquidTextButton(
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                text =
                    if (searching) {
                        stringResource(R.string.github_track_sheet_btn_fdroid_search_running)
                    } else {
                        stringResource(R.string.github_track_sheet_btn_fdroid_search)
                    },
                enabled = canSearchByName,
                onClick = onSearchByName,
                minHeight = 30.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 4.dp,
                textMaxLines = 1,
            )
        }
        AppLiquidSearchField(
            value = appSearchQuery,
            onValueChange = onAppSearchQueryChange,
            label = stringResource(R.string.github_track_sheet_input_fdroid_app_name),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
            onImeActionDone = onSearchByName,
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_summary_fdroid_app_name),
        )
    }

    SheetSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_input_package_title))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.github_track_sheet_btn_select_app),
                    onClick = { onPickerExpandedChange(true) },
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1,
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text =
                        if (searching) {
                            stringResource(R.string.github_track_sheet_btn_fdroid_scan_running)
                        } else {
                            stringResource(
                                if (selectedApp == null) {
                                    R.string.github_track_sheet_btn_fdroid_scan_package
                                } else {
                                    R.string.github_track_sheet_btn_fdroid_scan_installed
                                },
                            )
                        },
                    enabled = canScanPackage,
                    onClick = onScanFromPackage,
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1,
                )
            }
        }
        AppLiquidSearchField(
            value = packageNameInput,
            onValueChange = onPackageNameInputChange,
            label = stringResource(R.string.github_track_sheet_input_package),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
            onImeActionDone = onScanFromPackage,
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_summary_fdroid_installed_scan),
        )
        selectedApp?.let { app ->
            GitHubSelectedAppCard(selectedApp = app)
        }
    }

    if (candidates.isNotEmpty()) {
        FdroidCandidateList(
            candidates = candidates,
            selectedCandidate = selectedCandidate,
            onCandidateSelected = onCandidateSelected,
        )
    }
    if (searchFailures.isNotEmpty()) {
        FdroidSearchFailureNotice(
            backdrop = backdrop,
            failures = searchFailures,
            expanded = searchFailuresExpanded,
            repoReports = searchRepoReports,
            searching = searching,
            onRetryFailures = onRetryFailures,
            onExpandedChange = onSearchFailuresExpandedChange,
        )
    }
}

@Composable
private fun FdroidSearchFailureNotice(
    backdrop: Backdrop,
    failures: List<FdroidAppSearchFailure>,
    expanded: Boolean,
    repoReports: List<FdroidAppSearchRepoReport>,
    searching: Boolean,
    onRetryFailures: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    val visibleFailures = if (expanded) failures else failures.take(1)
    SheetSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetInputTitle(
                stringResource(
                    R.string.github_track_sheet_fdroid_search_partial_failure,
                    failures.size,
                ),
            )
            StatusPill(
                label = stringResource(R.string.common_status_failed),
                color = GitHubStatusPalette.PreRelease,
                size = AppStatusPillSize.Compact,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (repoReports.isNotEmpty()) {
                SheetDescriptionText(
                    text =
                        stringResource(
                            R.string.github_track_sheet_fdroid_search_report_format,
                            repoReports.size,
                            repoReports.sumOf { report -> report.failureCount },
                            fdroidSearchElapsedText(repoReports.maxOf { report -> report.elapsedMillis }),
                        ),
                )
            }
            val fallbackMessage = stringResource(R.string.github_error_fdroid_search_failed)
            visibleFailures.forEach { failure ->
                Text(
                    text =
                        stringResource(
                            R.string.github_track_sheet_fdroid_search_partial_failure_detail_format,
                            failure.repoFailureDisplayName(),
                            failure.message.ifBlank { fallbackMessage },
                        ),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hiddenCount = failures.size - visibleFailures.size
            if (hiddenCount > 0 && !expanded) {
                SheetDescriptionText(
                    text =
                        stringResource(
                            R.string.github_track_sheet_fdroid_search_partial_failure_more_format,
                            hiddenCount,
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (failures.size > 1) {
                AppLiquidTextButton(
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    text =
                        stringResource(
                            if (expanded) {
                                R.string.github_track_sheet_fdroid_search_failure_collapse
                            } else {
                                R.string.github_track_sheet_fdroid_search_failure_expand
                            },
                        ),
                    enabled = !searching,
                    onClick = { onExpandedChange(!expanded) },
                    minHeight = 30.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    textMaxLines = 1,
                )
            }
            AppLiquidTextButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                text =
                    if (searching) {
                        stringResource(R.string.github_track_sheet_btn_fdroid_search_running)
                    } else {
                        stringResource(R.string.github_track_sheet_fdroid_search_failure_retry)
                    },
                enabled = !searching,
                onClick = onRetryFailures,
                minHeight = 30.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 4.dp,
                textMaxLines = 1,
            )
        }
    }
}

@Composable
internal fun FdroidCandidateList(
    candidates: List<FdroidAppSearchCandidate>,
    selectedCandidate: FdroidAppSearchCandidate?,
    onCandidateSelected: (FdroidAppSearchCandidate) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows =
        remember(candidates, selectedCandidate) {
            candidates.map { candidate ->
                FdroidCandidateRowUiState(
                    id = candidate.fdroidCandidateStableId(),
                    candidate = candidate,
                    selected =
                        selectedCandidate != null &&
                            selectedCandidate.fdroidCandidateStableId() ==
                            candidate.fdroidCandidateStableId(),
                )
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_section_fdroid_candidates))
            Text(
                text =
                    stringResource(
                        R.string.github_track_sheet_fdroid_candidate_count_format,
                        candidates.size,
                    ),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_fdroid_candidates_summary),
        )
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .heightIn(max = FdroidCandidateListMaxHeight),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(
                items = rows,
                key = { row -> row.id },
                contentType = { "fdroid_app_search_candidate" },
            ) { row ->
                FdroidCandidateRow(
                    candidate = row.candidate,
                    selected = row.selected,
                    onClick = { onCandidateSelected(row.candidate) },
                )
            }
        }
    }
}

@Composable
internal fun FdroidCandidateRow(
    candidate: FdroidAppSearchCandidate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        gitHubCandidateChoiceColors(
            selected = selected,
            recommended = false,
            isDark = isAppInDarkTheme(),
        )
    SheetChoiceCard(
        title = candidate.displayName,
        summary = candidate.summary.ifBlank { candidate.packageName },
        selected = selected,
        onSelect = onClick,
        modifier = modifier.fillMaxWidth(),
        density = SheetChoiceCardDensity.Compact,
        pressSafePadding = GitHubCandidateChoicePressSafePadding,
        contentPadding = GitHubCandidateChoiceContentPadding,
        selectedAccentColor = GitHubStatusPalette.Update,
        unselectedTitleColor = colors.titleColor,
        summaryColor = MiuixTheme.colorScheme.onBackgroundVariant,
        selectedLabel =
            if (selected) {
                stringResource(R.string.github_track_sheet_fdroid_candidate_selected)
            } else {
                null
            },
        containerColor = colors.containerColor,
        borderColor = colors.borderColor,
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GitHubCandidateStatusPill(
                    label = candidate.repoDisplayName,
                    color = colors.accentColor,
                )
                if (candidate.antiFeatures.isNotEmpty()) {
                    GitHubCandidateStatusPill(
                        label =
                            stringResource(
                                R.string.github_track_sheet_fdroid_candidate_antifeatures_format,
                                candidate.antiFeatures.size,
                            ),
                        color = GitHubStatusPalette.PreRelease,
                    )
                }
                GitHubCandidateStatusPill(
                    label =
                        stringResource(
                            when (candidate.source) {
                                FdroidAppSearchSource.PackageApi -> {
                                    R.string.github_track_sheet_fdroid_candidate_source_package_api
                                }

                                FdroidAppSearchSource.OfficialSearchApi -> {
                                    R.string.github_track_sheet_fdroid_candidate_source_api
                                }

                                FdroidAppSearchSource.RepositoryIndex -> {
                                    R.string.github_track_sheet_fdroid_candidate_source_index
                                }
                            },
                        ),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        },
        showIndicator = false,
        details = {
            Text(
                text = candidate.fdroidCandidateMetaText(),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun FdroidRepoScopeOption.fdroidRepoScopeLabel(): String =
    when (id) {
        FdroidRepositoryPresets.COMMON_ID -> {
            stringResource(R.string.github_track_sheet_fdroid_repo_scope_common)
        }

        FdroidRepositoryPresets.CUSTOM_ID -> {
            stringResource(R.string.github_track_sheet_fdroid_repo_scope_custom)
        }

        else -> preset?.displayName.orEmpty()
    }

@Composable
private fun FdroidRepoScopeOption.fdroidRepoScopeSummary(): String =
    when (id) {
        FdroidRepositoryPresets.COMMON_ID -> {
            stringResource(R.string.github_track_sheet_summary_fdroid_repo_scope_common)
        }

        FdroidRepositoryPresets.CUSTOM_ID -> {
            stringResource(R.string.github_track_sheet_summary_fdroid_repo_scope_custom)
        }

        else -> {
            stringResource(
                R.string.github_track_sheet_summary_fdroid_repo_scope_preset,
                preset?.displayName.orEmpty(),
            )
        }
    }

@Composable
private fun FdroidRepoScopeOption.fdroidRepoScopeDetail(
    repoUrlInput: String,
    activeCommonRepos: List<FdroidRepositoryPreset>,
): String =
    when (id) {
        FdroidRepositoryPresets.COMMON_ID -> {
            stringResource(
                R.string.github_track_sheet_detail_fdroid_repo_scope_common,
                activeCommonRepos.joinToString(", ") { preset ->
                    preset.displayName
                },
            )
        }

        FdroidRepositoryPresets.CUSTOM_ID -> {
            stringResource(R.string.github_track_sheet_detail_fdroid_repo_scope_custom)
        }

        else -> {
            stringResource(
                R.string.github_track_sheet_detail_fdroid_repo_scope_preset,
                preset?.repoUrl ?: repoUrlInput,
            )
        }
    }

@Composable
private fun FdroidAppSearchCandidate.fdroidCandidateMetaText(): String {
    val versionText =
        latestVersionName
            .takeIf { it.isNotBlank() }
            ?.let { name ->
                if (latestVersionCode >= 0) {
                    stringResource(
                        R.string.github_track_sheet_fdroid_candidate_versions_format,
                        name,
                        latestVersionCode,
                        versionCount,
                    )
                } else {
                    stringResource(
                        R.string.github_track_sheet_fdroid_candidate_versions_name_format,
                        name,
                        versionCount,
                    )
                }
            }
    return listOfNotNull(
        repoDisplayName.takeIf { it.isNotBlank() },
        packageName.takeIf { it.isNotBlank() },
        versionText,
    ).joinToString(" · ")
}

private fun FdroidAppSearchCandidate.fdroidCandidateStableId(): String =
    repoUrl.trim().trimEnd('/') + "|" + packageName.lowercase()

private fun FdroidAppSearchFailure.repoFailureDisplayName(): String {
    FdroidRepositoryPresets.presetForRepoUrl(repoUrl)?.displayName?.let { displayName ->
        if (displayName.isNotBlank()) return displayName
    }
    return repoUrl
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .ifBlank { repoUrl }
}

@Composable
private fun fdroidSearchElapsedText(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    return if (safeMillis < 1_000L) {
        stringResource(R.string.github_track_sheet_fdroid_search_elapsed_ms, safeMillis)
    } else {
        stringResource(
            R.string.github_track_sheet_fdroid_search_elapsed_seconds,
            safeMillis / 1_000,
            (safeMillis % 1_000) / 100,
        )
    }
}
