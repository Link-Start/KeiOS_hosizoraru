@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.fdroid

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.os.Build
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.fdroid.FdroidAntiFeatureSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.fdroidVersionAssetFile
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.section.GitHubInlineLiquidSurface
import os.kei.ui.page.main.github.section.GitHubTrackedItemAssetRow
import os.kei.ui.page.main.github.sheet.copyTextToClipboard
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.markdown.AppMarkdownContent
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * An F-Droid package's build history, laid out the way the release list lays out a repository's.
 *
 * The same argument applies one source over: the tracked card already carries local, stable and
 * pre-release sections plus an asset panel, and the F-Droid detail sheet describes the *one* build the
 * track selected — neither has room for a history, and going back through versions is exactly what an
 * F-Droid reader does. Installing an older build is normal here in a way it is not on GitHub, because
 * F-Droid keeps every build it ever published and the newest one is routinely not the one you want.
 *
 * Two things differ from the release page, both because the source does.
 *
 * There is no paging. F-Droid answers once with the whole history, so the bottom bar carries a refresh
 * and a compatibility filter rather than page controls, and the top-bar field narrows the list locally
 * instead of fetching a page.
 *
 * And a row's own detail may be missing rather than unloaded. The package API lists builds without
 * saying where their APKs are, so an old build can legitimately have nothing to offer but its name and
 * code; the card says so instead of showing a download that would not work. What fills those gaps is a
 * refresh, not opening the card, so there is no per-row fetch here at all.
 */
