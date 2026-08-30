package os.kei.ui.page.main.github.release

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import os.kei.R
import android.os.Build
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.github.section.GitHubInlineLiquidSurface
import os.kei.ui.page.main.github.section.GitHubTrackedItemAssetRow
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.os.appLucideChevronDownIcon
import os.kei.ui.page.main.os.appLucideChevronLeftIcon
import os.kei.ui.page.main.os.appLucideChevronUpIcon
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideArchiveIcon
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import androidx.compose.ui.draw.clip
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSkipBackIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.markdown.AppMarkdownContent
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A repository's release history, laid out the way GitHub's own release page lays it out.
 *
 * The tracked card cannot carry this: it already shows local, stable and pre-release sections plus an
 * asset panel, and a release is itself a card with notes and files. The 发行日志 sheet that came before
 * had to degrade notes to preview lines to fit — the same space problem one level down.
 *
 * Collapsed, a card is a name, when it landed, and whether it is the latest or a pre-release, because
 * that is what picking a version off a list needs. Everything that identifies the build — tag, commit —
 * is a pill, since a release's name and its tag routinely mean different things: a CI pre-release keeps
 * a fixed name and moves its tag every push.
 *
 * The two nested cards are deliberately opposite by default. Notes are long enough to push the files off
 * the screen, and files are what most readers came down here for.
 */
@Composable
internal fun GitHubReleaseListPage(
    trackId: String,
    onBack: () -> Unit,
) {
    val viewModel: GitHubReleaseListViewModel =
        viewModel(
            key = "github-releases-$trackId",
            factory = viewModelFactory { initializer { GitHubReleaseListViewModel(trackId = trackId) } },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val uriHandler = LocalUriHandler.current

    val openReleases = remember { mutableStateMapOf<String, Unit>() }
    val openNotes = remember { mutableStateMapOf<String, Unit>() }
    // Assets default open, so this tracks what the reader has *closed* rather than what they opened.
    val closedAssets = remember { mutableStateMapOf<String, Unit>() }
    var seededPage by remember { mutableStateOf(-1) }
    var pageInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.page, uiState.rows.size, viewModel.defaultExpandedIds) {
        pageInput = uiState.tagQuery.ifBlank { uiState.page.toString() }
        if (seededPage == uiState.page || uiState.rows.isEmpty()) return@LaunchedEffect
        seededPage = uiState.page
        viewModel.defaultExpandedIds.forEach { id ->
            if (uiState.rows.any { row -> row.entry.id == id }) {
                openReleases[id] = Unit
                viewModel.ensureDetail(id)
            }
        }
    }

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
        actions = {
            // A page number *or* a tag, the way GitHub's "Find a release" box lets you go straight to
            // one. Paging ten at a time is fine for recent history and useless on a repository with
            // hundreds of releases.
            GitHubReleasePageJumpField(
                value = pageInput,
                onValueChange = { text -> pageInput = text.take(40) },
                onSubmit = {
                    val typed = pageInput.trim()
                    val page = typed.toIntOrNull()
                    when {
                        page != null -> viewModel.jumpToPage(page)
                        typed.isNotBlank() -> viewModel.findByTag(typed)
                        else -> viewModel.clearTagQuery()
                    }
                },
            )
        },
    ) { innerPadding ->
        val listTopPadding = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap
        val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val pagerBarBottomInset = if (navigationBarBottom != 0.dp) 8.dp + navigationBarBottom else 36.dp
        val edgeStackState = rememberAppEdgeStackState(stackLine = listTopPadding)
        Box(modifier = Modifier.fillMaxSize()) {
            AppEdgeStackKeepAlive(
                state = edgeStackState,
                // The top bar and the pager bar sample this. Without recording the list into it, both
                // render as flat white plates — glass with nothing behind it is just a fill.
                modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop),
            ) {
                CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
                    AppPageLazyColumn(
                        innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        topExtra = appEdgeStackKeepAliveTopPadding(listTopPadding),
                        bottomExtra = AppChromeTokens.floatingBottomBarOuterHeight + 24.dp,
                    ) {
                        releaseListBody(
                            uiState = uiState,
                            onToggleAllAssets = viewModel::toggleAllAssets,
                            compareUrlOf = viewModel::compareUrl,
                            lookupConfig = uiState.lookupConfig,
                            openReleases = openReleases,
                            openNotes = openNotes,
                            closedAssets = closedAssets,
                            onToggleRelease = { id, open ->
                                if (open) {
                                    openReleases[id] = Unit
                                    viewModel.ensureDetail(id)
                                } else {
                                    openReleases.remove(id)
                                }
                            },
                            onOpenLink = { url -> uriHandler.openUri(url) },
                            packageName = uiState.packageName,
                            onShare = { asset ->
                                val send =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, asset.downloadUrl)
                                    }
                                context.startActivity(
                                    Intent.createChooser(send, asset.name)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        )
                    }
                }
            }
            GitHubReleasePagerBar(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = pagerBarBottomInset),
                backdrop = pageBackdrop,
                canGoPrevious = uiState.hasPreviousPage,
                canGoNext = uiState.hasNextPage,
                loading = uiState.loading,
                // The API returns releases only, so there is nothing for this filter to do there.
                showTagFilter = uiState.atomMode,
                hideTagOnly = uiState.hideTagOnly,
                onFirst = viewModel::openFirstPage,
                onPrevious = viewModel::openPreviousPage,
                onNext = viewModel::openNextPage,
                onRefresh = viewModel::retry,
                onToggleTagFilter = viewModel::toggleHideTagOnly,
            )
        }
    }
}

