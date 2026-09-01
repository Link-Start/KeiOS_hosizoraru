@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideMemoryLobbyListDerivedState
import os.kei.ui.page.main.student.catalog.state.rememberBaGuideCatalogTabListState
import os.kei.ui.page.main.student.catalog.state.visibleMemoryLobbyEntriesWithFavoriteVisibility
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppEdgeStackListTopInset
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MEMORY_LOBBY_ENTRY_START_INDEX = 1
private const val MEMORY_LOBBY_VISIBLE_PREWARM_LIMIT = 6

private data class BaGuideMemoryLobbyVisibleWork(
    val imageUrls: List<String>,
    val prewarmEntries: List<BaGuideCatalogEntry>,
)

private data class BaGuideMemoryLobbyHeaderCounts(
    val readyCount: Int,
    val favoriteCount: Int,
    val cachedCount: Int,
)

@Composable
internal fun BaGuideMemoryLobbyTabContent(
    catalogSyncedAtMs: Long,
    catalogSceneBackdrop: Backdrop,
    derivedState: BaGuideMemoryLobbyListDerivedState,
    searchQuery: String,
    loading: Boolean,
    error: String?,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    accent: Color,
    isPageActive: Boolean,
    scrollToTopSignal: Int,
    mediaAdaptiveRotationEnabled: Boolean,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onRequestVisibleImages: (List<String>) -> Unit,
    onOpenGuide: (String) -> Unit,
    onRequestGuideDetailTab: (String, GuideBottomTab) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val pageScope = rememberCoroutineScope()
    val lookupCoordinator =
        remember(pageScope) {
            BaGuideMemoryLobbyLookupCoordinator(scope = pageScope)
        }
    val lookupStates by lookupCoordinator.states.collectAsStateWithLifecycle()
    val requestVisibleImages by rememberUpdatedState(onRequestVisibleImages)
    val allStudentEntries = derivedState.allStudentEntries
    val filteredEntries = derivedState.filteredEntries
    val favoriteContentIds = derivedState.favoriteContentIds
    var favoritesHidden by rememberSaveable { mutableStateOf(false) }
    val visibleFilteredEntries =
        remember(filteredEntries, favoriteContentIds, favoritesHidden) {
            visibleMemoryLobbyEntriesWithFavoriteVisibility(
                filteredEntries = filteredEntries,
                favoriteContentIds = favoriteContentIds,
                favoritesHidden = favoritesHidden,
            )
        }
    val visibleFilteredContentIds =
        remember(visibleFilteredEntries) {
            visibleFilteredEntries.mapTo(LinkedHashSet()) { entry -> entry.contentId }
        }
    val effectiveLoading = loading || (derivedState.deriving && allStudentEntries.isEmpty())
    val listStateHolder =
        rememberBaGuideCatalogTabListState(
            tab = BaGuideCatalogTab.Student,
            filteredEntries = visibleFilteredEntries,
            loading = effectiveLoading,
            isPageActive = isPageActive,
        )
    val listState = listStateHolder.listState
    val displayedEntries = listStateHolder.displayedEntries
    val showError = !error.isNullOrBlank()
    val showLoading = effectiveLoading && allStudentEntries.isEmpty()
    val showEmpty = !effectiveLoading && visibleFilteredEntries.isEmpty()
    val entryStartIndex = if (showError) MEMORY_LOBBY_ENTRY_START_INDEX + 1 else MEMORY_LOBBY_ENTRY_START_INDEX
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    var consumedScrollToTopSignal by remember { mutableStateOf(0) }
    var expandedContentIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val favoriteCount = favoriteContentIds.size
    val headerCounts =
        remember(visibleFilteredContentIds, lookupStates, favoriteCount) {
            var readyCount = 0
            var cachedCount = 0
            lookupStates.forEach { (contentId, state) ->
                if (contentId in visibleFilteredContentIds && state is BaGuideMemoryLobbyLookupState.Ready) {
                    readyCount += 1
                    if (state.item.fromCache) {
                        cachedCount += 1
                    }
                }
            }
            BaGuideMemoryLobbyHeaderCounts(
                readyCount = readyCount,
                favoriteCount = favoriteCount,
                cachedCount = cachedCount,
            )
        }

    LaunchedEffect(catalogSyncedAtMs) {
        lookupCoordinator.clear()
    }
    LaunchedEffect(visibleFilteredContentIds) {
        expandedContentIds = expandedContentIds.filter { contentId -> contentId in visibleFilteredContentIds }.toSet()
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > consumedScrollToTopSignal && isPageActive) {
            consumedScrollToTopSignal = scrollToTopSignal
            listState.animateScrollToItem(0)
        } else {
            consumedScrollToTopSignal = scrollToTopSignal
        }
    }
    LaunchedEffect(listState, isPageActive, snapshotFlowManager) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                listState.canScrollBackward to listState.canScrollForward
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
    // Two columns on a tablet or an unfolded fold, flowing row-major so the list keeps one order and one
    // scroll. An expanded entry takes the whole row: it opens a lobby illustration taller than the viewport,
    // and pairing that with a collapsed entry would leave most of a panel-height cell empty.
    val columnsPerRow = appPageColumnCount()
    val entryRows =
        remember(displayedEntries, columnsPerRow, expandedContentIds) {
            baGuideCatalogEntryRows(
                entries = displayedEntries,
                columnsPerRow = columnsPerRow,
                isFullSpan = { entry -> entry.contentId in expandedContentIds },
            )
        }
    LaunchedEffect(
        listState,
        displayedEntries,
        entryRows,
        isPageActive,
        showLoading,
        showEmpty,
        entryStartIndex,
        snapshotFlowManager,
        lookupCoordinator,
    ) {
        if (!isPageActive || showLoading || showEmpty || displayedEntries.isEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlowManager
            .snapshotFlow {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                // Resolved through the rows: one visible item is two entries in two columns, and only
                // one where that row is a full span.
                val visibleItemRange =
                    baGuideCatalogVisibleEntryRange(
                        rows = entryRows,
                        visibleItemRange =
                            BaGuideVisibleItemRange(
                                firstItemIndex = visibleItems.firstOrNull()?.index ?: -1,
                                lastItemIndex = visibleItems.lastOrNull()?.index ?: -1,
                                visibleItemCount = visibleItems.size,
                            ),
                        entryStartIndex = entryStartIndex,
                    )
                BaGuideMemoryLobbyVisibleWork(
                    imageUrls =
                        buildBaGuideCatalogVisibleImageRequestUrls(
                            displayedEntries = displayedEntries,
                            visibleItemRange = visibleItemRange,
                            entryStartIndex = 0,
                        ),
                    prewarmEntries =
                        buildBaGuideStudentBgmVisiblePrewarmEntries(
                            displayedEntries = displayedEntries,
                            visibleItemRange = visibleItemRange,
                            entryStartIndex = 0,
                            limit = MEMORY_LOBBY_VISIBLE_PREWARM_LIMIT,
                        ),
                )
            }.distinctUntilChanged()
            .collect { work ->
                requestVisibleImages(work.imageUrls)
                lookupCoordinator.prewarmCached(work.prewarmEntries)
                lookupCoordinator.prewarmVisibleNetwork(work.prewarmEntries)
            }
    }
    val entryListGap = rememberBaGuideCatalogEntryListGap()
    val showPinnedLobbyHeader = !showLoading
    val listTopPadding =
        if (showPinnedLobbyHeader) {
            AppEdgeStackListTopInset
        } else {
            innerPadding.calculateTopPadding()
        }
    val edgeStackState = rememberAppEdgeStackState(stackLine = listTopPadding)
    Column(modifier = Modifier.fillMaxSize()) {
    if (showPinnedLobbyHeader) {
        // The lobby summary is this tab's status hub: pinned above the list so the
        // memorial-lobby pile always forms beneath it.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        top = innerPadding.calculateTopPadding(),
                    ),
        ) {
            BaGuideMemoryLobbyHeader(
                totalCount = allStudentEntries.size,
                displayedCount = visibleFilteredEntries.size,
                readyCount = headerCounts.readyCount,
                favoriteCount = headerCounts.favoriteCount,
                cachedCount = headerCounts.cachedCount,
                searchActive = searchQuery.isNotBlank(),
                favoritesHidden = favoritesHidden,
                accent = accent,
                onToggleFavoritesHidden = {
                    if (favoriteContentIds.isNotEmpty()) {
                        favoritesHidden = !favoritesHidden
                    }
                },
            )
        }
    }
    CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
    // No PullToRefresh on this tab, so the keep-alive box takes the list's own weight and the list
    // fills it. The headroom lands in `contentPadding.top` rather than a `topExtra`.
    AppEdgeStackKeepAlive(
        state = edgeStackState,
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
    ) {
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        contentPadding =
            PaddingValues(
                top = appEdgeStackKeepAliveTopPadding(listTopPadding),
                bottom = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
                start = appPageEdgePaddingStart(),
                end = appPageEdgePaddingEnd(),
            ),
        verticalArrangement = Arrangement.spacedBy(entryListGap),
    ) {
        if (showError) {
            item(
                key = "memory-lobby-error",
                contentType = "memory_lobby_status",
            ) {
                LiquidInfoBlock(
                    backdrop = catalogSceneBackdrop,
                    title = stringResource(R.string.ba_catalog_sync_status_title),
                    subtitle = error.orEmpty(),
                    body = stringResource(R.string.ba_catalog_sync_status_body_retry),
                    accent = Color(0xFFEF4444),
                )
            }
        }
        if (showLoading) {
            item(
                key = "memory-lobby-loading",
                contentType = "memory_lobby_status",
            ) {
                AppAronaLoadingPanel(accent = accent)
            }
        }

        if (showEmpty) {
            item(
                key = "memory-lobby-empty",
                contentType = "memory_lobby_status",
            ) {
                LiquidInfoBlock(
                    backdrop = catalogSceneBackdrop,
                    title = stringResource(R.string.ba_catalog_empty_title),
                    subtitle =
                        stringResource(
                            if (searchQuery.isNotBlank()) {
                                R.string.ba_catalog_empty_subtitle_search
                            } else {
                                R.string.ba_catalog_empty_subtitle_default
                            },
                        ),
                    accent = accent,
                )
            }
        } else if (!showLoading) {
            items(
                items = entryRows,
                key = { row -> "memory-lobby-${row.entries.first().contentId}" },
                contentType = { "memory_lobby_entry" },
            ) { row ->
                BaGuideCatalogEntryRowLayout(
                    row = row,
                    columnsPerRow = columnsPerRow,
                    horizontalGap = entryListGap,
                ) { entry, _ ->
                val expanded = entry.contentId in expandedContentIds
                BaGuideMemoryLobbyCard(
                    entry = entry,
                    lookupState = lookupStates[entry.contentId] ?: BaGuideMemoryLobbyLookupState.Idle,
                    expanded = expanded,
                    favorite = entry.contentId in favoriteContentIds,
                    accent = accent,
                    mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                    onToggleExpanded = {
                        expandedContentIds =
                            if (expanded) {
                                expandedContentIds - entry.contentId
                            } else {
                                expandedContentIds + entry.contentId
                            }
                    },
                    onResolve = {
                        lookupCoordinator.resolveEntry(
                            entry = entry,
                            allowNetwork = true,
                        )
                    },
                    onOpenGuide = {
                        onRequestGuideDetailTab(entry.detailUrl, GuideBottomTab.Gallery)
                        onOpenGuide(entry.detailUrl)
                    },
                    onToggleFavorite = { onToggleFavorite(entry.contentId) },
                )
                }
            }

            if (listStateHolder.hasMoreEntries) {
                item(
                    key = "memory-lobby-loading-more",
                    contentType = "memory_lobby_loading_more",
                ) {
                    BaGuideCatalogLoadingMoreRow(
                        loadingMoreText = stringResource(R.string.ba_catalog_loading_more),
                        accent = accent,
                    )
                }
            }
        }
    }
    }
    }
    }
}

@Composable
private fun BaGuideCatalogLoadingMoreRow(
    loadingMoreText: String,
    accent: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidCircularProgressBar(
            progress = { 0.3f },
            size = 16.dp,
            strokeWidth = 2.dp,
            activeColor = accent,
            inactiveColor = accent.copy(alpha = 0.30f),
        )
        Text(
            text = loadingMoreText,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}