@Composable
internal fun FdroidVersionListPage(
    trackId: String,
    onBack: () -> Unit,
) {
    val viewModel: FdroidVersionListViewModel =
        viewModel(
            key = "fdroid-versions-$trackId",
            factory = viewModelFactory { initializer { FdroidVersionListViewModel(trackId = trackId) } },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val secondaryListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val uriHandler = LocalUriHandler.current

    val openVersions = remember { mutableStateMapOf<String, Unit>() }
    val openNotes = remember { mutableStateMapOf<String, Unit>() }
    // The APK and anti-feature sections default open, so these track what the reader has *closed*
    // rather than what they opened.
    val closedApk = remember { mutableStateMapOf<String, Unit>() }
    val closedAntiFeatures = remember { mutableStateMapOf<String, Unit>() }
    // A build that is closed again keeps its place in the reading lane, so this remembers what the reader
    // has been through rather than what is open right now. Both go to the lane rule.
    val keptInReadingLane = remember { mutableStateMapOf<String, Unit>() }
    var seededAnchors by remember { mutableStateOf("") }
    var lastNarrowing by remember { mutableStateOf("") }

    val readingIds = viewModel.defaultExpandedIds + openVersions.keys + keptInReadingLane.keys
    // One column while there is nothing to lane: a notice about the whole history does not belong in a
    // half-width lane with an empty one beside it.
    //
    // Keyed on whether the history has anything in it, NOT on what the filters left visible. `columnCount`
    // feeds `contentMaxWidth`, which the top row centres against — so keying it on the filtered rows would
    // snap the title and the field inward the moment a query matched nothing and back out on the next
    // keystroke. The release list does key on its rows, but its list only empties on a page change.
    val columnCount = if (uiState.totalCount == 0) 1 else appPageColumnCount()

    LaunchedEffect(viewModel.defaultExpandedIds, uiState.query, uiState.compatibleOnly) {
        val narrowing = "${uiState.query}|${uiState.compatibleOnly}"
        if (lastNarrowing != narrowing) {
            lastNarrowing = narrowing
            // A narrowed history is a new list to read: what was held is not necessarily on screen any
            // more. The hold is dropped, but nothing is *opened* — re-seeding here would re-open the
            // recommended build on every keystroke, including one the reader had just collapsed.
            keptInReadingLane.clear()
        }
        val anchors = viewModel.defaultExpandedIds.sorted().joinToString(",")
        if (seededAnchors == anchors || uiState.rows.isEmpty()) return@LaunchedEffect
        seededAnchors = anchors
        viewModel.defaultExpandedIds.forEach { id -> openVersions[id] = Unit }
    }

    AppPageScaffold(
        title = uiState.appLabel.ifBlank { stringResource(R.string.github_fdroid_version_page_title) },
        modifier =
            Modifier
                .fillMaxSize()
                .pageRootTestTag(KeiOsTestTags.FdroidVersionPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        // The field is wider than the release list's page-number box, so the title has to actually reserve
        // room for it rather than being drawn under it. Same as the OS, MCP and calendar pages.
        reserveTopEndActionSpace = true,
        contentMaxWidth = appPageContentMaxWidthFor(columnCount),
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = pageBackdrop,
            )
        },
        actions = {
            // Local rather than a lookup: the whole history is already here, so narrowing it is instant
            // and needs no submit. Nothing to page to means nothing to fetch.
            FdroidVersionFilterField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        val listTopPadding = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap
        val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barBottomInset = if (navigationBarBottom != 0.dp) 8.dp + navigationBarBottom else 36.dp
        val edgeStackState = rememberAppEdgeStackState(stackLine = listTopPadding)
        Box(modifier = Modifier.fillMaxSize()) {
            AppEdgeStackKeepAlive(
                state = edgeStackState,
                // The top bar and the bottom bar sample this. Without recording the list into it, both
                // render as flat white plates — glass with nothing behind it is just a fill.
                modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop),
            ) {
                CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
                    val lanes =
                        remember(uiState.rows, readingIds, columnCount) {
                            if (columnCount >= 2) {
                                fdroidVersionLanesFor(rows = uiState.rows, readingIds = readingIds)
                            } else {
                                FdroidVersionLanes(
                                    first = uiState.rows.withIndex().toList(),
                                    second = emptyList(),
                                )
                            }
                        }
                    val clipboardLabel = stringResource(R.string.github_fdroid_detail_label_apk)
                    val copiedToast = stringResource(R.string.github_fdroid_detail_toast_link_copied)
                    // Remembered rather than rebuilt: a fresh instance every recomposition is never equal
                    // to the last one, which would recompose every visible card whenever anything on the
                    // page changed. Keyed on the state it closes over -- the three expansion maps and the
                    // link handlers are already stable references.
                    val cardActions =
                        remember(
                            uiState.packagePageUrl,
                            uiState.packageName,
                            uiState.repositoryUrl,
                            uiState.anyFilePublished,
                            uiState.lookupConfig,
                            clipboardLabel,
                            copiedToast,
                        ) {
                            FdroidVersionCardActions(
                                openVersions = openVersions,
                                openNotes = openNotes,
                                closedApk = closedApk,
                                closedAntiFeatures = closedAntiFeatures,
                                onToggleVersion = { id, open ->
                                    if (open) {
                                        openVersions[id] = Unit
                                        // Recorded on opening rather than on closing, so the build keeps
                                        // its lane through the close that follows.
                                        keptInReadingLane[id] = Unit
                                    } else {
                                        openVersions.remove(id)
                                    }
                                },
                                onOpenLink = { url -> uriHandler.openUri(url) },
                                onCopyDownloadUrl = { url ->
                                    copyTextToClipboard(context, clipboardLabel, url)
                                    context.showToast(copiedToast)
                                },
                                onShareAsset = { asset ->
                                    // The asset row's share action, which on the release list opens a
                                    // chooser. It has a share icon and says "share", so it has to share.
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
                                packagePageUrl = uiState.packagePageUrl,
                                packageName = uiState.packageName,
                                repoUrl = uiState.repositoryUrl,
                                anyFilePublished = uiState.anyFilePublished,
                                lookupConfig = uiState.lookupConfig,
                            )
                        }
                    // The browsing lane carries the history's own notices -- loading, empty, cache-only,
                    // the failed refresh -- because those describe the list rather than one of its halves.
                    val browsingLane: LazyListScope.() -> Unit = {
                        fdroidVersionListBody(
                            uiState = uiState,
                            rows = lanes.first,
                            actions = cardActions,
                        )
                    }
                    val readingLane: LazyListScope.() -> Unit = {
                        fdroidVersionCards(rows = lanes.second, actions = cardActions)
                    }
                    val listInnerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                    val listTopExtra = appEdgeStackKeepAliveTopPadding(listTopPadding)
                    val listBottomExtra = AppChromeTokens.floatingBottomBarOuterHeight + 24.dp
                    if (columnCount >= 2) {
                        AppPageTwoColumnLists(
                            innerPadding = listInnerPadding,
                            primaryState = listState,
                            secondaryState = secondaryListState,
                            modifier = Modifier.fillMaxSize(),
                            topExtra = listTopExtra,
                            bottomExtra = listBottomExtra,
                            primary = browsingLane,
                            secondary = readingLane,
                        )
                    } else {
                        AppPageLazyColumn(
                            innerPadding = listInnerPadding,
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            topExtra = listTopExtra,
                            bottomExtra = listBottomExtra,
                            content = browsingLane,
                        )
                    }
                }
            }
            FdroidVersionBar(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = barBottomInset),
                backdrop = pageBackdrop,
                busy = uiState.loading || uiState.refreshing || uiState.loadingFileDetails,
                compatibleOnly = uiState.compatibleOnly,
                onRefresh = viewModel::refresh,
                onToggleCompatibleOnly = viewModel::toggleCompatibleOnly,
            )
        }
    }
}

