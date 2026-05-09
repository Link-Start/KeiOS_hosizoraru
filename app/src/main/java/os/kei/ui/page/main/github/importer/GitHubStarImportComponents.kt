package os.kei.ui.page.main.github.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Icon
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
    AppSurfaceCard(
        showIndication = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = appLucideHeartIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MiuixTheme.colorScheme.onBackground
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.github_star_import_source_title),
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = AppTypographyTokens.CompactTitle.fontSize,
                        lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                        fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    label = stringResource(source.labelRes),
                    color = GitHubStatusPalette.Update
                )
            }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                if (tokenAvailable) {
                                    R.string.github_star_import_token_ready
                                } else {
                                    R.string.github_star_import_token_missing
                                }
                            ),
                            color = if (tokenAvailable) GitHubStatusPalette.Update else GitHubStatusPalette.Error,
                            modifier = Modifier.weight(1f),
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        StarImportLoadButton(
                            loading = loading,
                            importing = importing,
                            enabled = sourceReady,
                            onClick = onLoadPreview
                        )
                    }
                }

                StarImportUiSource.PublicUser -> {
                    StarImportInputActionRow(
                        value = usernameInput,
                        onValueChange = onUsernameInputChange,
                        label = stringResource(R.string.github_star_import_username_label),
                        loading = loading,
                        importing = importing,
                        enabled = sourceReady,
                        onLoadPreview = onLoadPreview
                    )
                }

                StarImportUiSource.ListUrl -> {
                    StarImportInputActionRow(
                        value = listUrlInput,
                        onValueChange = onListUrlInputChange,
                        label = stringResource(R.string.github_star_import_list_url_label),
                        loading = loading,
                        importing = importing,
                        enabled = sourceReady,
                        onLoadPreview = onLoadPreview
                    )
                }
            }
        }
    }
}

@Composable
private fun StarImportInputActionRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    loading: Boolean,
    importing: Boolean,
    enabled: Boolean,
    onLoadPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLiquidSearchField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            backdrop = null,
            modifier = Modifier.weight(1f),
            variant = GlassVariant.Content,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        StarImportLoadButton(
            loading = loading,
            importing = importing,
            enabled = enabled,
            onClick = onLoadPreview
        )
    }
}

@Composable
private fun StarImportLoadButton(
    loading: Boolean,
    importing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AppLiquidTextButton(
        backdrop = null,
        text = if (loading) {
            stringResource(R.string.github_star_import_action_loading)
        } else {
            stringResource(R.string.github_star_import_action_load_short)
        },
        onClick = onClick,
        enabled = enabled && !loading && !importing,
        variant = GlassVariant.SheetAction,
        leadingIcon = appLucideListIcon(),
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun StarImportSourceGuideCard(
    source: StarImportUiSource,
    sourceReady: Boolean
) {
    var expanded by rememberSaveable(source) { mutableStateOf(true) }
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_source_guide_title),
        subtitle = "",
        sectionIcon = appLucideInfoIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        showIndication = false,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
        contentVerticalSpacing = 8.dp
    ) {
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_requirement_label),
            value = stringResource(source.requirementMessageRes),
            color = if (sourceReady) {
                GitHubStatusPalette.Update
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            },
            maxLines = 3
        )
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_sample_label),
            value = stringResource(source.sampleRes),
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 3
        )
        StarImportInfoLine(
            label = stringResource(R.string.github_star_import_input_hint_label),
            value = stringResource(source.inputHintRes),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 4
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
internal fun StarImportEmptyCard() {
    AppFeatureCard(
        title = stringResource(R.string.github_star_import_empty_title),
        subtitle = stringResource(R.string.github_star_import_empty_subtitle),
        sectionIcon = appLucideInfoIcon(),
        showIndication = false
    ) {}
}

@Composable
private fun StarImportInfoLine(
    label: String,
    value: String,
    color: Color,
    maxLines: Int = 3
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
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