private fun LazyListScope.releaseListBody(
    uiState: GitHubReleaseListUiState,
    openReleases: MutableMap<String, Unit>,
    openNotes: MutableMap<String, Unit>,
    closedAssets: MutableMap<String, Unit>,
    onToggleRelease: (String, Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (GitHubReleaseAssetFile) -> Unit,
    onToggleAllAssets: (String) -> Unit,
    compareUrlOf: (GitHubReleaseRow) -> String?,
    lookupConfig: GitHubLookupConfig,
    packageName: String,
) {
    when {
        uiState.unsupported -> item(key = "release-unsupported") {
            GitHubReleaseNotice(textRes = R.string.github_release_unsupported)
        }

        uiState.loading && uiState.rows.isEmpty() -> item(key = "release-loading") {
            GitHubReleaseNotice(textRes = R.string.github_release_loading)
        }

        uiState.errorMessage.isNotBlank() && uiState.rows.isEmpty() -> item(key = "release-error") {
            GitHubReleaseNotice(
                text = stringResource(R.string.github_release_load_failed_format, uiState.errorMessage),
            )
        }

        // Past the end is not the same as empty. Paging or jumping beyond the last release used to
        // report the repository as having none, which for a repository with twenty of them reads as a
        // failure rather than as the end of the list.
        uiState.rows.isEmpty() && uiState.page > 1 -> item(key = "release-past-end") {
            GitHubReleaseNotice(
                text = stringResource(R.string.github_release_page_past_end_format, uiState.page),
            )
        }

        uiState.rows.isEmpty() -> item(key = "release-empty") {
            GitHubReleaseNotice(textRes = R.string.github_release_empty)
        }

        uiState.tagFilterUnavailable -> {
            item(key = "release-filter-unavailable") {
                GitHubReleaseNotice(textRes = R.string.github_release_tag_filter_unavailable)
            }
            releaseCards(
                uiState = uiState,
                openReleases = openReleases,
                openNotes = openNotes,
                closedAssets = closedAssets,
                onToggleRelease = onToggleRelease,
                onOpenLink = onOpenLink,
                onShare = onShare,
                onToggleAllAssets = onToggleAllAssets,
                compareUrlOf = compareUrlOf,
                lookupConfig = lookupConfig,
                packageName = packageName,
            )
        }

        else -> releaseCards(
            uiState = uiState,
            openReleases = openReleases,
            openNotes = openNotes,
            closedAssets = closedAssets,
            onToggleRelease = onToggleRelease,
            onOpenLink = onOpenLink,
            onShare = onShare,
            onToggleAllAssets = onToggleAllAssets,
            compareUrlOf = compareUrlOf,
            lookupConfig = lookupConfig,
            packageName = packageName,
        )
    }
}

private fun LazyListScope.releaseCards(
    uiState: GitHubReleaseListUiState,
    openReleases: MutableMap<String, Unit>,
    openNotes: MutableMap<String, Unit>,
    closedAssets: MutableMap<String, Unit>,
    onToggleRelease: (String, Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (GitHubReleaseAssetFile) -> Unit,
    onToggleAllAssets: (String) -> Unit,
    compareUrlOf: (GitHubReleaseRow) -> String?,
    lookupConfig: GitHubLookupConfig,
    packageName: String,
) {
    items(
        count = uiState.rows.size,
        key = { index -> uiState.rows[index].entry.id },
        contentType = { "github_release" },
    ) { index ->
        val row = uiState.rows[index]
        GitHubReleaseCard(
            row = row,
            expanded = openReleases.containsKey(row.entry.id),
            onExpandedChange = { open -> onToggleRelease(row.entry.id, open) },
            notesExpanded = openNotes.containsKey(row.entry.id),
            onNotesExpandedChange = { open ->
                if (open) openNotes[row.entry.id] = Unit else openNotes.remove(row.entry.id)
            },
            assetsExpanded = !closedAssets.containsKey(row.entry.id),
            onAssetsExpandedChange = { open ->
                if (open) closedAssets.remove(row.entry.id) else closedAssets[row.entry.id] = Unit
            },
            onOpenLink = onOpenLink,
            onShare = onShare,
            onToggleAllAssets = onToggleAllAssets,
            compareUrl = compareUrlOf(row),
            lookupConfig = lookupConfig,
            packageName = packageName,
            cardTestTag = KeiOsTestTags.GitHubReleaseCardFirst.takeIf { index == 0 },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubReleaseCard(
    row: GitHubReleaseRow,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    notesExpanded: Boolean,
    onNotesExpandedChange: (Boolean) -> Unit,
    assetsExpanded: Boolean,
    onAssetsExpandedChange: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (GitHubReleaseAssetFile) -> Unit,
    onToggleAllAssets: (String) -> Unit,
    compareUrl: String?,
    lookupConfig: GitHubLookupConfig,
    packageName: String,
    cardTestTag: String?,
) {
    val entry = row.entry
    val context = LocalContext.current
    val supportedAbis = remember { Build.SUPPORTED_ABIS.orEmpty().toList() }
    val nowMillis = remember(row.detail) { System.currentTimeMillis() }
    val notes = row.detail?.releaseNotesBody?.takeIf(String::isNotBlank) ?: entry.bodyMarkdown
    AppLiquidAccordionCard(
        backdrop = null,
        title = entry.displayName,
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = cardTestTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier,
        // Ten cards a page, two of which open themselves — the pile has to keep working on the open ones
        // or it stops working at all here.
        edgeStackWhileExpanded = true,
        // Compare and open-in-browser belong on the header rather than as buttons stranded at the foot
        // of a long card: they act on the release itself, not on whatever the reader scrolled to.
        headerActions = if (!expanded) {
            null
        } else {
            {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCompactIconAction(
                    icon = appLucideBranchIcon(),
                    contentDescription = stringResource(R.string.github_release_compare),
                    tint = MiuixTheme.colorScheme.primary,
                    enabled = compareUrl != null,
                    onClick = { compareUrl?.let(onOpenLink) },
                )
                AppCompactIconAction(
                    icon = appLucideExternalLinkIcon(),
                    contentDescription = stringResource(R.string.github_release_open_in_browser),
                    tint = MiuixTheme.colorScheme.primary,
                    enabled = true,
                    onClick = { onOpenLink(entry.htmlUrl) },
                )
            }
            }
        },
        titleAccessory = {
            if (row.installed) {
                GitHubReleasePill(
                    label = stringResource(R.string.github_release_badge_installed),
                    color = GitHubStatusPalette.Active,
                )
            }
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
            // Collapsed, the date is the second thing a reader needs. Open, the header has actions to
            // carry and the summary strip below states the date, so it stops competing for the title.
            if (!expanded) {
                entry.publishedAtMillis?.let { millis ->
                    GitHubReleasePill(
                        label = releasedLabel(millis),
                        color = AppStatusColors.Cached,
                    )
                }
            }
        },
    ) {
        // The same tinted summary strip the tracked card's asset panel uses, carrying what identifies
        // the build. A release's name and its tag routinely mean different things — a CI pre-release keeps
        // one name and moves its tag every push — and the commit is what makes it comparable to latest.
        val accent = if (entry.prerelease) GitHubReleasePreReleaseColor else GitHubStatusPalette.Update
        val isDark = isAppInDarkTheme()
        GitHubInlineLiquidSurface(
            backdrop = null,
            tint = Color.Unspecified,
            surfaceColor =
                GitHubStatusPalette
                    .tonedSurface(accent, isDark = isDark)
                    .copy(alpha = if (isDark) 0.30f else 0.18f),
            onClick = { onOpenLink(entry.htmlUrl) },
        ) {
            // Wraps rather than truncating. Four pills — tag, date, commit, publisher — do not fit one
            // line on a phone, and a clipped publisher or a half-shown commit is worse than a second
            // row: these exist precisely to be read exactly.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GitHubReleasePill(label = entry.tagName, color = GitHubStatusPalette.Stable)
                entry.publishedAtMillis?.let { millis ->
                    GitHubReleasePill(label = releasedLabel(millis), color = AppStatusColors.Cached)
                }
                row.detail?.shortCommitSha?.takeIf(String::isNotBlank)?.let { sha ->
                    GitHubReleasePill(label = sha, color = GitHubStatusPalette.Active)
                }
                if (entry.authorName.isNotBlank()) {
                    GitHubReleasePill(label = entry.authorName, color = accent)
                }
            }
        }

        GitHubReleaseNestedCard(
            title = stringResource(R.string.github_release_notes_section),
            expanded = notesExpanded,
            onExpandedChange = onNotesExpandedChange,
        ) {
            if (notes.isBlank()) {
                Text(
                    text = stringResource(R.string.github_release_notes_empty),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            } else {
                AppMarkdownContent(
                    markdown = notes,
                    titleColor = MiuixTheme.colorScheme.onBackground,
                    subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    accentColor = MiuixTheme.colorScheme.primary,
                    codeContainerColor = MiuixTheme.colorScheme.surfaceContainer,
                    sourceKey = entry.id,
                    onOpenLink = onOpenLink,
                )
            }
        }

        GitHubReleaseNestedCard(
            title = stringResource(R.string.github_release_assets_section),
            trailing = row.detail?.assets?.size?.toString()
                ?: entry.assetCount.takeIf { count -> count > 0 }?.toString().orEmpty(),
            expanded = assetsExpanded,
            onExpandedChange = onAssetsExpandedChange,
            headerAction = {
                AppCompactIconAction(
                    icon = appLucideArchiveIcon(),
                    contentDescription =
                        stringResource(
                            if (row.showAllAssets) {
                                R.string.github_release_assets_show_relevant
                            } else {
                                R.string.github_release_assets_show_all
                            },
                        ),
                    tint =
                        if (row.showAllAssets) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        },
                    enabled = true,
                    onClick = { onToggleAllAssets(entry.id) },
                )
            },
        ) {
            when {
                row.detailLoading ->
                    Text(
                        text = stringResource(R.string.github_release_assets_loading),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )

                row.detailError.isNotBlank() ->
                    Text(text = row.detailError, color = AppStatusColors.Failed)

                row.detail?.assets.isNullOrEmpty() ->
                    Text(
                        text = stringResource(R.string.github_release_assets_empty),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )

                else -> row.detail?.assets?.forEach { asset ->
                    // The tracked card's own asset row, so a file reads the same on both surfaces: ABI
                    // and extension pills, size, age, the trust check, and icon actions rather than
                    // stacked text buttons.
                    GitHubTrackedItemAssetRow(
                        asset = asset,
                        expectedPackageName = packageName,
                        alwaysLatestReleaseDownload = false,
                        targetAccent = accent,
                        summaryContainerColor =
                            GitHubStatusPalette
                                .tonedSurface(accent, isDark = isDark)
                                .copy(alpha = if (isDark) 0.30f else 0.18f),
                        summaryBorderColor = accent.copy(alpha = if (isDark) 0.30f else 0.20f),
                        supportedAbis = supportedAbis,
                        relativeTimeNowMillis = nowMillis,
                        // Verifying an *old* APK's signer matters at least as much as verifying the
                        // newest one, so this follows the same settings the tracked card follows.
                        showApkTrustCheck = lookupConfig.decisionAssistEnabled &&
                            lookupConfig.apkTrustCheckEnabled,
                        managedInstallEnabled = lookupConfig.appManagedShareInstallEnabled,
                        manifestInfo = null,
                        managedInstallRunning = false,
                        installActionColor = MiuixTheme.colorScheme.primary,
                        context = context,
                        onOpenApkInfo = { onOpenLink(entry.htmlUrl) },
                        onInstallApk = { onOpenLink(asset.downloadUrl) },
                        onOpenApkInDownloader = { onOpenLink(asset.downloadUrl) },
                        onShareApkLink = onShare,
                    )
                }
            }
        }

    }
}

/**
 * A real card inside the release card, not a tinted strip.
 *
 * Notes and files are each a section a reader opens on their own terms, so they get the same accordion
 * the page's cards use rather than a bespoke header row.
 */
@Composable
private fun GitHubReleaseNestedCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    trailing: String = "",
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppLiquidAccordionCard(
        backdrop = null,
        title = title,
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        // A step away from the parent, not a heavier coat of the same paint. `surfaceContainer` is
        // near-white in light, so stacking it on a white card renders nothing — the section read as a
        // heading with a chevron. Veiling with the foreground colour guarantees a visible edge in both
        // themes, which is what makes it a card.
        containerColor =
            MiuixTheme.colorScheme.onBackground.copy(
                alpha = if (isAppInDarkTheme()) 0.10f else 0.05f,
            ),
        titleAccessory =
            if (trailing.isNotBlank()) {
                { GitHubReleasePill(label = trailing, color = MiuixTheme.colorScheme.onBackgroundVariant) }
            } else {
                null
            },
        headerActions = headerAction,
        content = content,
    )
}

/**
 * One floating Liquid surface holding the page controls, rather than three separate circles.
 *
 * Three docks in a row are three buttons; a bar is a single surface the controls sit inside, which is
 * what every other page's bottom chrome is.
 */
@Composable
private fun GitHubReleasePagerBar(
    modifier: Modifier,
    backdrop: com.kyant.backdrop.Backdrop?,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    loading: Boolean,
    showTagFilter: Boolean,
    hideTagOnly: Boolean,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTagFilter: () -> Unit,
) {
    AppLiquidFloatingSurface(
        // A fixed capsule rather than a stretched plate: four actions is a known width, and the bar
        // reads as chrome floating over the list the way the pager's does, not as a footer bolted to
        // the bottom edge.
        modifier =
            modifier
                .width(if (showTagFilter) GitHubReleasePagerBarWideWidth else GitHubReleasePagerBarWidth)
                .height(AppChromeTokens.floatingBottomBarOuterHeight),
        backdrop = backdrop,
    ) {
        Row(
            modifier = Modifier.wrapContentWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GitHubReleaseBarAction(
                icon = appLucideSkipBackIcon(),
                enabled = canGoPrevious && !loading,
                contentDescription = stringResource(R.string.github_release_first_page),
                onClick = onFirst,
            )
            GitHubReleaseBarAction(
                icon = appLucideChevronLeftIcon(),
                enabled = canGoPrevious && !loading,
                contentDescription = stringResource(R.string.github_release_previous_page),
                onClick = onPrevious,
            )
            if (showTagFilter) {
                GitHubReleaseBarAction(
                    icon = appLucideFilterIcon(),
                    enabled = !loading,
                    highlighted = hideTagOnly,
                    contentDescription = stringResource(R.string.github_release_hide_tag_only),
                    onClick = onToggleTagFilter,
                    modifier = Modifier.testTag(KeiOsTestTags.GitHubReleaseTagFilterButton),
                )
            }
            GitHubReleaseBarAction(
                icon = appLucideRefreshIcon(),
                enabled = !loading,
                contentDescription = stringResource(R.string.common_refresh),
                onClick = onRefresh,
            )
            GitHubReleaseBarAction(
                icon = appLucideChevronRightIcon(),
                enabled = canGoNext && !loading,
                contentDescription = stringResource(R.string.github_release_next_page),
                onClick = onNext,
                modifier = Modifier.testTag(KeiOsTestTags.GitHubReleaseNextPageButton),
            )
        }
    }
}

@Composable
private fun GitHubReleaseBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GitHubReleaseDockIcon(icon, enabled, contentDescription, highlighted)
    }
}