/**
 * Everything a card needs that is the same for every card, bundled so the lane lambdas stay readable.
 *
 * The release list passes eleven parameters through two lane functions to reach its cards. That page has
 * one lane function calling the other; this one has two calling a shared card list, and threading the
 * same eleven twice is where a lane quietly ends up with the other lane's rows.
 *
 * [Stable] holds because the page remembers one instance per distinct configuration and the three maps
 * are snapshot state, so a card that reads one is invalidated on its own rather than with the page.
 */
@Stable
private class FdroidVersionCardActions(
    val openVersions: MutableMap<String, Unit>,
    val openNotes: MutableMap<String, Unit>,
    val closedApk: MutableMap<String, Unit>,
    val closedAntiFeatures: MutableMap<String, Unit>,
    val onToggleVersion: (String, Boolean) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onCopyDownloadUrl: (String) -> Unit,
    val onShareAsset: (GitHubReleaseAssetFile) -> Unit,
    val packagePageUrl: String,
    val packageName: String,
    val repoUrl: String,
    val anyFilePublished: Boolean,
    val lookupConfig: GitHubLookupConfig,
)

private fun LazyListScope.fdroidVersionListBody(
    uiState: FdroidVersionListUiState,
    rows: List<IndexedValue<FdroidVersionRow>>,
    actions: FdroidVersionCardActions,
) {
    when {
        uiState.unsupported -> item(key = "fdroid-version-unsupported") {
            FdroidVersionNotice(textRes = R.string.github_fdroid_version_unsupported)
        }

        uiState.loading && uiState.rows.isEmpty() -> item(key = "fdroid-version-loading") {
            FdroidVersionNotice(textRes = R.string.github_fdroid_version_loading)
        }

        uiState.errorMessage.isNotBlank() && uiState.rows.isEmpty() -> {
            item(key = "fdroid-version-error") {
                FdroidVersionNotice(
                    text =
                        stringResource(
                            R.string.github_fdroid_version_load_failed_format,
                            uiState.errorMessage,
                        ),
                )
            }
        }

        // Filtered to nothing is not the same as empty. A reader who typed a version that is not here, or
        // hid everything their device cannot run, has to be told which of the two happened.
        uiState.rows.isEmpty() && uiState.totalCount > 0 -> item(key = "fdroid-version-no-match") {
            FdroidVersionNotice(
                text =
                    if (uiState.query.isNotBlank()) {
                        stringResource(R.string.github_fdroid_version_no_match_format, uiState.query)
                    } else {
                        stringResource(R.string.github_fdroid_version_none_compatible)
                    },
            )
        }

        uiState.rows.isEmpty() -> item(key = "fdroid-version-empty") {
            FdroidVersionNotice(textRes = R.string.github_fdroid_version_empty)
        }

        else -> {
            // A refresh that failed over a list that is already on screen. Said above the history rather
            // than instead of it: the cached builds are still true, they are just not all of them.
            if (uiState.errorMessage.isNotBlank()) {
                item(key = "fdroid-version-stale") {
                    FdroidVersionNotice(
                        text =
                            stringResource(
                                R.string.github_fdroid_version_refresh_failed_format,
                                uiState.errorMessage,
                            ),
                    )
                }
            } else if (uiState.loadingFileDetails) {
                // The index is on its way. Said rather than left blank, because it is megabytes and the
                // history is already readable without it -- and because the alternative wording, "this
                // repository publishes no files", would be wrong for the minute it takes to arrive.
                item(key = "fdroid-version-loading-details") {
                    FdroidVersionNotice(textRes = R.string.github_fdroid_version_loading_file_details)
                }
            } else if (!uiState.anyFilePublished) {
                // Said once, about the repository, rather than repeated inside every card. Three builds
                // each carrying the same paragraph is what made this page read as an apology.
                item(key = "fdroid-version-no-files") {
                    FdroidVersionNotice(textRes = R.string.github_fdroid_version_files_not_published)
                }
            } else if (!uiState.liveHistoryLoaded) {
                // "This package has eight versions" and "we only kept eight" look identical otherwise.
                item(key = "fdroid-version-cache-only") {
                    FdroidVersionNotice(
                        text =
                            stringResource(
                                R.string.github_fdroid_version_cache_only_format,
                                uiState.totalCount,
                            ),
                    )
                }
            }
            fdroidVersionCards(rows = rows, actions = actions)
        }
    }
}

