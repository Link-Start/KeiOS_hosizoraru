package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogTabContentUiState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

@Composable
internal fun BaGuideCatalogTabListLayout(
    listState: LazyListState,
    secondaryListState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    uiState: BaGuideCatalogTabContentUiState,
    progress: Float,
    progressColor: Color,
    accent: Color,
    displayedEntries: List<BaGuideCatalogEntry>,
    hasMoreEntries: Boolean,
    favoriteCatalogEntries: Map<Long, Long>,
    onOpenGuide: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    val statusBackdrop = rememberLayerBackdrop()
    val showStatusBackdrop = uiState.showError || uiState.showEmpty
    val entryListGap = rememberBaGuideCatalogEntryListGap()
    // Two lanes on a tablet, alternating so each lane stays sorted -- see `baGuideCatalogEntryLanes`.
    // A status-only state has no entries to split and stays on one lane.
    val pageColumnCount = appPageColumnCount()
    val columnCount = if (uiState.showEmpty) 1 else pageColumnCount
    val laneStates = if (columnCount >= 2) listOf(listState, secondaryListState) else listOf(listState)
    val laneEntries =
        remember(displayedEntries, columnCount) {
            baGuideCatalogEntryLanes(entries = displayedEntries, columnCount = columnCount)
        }
    Box(modifier = Modifier.fillMaxSize()) {
        if (showStatusBackdrop) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(statusBackdrop),
            )
        }
        val laneContents =
            laneEntries.mapIndexed { lane, entries ->
                val laneContent: LazyListScope.() -> Unit = {
                    // One status card for the list, in the leading lane, rather than one per column.
                    if (lane == 0 && uiState.showError) {
                        item(
                            key = "ba-guide-tab-error",
                            contentType = "ba_guide_catalog_status",
                        ) {
                            LiquidInfoBlock(
                                backdrop = statusBackdrop,
                                title = uiState.syncStatusTitle,
                                subtitle = uiState.errorText,
                                body = uiState.syncStatusBody,
                                accent = Color(0xFFEF4444),
                            )
                        }
                    }
                    if (uiState.showEmpty) {
                        if (lane == 0) {
                            item(
                                key = "ba-guide-tab-empty",
                                contentType = "ba_guide_catalog_status",
                            ) {
                                LiquidInfoBlock(
                                    backdrop = statusBackdrop,
                                    title = uiState.emptyTitle,
                                    subtitle = uiState.emptySubtitle,
                                    accent = accent,
                                )
                            }
                        }
                    } else {
                        renderBaGuideCatalogEntryListAdapter(
                            laneEntries = entries,
                            hasMoreEntries = hasMoreEntries,
                            favoriteCatalogEntries = favoriteCatalogEntries,
                            accent = accent,
                            loadingMoreText = uiState.loadingMoreText,
                            laneIndex = lane,
                            onOpenGuide = onOpenGuide,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
                laneContent
            }
        BaGuideCatalogLaneLists(
            laneStates = laneStates,
            startPadding = appPageEdgePaddingStart(),
            endPadding = appPageEdgePaddingEnd(),
            topPadding = innerPadding.calculateTopPadding(),
            bottomPadding = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
            horizontalGap = entryListGap,
            verticalGap = entryListGap,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            lanes = laneContents,
        )
    }
}