@Composable
private fun GitHubReleaseDockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    contentDescription: String? = null,
    highlighted: Boolean = false,
) {
    Icon(
        modifier = Modifier.size(22.dp),
        imageVector = icon,
        contentDescription = contentDescription,
        tint =
            when {
                !enabled -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f)
                // A filter that is *on* has to look on, since what it does is remove rows.
                highlighted -> MiuixTheme.colorScheme.primary
                else -> MiuixTheme.colorScheme.onBackgroundVariant
            },
    )
}

/** The page number, editable, so a repository with a long history can be jumped through. */
@Composable
private fun GitHubReleasePageJumpField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                // Stays a page-number's width even when a tag is typed into it: the title card sits
                // right beside it, and growing the field clipped the repository's name.
                .width(72.dp)
                // The same height `AppLiquidNavigationButton` uses, so the field centres against the
                // title card instead of riding above it — the top-end overlay pins by its top edge.
                .height(52.dp)
                .appSquircleBackground(
                    MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                    14.dp,
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().testTag(KeiOsTestTags.GitHubReleasePageJumpField),
        )
    }
}

@Composable
private fun GitHubReleaseNotice(
    text: String? = null,
    textRes: Int? = null,
) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
        text = text ?: textRes?.let { res -> stringResource(res) }.orEmpty(),
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
 * A pill sits on the card's own uniform fill, where a blur returns that fill — the same pixels for the
 * price of an offscreen layer.
 */