private fun LazyListScope.fdroidVersionCards(
    rows: List<IndexedValue<FdroidVersionRow>>,
    actions: FdroidVersionCardActions,
) {
    items(
        count = rows.size,
        key = { index -> rows[index].value.id },
        contentType = { "fdroid_version" },
    ) { index ->
        val indexed = rows[index]
        val row = indexed.value
        FdroidVersionCard(
            row = row,
            expanded = actions.openVersions.containsKey(row.id),
            onExpandedChange = { open -> actions.onToggleVersion(row.id, open) },
            notesExpanded = actions.openNotes.containsKey(row.id),
            onNotesExpandedChange = { open ->
                if (open) actions.openNotes[row.id] = Unit else actions.openNotes.remove(row.id)
            },
            apkExpanded = !actions.closedApk.containsKey(row.id),
            onApkExpandedChange = { open ->
                if (open) actions.closedApk.remove(row.id) else actions.closedApk[row.id] = Unit
            },
            // Open by default, like the APK section: a warning behind a fold is a warning nobody reads.
            antiFeaturesExpanded = !actions.closedAntiFeatures.containsKey(row.id),
            onAntiFeaturesExpandedChange = { open ->
                if (open) {
                    actions.closedAntiFeatures.remove(row.id)
                } else {
                    actions.closedAntiFeatures[row.id] = Unit
                }
            },
            actions = actions,
            // The first build *in this lane*, which a lane on its own no longer knows.
            cardTestTag = KeiOsTestTags.FdroidVersionCardFirst.takeIf { indexed.index == 0 },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FdroidVersionCard(
    row: FdroidVersionRow,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    notesExpanded: Boolean,
    onNotesExpandedChange: (Boolean) -> Unit,
    apkExpanded: Boolean,
    onApkExpandedChange: (Boolean) -> Unit,
    antiFeaturesExpanded: Boolean,
    onAntiFeaturesExpandedChange: (Boolean) -> Unit,
    actions: FdroidVersionCardActions,
    cardTestTag: String?,
) {
    val version = row.version
    val context = LocalContext.current
    val supportedAbis = remember { Build.SUPPORTED_ABIS.orEmpty().toList() }
    val nowMillis = remember(row.id) { System.currentTimeMillis() }
    val accent = if (row.channel.isPreRelease) FdroidPreReleaseColor else GitHubStatusPalette.Update
    val isDark = isAppInDarkTheme()
    AppLiquidAccordionCard(
        backdrop = null,
        title = row.displayName,
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = cardTestTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier,
        // Two builds open themselves on arrival, so the pile has to keep working on the open ones or it
        // stops working for the history behind them.
        edgeStackWhileExpanded = true,
        headerActions = if (!expanded) {
            null
        } else {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Absent rather than dimmed when there is nothing to copy. `AppCompactIconAction`
                    // keeps the tint it is given whatever `enabled` says, so a disabled copy button reads
                    // as a live one that does nothing -- and a repository whose index publishes no file
                    // has that on every row.
                    if (row.downloadUrl.isNotBlank()) {
                        AppCompactIconAction(
                            icon = osLucideCopyIcon(),
                            contentDescription =
                                stringResource(R.string.github_fdroid_version_copy_download),
                            tint = MiuixTheme.colorScheme.primary,
                            enabled = true,
                            onClick = { actions.onCopyDownloadUrl(row.downloadUrl) },
                        )
                    }
                    if (actions.packagePageUrl.isNotBlank()) {
                        AppCompactIconAction(
                            icon = appLucideExternalLinkIcon(),
                            contentDescription = stringResource(R.string.github_release_open_in_browser),
                            tint = MiuixTheme.colorScheme.primary,
                            enabled = true,
                            onClick = { actions.onOpenLink(actions.packagePageUrl) },
                        )
                    }
                }
            }
        },
        // Exactly one badge inline, and the date only when there is no badge to show.
        //
        // The title shares this row with the accessory, the header actions and the chevron, and it is the
        // *anchored* card -- installed and recommended at once -- that carries the most badges. Two
        // English badges beside two action icons squeezed "2.1.3.0" onto two lines. So the row states the
        // single most decision-relevant fact and the summary strip below carries the full set.
        titleAccessory = {
            val badge = row.primaryBadge()
            if (badge != null) {
                FdroidVersionPill(label = stringResource(badge.labelRes), color = badge.color())
            } else {
                formatReleaseUpdatedAtCompact(version.addedAtMillis)?.let { label ->
                    FdroidVersionPill(label = label, color = AppStatusColors.Cached)
                }
            }
        },
    ) {
        // The same tinted summary strip the tracked card's asset panel uses, carrying what identifies the
        // build. The version code is the pill that matters most here: F-Droid orders and compares by it,
        // and a repository's version *names* are routinely not comparable at all.
        GitHubInlineLiquidSurface(
            backdrop = null,
            tint = Color.Unspecified,
            surfaceColor =
                GitHubStatusPalette
                    .tonedSurface(accent, isDark = isDark)
                    .copy(alpha = if (isDark) 0.30f else 0.18f),
            onClick = { actions.packagePageUrl.takeIf(String::isNotBlank)?.let(actions.onOpenLink) },
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FdroidVersionPill(
                    label = version.versionCode.toString(),
                    color = GitHubStatusPalette.Stable,
                )
                // The full badge set, since the title row shows at most one of them. Also what keeps this
                // strip from rendering as a tall band around a single pill on a repository whose index
                // publishes nothing but version names and codes.
                row.badges().forEach { badge ->
                    FdroidVersionPill(label = stringResource(badge.labelRes), color = badge.color())
                }
                formatReleaseUpdatedAtCompact(version.addedAtMillis)?.let { label ->
                    FdroidVersionPill(label = label, color = AppStatusColors.Cached)
                }
                if (version.apkSizeBytes > 0L) {
                    FdroidVersionPill(
                        label = formatAssetSize(version.apkSizeBytes, context),
                        color = GitHubStatusPalette.Active,
                    )
                }
                version.sdkLabel()?.let { label ->
                    FdroidVersionPill(label = label, color = accent)
                }

                // Every ABI, never a subset. Which architectures a build carries decides whether it
                // runs at all and how big it is, so a truncated list is worse than a taller strip -- and
                // the strip is a FlowRow, so the cost of the full set is a second line.
                version.nativeAbis.forEach { abi ->
                    FdroidVersionPill(label = abi, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }

        // F-Droid's own warnings, and the reason a reader would pick an older build over a newer one.
        if (version.antiFeatures.isNotEmpty()) {
            FdroidAntiFeatureCard(
                features = version.antiFeatures,
                expanded = antiFeaturesExpanded,
                onExpandedChange = onAntiFeaturesExpandedChange,
            )
        }

        // Only when there are notes. The release list shows an empty notes section because it *fetched*
        // and found none, which is worth saying; here a blank field means the index never carried any, and
        // on a repository whose package API publishes only names and codes that is every row -- three
        // accordions that open onto one line of apology.
        val notes = version.whatsNew.trim()
        if (notes.isNotBlank()) {
            FdroidVersionNestedCard(
                title = stringResource(R.string.github_fdroid_detail_label_release_notes),
                expanded = notesExpanded,
                onExpandedChange = onNotesExpandedChange,
            ) {
                AppMarkdownContent(
                    markdown = notes,
                    titleColor = MiuixTheme.colorScheme.onBackground,
                    subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    accentColor = MiuixTheme.colorScheme.primary,
                    codeContainerColor = MiuixTheme.colorScheme.surfaceContainer,
                    sourceKey = row.id,
                    onOpenLink = actions.onOpenLink,
                )
            }
        }

        val asset =
            remember(row.id, actions.repoUrl) {
                fdroidVersionAssetFile(
                    repoUrl = actions.repoUrl,
                    apkName = version.apkName,
                    apkPath = version.apkPath,
                    apkSha256 = version.apkSha256,
                    apkSizeBytes = version.apkSizeBytes,
                    addedAtMillis = version.addedAtMillis,
                    signerSha256 = version.signerSha256,
                )
            }
        // Dropped entirely when the repository publishes no files for anything: the page already said so
        // once, and an APK section that only repeats it is a heading over a paragraph the reader has read.
        // Kept when *some* builds have a file, because then its absence is about this build.
        if (asset != null || actions.anyFilePublished) {
            FdroidVersionNestedCard(
                title = stringResource(R.string.github_fdroid_detail_label_apk),
                expanded = apkExpanded,
                onExpandedChange = onApkExpandedChange,
            ) {
                if (asset == null) {
                    // Not "refresh and it will appear". f-droid.org's `/api/v1/packages` answers with a
                    // version name and a code and nothing else -- no file, no size, no hash -- so for a track
                    // whose repository is read that way, no refresh will ever fill this in. Saying otherwise
                    // sends the reader to press a button that cannot work.
                    Text(
                        text = stringResource(R.string.github_fdroid_version_apk_not_published),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                } else {
                    // The tracked card's own asset row, so a file reads the same on all three F-Droid
                    // surfaces: ABI and extension pills, size, age, the trust check, and icon actions.
                    GitHubTrackedItemAssetRow(
                        asset = asset,
                        expectedPackageName = actions.packageName,
                        alwaysLatestReleaseDownload = false,
                        targetAccent = accent,
                        summaryContainerColor =
                            GitHubStatusPalette
                                .tonedSurface(accent, isDark = isDark)
                                .copy(alpha = if (isDark) 0.30f else 0.18f),
                        summaryBorderColor = accent.copy(alpha = if (isDark) 0.30f else 0.20f),
                        supportedAbis = supportedAbis,
                        relativeTimeNowMillis = nowMillis,
                        // Verifying an *old* APK's signer matters at least as much as verifying the newest
                        // one, so this follows the same settings the tracked card follows.
                        showApkTrustCheck = actions.lookupConfig.decisionAssistEnabled &&
                            actions.lookupConfig.apkTrustCheckEnabled,
                        managedInstallEnabled = actions.lookupConfig.appManagedShareInstallEnabled,
                        manifestInfo = null,
                        managedInstallRunning = false,
                        installActionColor = MiuixTheme.colorScheme.primary,
                        context = context,
                        onOpenApkInfo = {
                            actions.packagePageUrl.takeIf(String::isNotBlank)?.let(actions.onOpenLink)
                        },
                        onInstallApk = { actions.onOpenLink(asset.downloadUrl) },
                        onOpenApkInDownloader = { actions.onOpenLink(asset.downloadUrl) },
                        onShareApkLink = actions.onShareAsset,
                    )
                }
            }
        }
    }
}

/**
 * The anti-features F-Droid records against a build, in a section of their own.
 *
 * Its own card rather than a bare tinted strip, and that is the point: a GitHub release has no concept of
 * these, so nothing on the release page taught the reader what a lone red `NonFreeAdd` beside a version
 * number is. A titled section names the category once, the way the notes and APK sections do.
 *
 * The pills are the tags; the value is the sentence under each one. index-v2 states, per build, *why* it
 * carries the flag — "The app contains the Tasker plugin", "The app uses Bugsnag" — which is a different
 * and far more useful thing than the repository's general description of the category. Open by default,
 * because a warning the reader has to go looking for is not doing its job.
 */
@Composable
private fun FdroidAntiFeatureCard(
    features: List<FdroidAntiFeatureSnapshot>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val isDark = isAppInDarkTheme()
    AppLiquidAccordionCard(
        backdrop = null,
        title = stringResource(R.string.github_fdroid_detail_section_anti_features),
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        // Tinted with the warning colour rather than the neutral veil the other sections use, so the one
        // section that carries a caution does not read as another fold of detail.
        containerColor =
            GitHubStatusPalette
                .tonedSurface(AppStatusColors.Failed, isDark = isDark)
                .copy(alpha = if (isDark) 0.26f else 0.16f),
        titleAccessory = {
            FdroidVersionPill(
                label = features.size.toString(),
                color = AppStatusColors.Failed,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            features.forEach { feature ->
                val reason = feature.description.trim()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FdroidVersionPill(
                        label = feature.label.trim().ifBlank { feature.id },
                        color = AppStatusColors.Failed,
                        // These read as short sentences rather than tags on some repositories, and
                        // clipping one to a line loses the warning.
                        maxLines = 2,
                    )
                    if (reason.isNotBlank()) {
                        Text(
                            text = reason,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A real card inside the version card, not a tinted strip.
 *
 * Notes and the APK are each a section a reader opens on their own terms, so they get the same accordion
 * the page's cards use. Same treatment, and the same reason, as the release card's two.
 */
@Composable
private fun FdroidVersionNestedCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    AppLiquidAccordionCard(
        backdrop = null,
        title = title,
        subtitle = "",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        // A step away from the parent rather than a heavier coat of the same paint: `surfaceContainer` is
        // near-white in light, so stacking it on a white card renders nothing.
        containerColor =
            MiuixTheme.colorScheme.onBackground.copy(
                alpha = if (isAppInDarkTheme()) 0.10f else 0.05f,
            ),
        content = content,
    )
}

/**
 * Two actions, on one floating Liquid surface.
 *
 * The release list's bar has four because that source pages. This one has nothing to page: the history
 * arrives whole, so what is left is fetching it again and narrowing it to what this device can run.
 */
@Composable
private fun FdroidVersionBar(
    modifier: Modifier,
    backdrop: Backdrop?,
    busy: Boolean,
    compatibleOnly: Boolean,
    onRefresh: () -> Unit,
    onToggleCompatibleOnly: () -> Unit,
) {
    AppLiquidFloatingSurface(
        modifier =
            modifier
                .width(FdroidVersionBarWidth)
                .height(AppChromeTokens.floatingBottomBarOuterHeight),
        backdrop = backdrop,
    ) {
        Row(
            modifier = Modifier.wrapContentWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FdroidVersionBarAction(
                icon = appLucideFilterIcon(),
                enabled = !busy,
                highlighted = compatibleOnly,
                // Said rather than only tinted: a filter whose whole effect is removing rows cannot
                // announce itself the same way on and off, and a tint is nothing to a screen reader.
                contentDescription =
                    stringResource(
                        if (compatibleOnly) {
                            R.string.github_fdroid_version_filter_compatible_active
                        } else {
                            R.string.github_fdroid_version_filter_compatible
                        },
                    ),
                onClick = onToggleCompatibleOnly,
                modifier = Modifier.testTag(KeiOsTestTags.FdroidVersionCompatibleFilterButton),
            )
            FdroidVersionBarAction(
                icon = appLucideRefreshIcon(),
                enabled = !busy,
                contentDescription = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                modifier = Modifier.testTag(KeiOsTestTags.FdroidVersionRefreshButton),
            )
        }
    }
}

@Composable
private fun FdroidVersionBarAction(
    icon: ImageVector,
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
}

/**
 * Narrows the history as it is typed into.
 *
 * The same Liquid capsule the back button is, the way the release list's page-jump field is — but wider,
 * because what goes in here is a version rather than a page number, and no submit, because there is
 * nothing to fetch.
 */
@Composable
private fun FdroidVersionFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldDescription = stringResource(R.string.github_fdroid_version_filter_cd)
    AppLiquidFloatingSurface(
        modifier =
            Modifier
                // A version's worth of room and no more: the title card sits right beside this, and the
                // page reserves exactly this much of the title's width for it.
                .width(96.dp)
                // The height the back button uses, so the field centres against the title card instead of
                // riding above it — the top-end overlay pins by its top edge.
                .height(52.dp),
        shape = ContinuousCapsule,
        backdrop = backdrop,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = { text -> onValueChange(text.take(40)) },
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                // The default brush is opaque black, which on this app's dark theme is a caret drawn on
                // near-black. The release list's page field has the same bug; this one does not inherit it.
                cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        // The field is empty until it is typed into, so there is nothing on screen to say
                        // what the capsule is. The release list's equivalent always shows a page number.
                        .semantics { contentDescription = fieldDescription }
                        .testTag(KeiOsTestTags.FdroidVersionFilterField),
            )
            // Drawn behind, and only while empty: a bare capsule beside the back button reads as a
            // disabled button rather than as somewhere to type.
            if (value.isEmpty()) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = appLucideSearchIcon(),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun FdroidVersionNotice(
    text: String? = null,
    textRes: Int? = null,
) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
        text = text ?: textRes?.let { res -> stringResource(res) }.orEmpty(),
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}

/**
 * Flat rather than glass, the way the release card's pills are.
 *
 * A pill sits on the card's own uniform fill, where a blur returns that fill — the same pixels for the
 * price of an offscreen layer.
 */
@Composable
private fun FdroidVersionPill(
    label: String,
    color: Color,
    maxLines: Int = 1,
) {
    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
        StatusPill(
            label = label,
            color = { color },
            size = AppStatusPillSize.Compact,
            backdrop = null,
            maxLines = maxLines,
        )
    }
}

/**
 * `min 24 · target 34`, or nothing when the index said neither.
 *
 * Through the same resource the F-Droid detail sheet uses, so the two surfaces read identically and a
 * translator has one string to change rather than two.
 */
@Composable
private fun FdroidVersionSnapshot.sdkLabel(): String? {
    val min = minSdk
    val target = targetSdk
    return when {
        min != null && target != null ->
            stringResource(R.string.github_fdroid_detail_value_sdk, min.toString(), target.toString())

        min != null -> stringResource(R.string.github_fdroid_version_sdk_min_format, min)
        target != null -> stringResource(R.string.github_fdroid_version_sdk_target_format, target)
        else -> null
    }
}

/** One badge, resolved: the pre-release one's label depends on which channel the build is in. */
private class FdroidVersionBadge(
    val labelRes: Int,
    private val tint: FdroidBadgeTint,
) {
    @Composable
    fun color(): Color =
        when (tint) {
            FdroidBadgeTint.Installed -> GitHubStatusPalette.Active
            FdroidBadgeTint.Recommended -> AppStatusColors.Fresh
            FdroidBadgeTint.PreRelease -> FdroidPreReleaseColor
            FdroidBadgeTint.Incompatible -> AppStatusColors.Failed
        }
}

private enum class FdroidBadgeTint { Installed, Recommended, PreRelease, Incompatible }

/**
 * A build's badges, most decision-relevant first.
 *
 * Ordered rather than merely collected, because the title row has space for exactly one: what is already
 * on your device outranks what the track would install, which outranks the channel, which outranks a
 * build you could not install anyway.
 */
private fun FdroidVersionRow.badges(): List<FdroidVersionBadge> =
    buildList {
        if (installed) {
            add(
                FdroidVersionBadge(
                    labelRes = R.string.github_fdroid_version_badge_installed,
                    tint = FdroidBadgeTint.Installed,
                ),
            )
        }
        if (recommended) {
            add(
                FdroidVersionBadge(
                    labelRes = R.string.github_fdroid_version_badge_recommended,
                    tint = FdroidBadgeTint.Recommended,
                ),
            )
        }
        channel.preReleaseLabelRes()?.let { labelRes ->
            add(FdroidVersionBadge(labelRes = labelRes, tint = FdroidBadgeTint.PreRelease))
        }
        if (!compatible) {
            add(
                FdroidVersionBadge(
                    labelRes = R.string.github_fdroid_version_badge_incompatible,
                    tint = FdroidBadgeTint.Incompatible,
                ),
            )
        }
    }

private fun FdroidVersionRow.primaryBadge(): FdroidVersionBadge? = badges().firstOrNull()

/** Null for a stable build; the channel's own name for anything else. */
private fun GitHubReleaseChannel.preReleaseLabelRes(): Int? =
    when (this) {
        GitHubReleaseChannel.DEV -> R.string.github_fdroid_version_channel_dev
        GitHubReleaseChannel.ALPHA -> R.string.github_fdroid_version_channel_alpha
        GitHubReleaseChannel.BETA -> R.string.github_fdroid_version_channel_beta
        GitHubReleaseChannel.RC -> R.string.github_fdroid_version_channel_rc
        GitHubReleaseChannel.PREVIEW -> R.string.github_fdroid_version_channel_preview
        GitHubReleaseChannel.STABLE, GitHubReleaseChannel.UNKNOWN -> null
    }

/** Two 48dp actions, 4dp between them, and the capsule's own 8dp each side. */
private val FdroidVersionBarWidth = 120.dp

/** The same amber GitHub marks a pre-release with, which this app already uses for its warnings. */
private val FdroidPreReleaseColor = Color(0xFFF59E0B)
