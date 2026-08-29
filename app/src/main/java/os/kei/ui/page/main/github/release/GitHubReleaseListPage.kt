package os.kei.ui.page.main.github.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseListEntry
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.markdown.AppMarkdownContent
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.CompositionLocalProvider

/**
 * A repository's release history, as its own page.
 *
 * The tracked card cannot carry this. It already shows local, stable and pre-release sections plus an
 * asset panel, and the release list is a second list with its own notes and files — the 发行日志 sheet
 * that came before this had to degrade notes to preview lines to fit, which is the same space problem one
 * level down. A page also gets the markdown renderer the sheet could not afford.
 *
 * Ten per page, which is what GitHub's own release list shows. Most repositories' recent history fits;
 * the ones that publish a pre-release per CI run get a next page rather than a 30-deep first load.
 */
@Composable
internal fun GitHubReleaseListPage(
    trackId: String,
    onBack: () -> Unit,
) {
    val viewModel: GitHubReleaseListViewModel =
        viewModel(
            key = "github-releases-$trackId",
            factory =
                viewModelFactory {
                    initializer { GitHubReleaseListViewModel(trackId = trackId) }
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val uriHandler = LocalUriHandler.current
    // Session-only, like the BA office cards: a visit starts compact, and scrolling keeps what was opened.
    val expanded = remember { mutableStateMapOf<String, Unit>() }

    AppPageScaffold(
        title = uiState.repositoryLabel.ifBlank { stringResource(R.string.github_release_page_title) },
        modifier =
            Modifier
                .fillMaxSize()
                .pageRootTestTag(KeiOsTestTags.GitHubReleasePageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            topExtra = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
        ) {
            when {
                uiState.unsupported ->
                    item(key = "release-unsupported") {
                        GitHubReleaseNotice(text = stringResource(R.string.github_release_unsupported))
                    }

                uiState.loading && uiState.entries.isEmpty() ->
                    item(key = "release-loading") {
                        GitHubReleaseNotice(text = stringResource(R.string.github_release_loading))
                    }

                uiState.errorMessage.isNotBlank() && uiState.entries.isEmpty() ->
                    item(key = "release-error") {
                        GitHubReleaseNotice(
                            text =
                                stringResource(
                                    R.string.github_release_load_failed_format,
                                    uiState.errorMessage,
                                ),
                        )
                    }

                uiState.entries.isEmpty() ->
                    item(key = "release-empty") {
                        GitHubReleaseNotice(text = stringResource(R.string.github_release_empty))
                    }

                else ->
                    itemsIndexedReleases(
                        entries = uiState.entries,
                        expanded = expanded,
                        onOpenLink = { url -> uriHandler.openUri(url) },
                    )
            }

            if (uiState.entries.isNotEmpty()) {
                item(key = "release-pager") {
                    GitHubReleasePager(
                        page = uiState.page,
                        hasPrevious = uiState.hasPreviousPage,
                        hasNext = uiState.hasNextPage,
                        loading = uiState.loading,
                        onPrevious = viewModel::openPreviousPage,
                        onNext = viewModel::openNextPage,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedReleases(
    entries: List<GitHubReleaseListEntry>,
    expanded: MutableMap<String, Unit>,
    onOpenLink: (String) -> Unit,
) {
    items(
        count = entries.size,
        key = { index -> entries[index].id },
        contentType = { "github_release" },
    ) { index ->
        val entry = entries[index]
        GitHubReleaseCard(
            entry = entry,
            expanded = expanded.containsKey(entry.id),
            onExpandedChange = { open ->
                if (open) expanded[entry.id] = Unit else expanded.remove(entry.id)
            },
            onOpenLink = onOpenLink,
            // The profile journey's handle on a release card; the first one is enough.
            cardTestTag = KeiOsTestTags.GitHubReleaseCardFirst.takeIf { index == 0 },
        )
    }
}

@Composable
private fun GitHubReleaseCard(
    entry: GitHubReleaseListEntry,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    cardTestTag: String?,
) {
    AppLiquidAccordionCard(
        backdrop = null,
        title = entry.displayName,
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = cardTestTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier,
        titleAccessory = {
            if (entry.latest) {
                GitHubReleasePill(
                    label = stringResource(R.string.github_release_badge_latest),
                    color = AppStatusColors.Fresh,
                )
            }
            if (entry.prerelease) {
                GitHubReleasePill(
                    label = stringResource(R.string.github_release_badge_prerelease),
                    color = GitHubReleasePreReleaseColor,
                )
            }
            entry.publishedAtMillis?.let { millis ->
                GitHubReleasePill(label = formatReleaseDate(millis), color = AppStatusColors.Cached)
            }
        },
    ) {
        GitHubReleaseFact(
            label = stringResource(R.string.github_release_tag_label),
            value = entry.tagName,
        )
        if (entry.authorName.isNotBlank()) {
            GitHubReleaseFact(
                label = stringResource(R.string.github_release_author_label),
                value = entry.authorName,
            )
        }
        GitHubReleaseFact(
            label = stringResource(R.string.github_release_assets_label),
            value = entry.assetCount.toString(),
        )
        if (entry.bodyMarkdown.isNotBlank()) {
            AppMarkdownContent(
                markdown = entry.bodyMarkdown,
                titleColor = MiuixTheme.colorScheme.onBackground,
                subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
                accentColor = MiuixTheme.colorScheme.primary,
                codeContainerColor = MiuixTheme.colorScheme.surfaceContainer,
                sourceKey = entry.id,
                onOpenLink = onOpenLink,
            )
        }
        AppLiquidTextButton(
            modifier = Modifier.fillMaxWidth(),
            backdrop = null,
            text = stringResource(R.string.github_release_open_in_browser),
            textColor = MiuixTheme.colorScheme.primary,
            containerColor = MiuixTheme.colorScheme.primary,
            variant = GlassVariant.SheetAction,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis,
            onClick = { onOpenLink(entry.htmlUrl) },
        )
    }
}

@Composable
private fun GitHubReleasePager(
    page: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    loading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLiquidTextButton(
            modifier = Modifier.weight(1f),
            backdrop = null,
            text = stringResource(R.string.github_release_previous_page),
            textColor = MiuixTheme.colorScheme.onBackgroundVariant,
            containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
            variant = GlassVariant.SheetAction,
            enabled = hasPrevious && !loading,
            textMaxLines = 1,
            onClick = onPrevious,
        )
        Text(
            text = stringResource(R.string.github_release_page_indicator_format, page),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        AppLiquidTextButton(
            modifier = Modifier.weight(1f).testTag(KeiOsTestTags.GitHubReleaseNextPageButton),
            backdrop = null,
            text = stringResource(R.string.github_release_next_page),
            textColor = MiuixTheme.colorScheme.primary,
            containerColor = MiuixTheme.colorScheme.primary,
            variant = GlassVariant.SheetAction,
            enabled = hasNext && !loading,
            textMaxLines = 1,
            onClick = onNext,
        )
    }
}

@Composable
private fun GitHubReleaseNotice(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}

@Composable
private fun GitHubReleaseFact(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MiuixTheme.colorScheme.onBackgroundVariant)
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Flat rather than glass, the way the BA slot cards' pills are.
 *
 * A pill sits on the card's own surface, where a blur has a uniform field to work on and so returns it —
 * measured on the BA page as the same pixels for an offscreen layer per pill.
 */
@Composable
private fun GitHubReleasePill(
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
        StatusPill(
            label = label,
            color = { color },
            size = AppStatusPillSize.Compact,
            backdrop = null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** GitHub marks a pre-release amber; the app already uses this exact amber for its warnings. */
private val GitHubReleasePreReleaseColor = androidx.compose.ui.graphics.Color(0xFFF59E0B)

private fun formatReleaseDate(millis: Long): String =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