@Composable
private fun GitHubReleasePill(
    label: String,
    color: Color,
) {
    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
        StatusPill(
            label = label,
            color = { color },
            size = AppStatusPillSize.Compact,
            backdrop = null,
            maxLines = 1,
        )
    }
}

/** Four 48dp actions, their spacing, and the capsule's own padding. */
private val GitHubReleasePagerBarWidth = 224.dp

/** The same, plus the tag filter the Atom feed needs. */
private val GitHubReleasePagerBarWideWidth = 276.dp

/** GitHub marks a pre-release amber; the app already uses this exact amber for its warnings. */
private val GitHubReleasePreReleaseColor = Color(0xFFF59E0B)

/**
 * "3 days ago" while that still means something, then a short date.
 *
 * A release from last year does not become more legible as "412 days ago", and the collapsed card has
 * room for one short label either way.
 */
@Composable
private fun releasedLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val elapsed = (now - millis).coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    return when {
        minutes < 60L -> stringResource(R.string.github_release_time_minutes_format, minutes.coerceAtLeast(1L))
        hours < 24L -> stringResource(R.string.github_release_time_hours_format, hours)
        days <= 30L -> stringResource(R.string.github_release_time_days_format, days)
        else -> shortReleaseDate(millis)
    }
}

private fun shortReleaseDate(millis: Long): String =
    runCatching {
        SimpleDateFormat("yy-MM-dd", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
