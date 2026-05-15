package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogTabContentUiState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

@Composable
internal fun BaGuideCatalogTabListLayout(
    listState: LazyListState,
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
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
            bottom = innerPadding.calculateBottomPadding() + AppChromeTokens.pageSectionGap,
            start = AppChromeTokens.pageHorizontalPadding,
            end = AppChromeTokens.pageHorizontalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppChromeTokens.pageSectionGap)
    ) {
        if (uiState.showError) {
            item(
                key = "ba-guide-tab-error",
                contentType = "ba_guide_catalog_status"
            ) {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.syncStatusTitle,
                    subtitle = uiState.errorText,
                    body = uiState.syncStatusBody,
                    accent = Color(0xFFEF4444)
                )
            }
        }
        if (uiState.showEmpty) {
            item(
                key = "ba-guide-tab-empty",
                contentType = "ba_guide_catalog_status"
            ) {
                LiquidInfoBlock(
                    backdrop = null,
                    title = uiState.emptyTitle,
                    subtitle = uiState.emptySubtitle,
                    accent = accent
                )
            }
        } else {
            renderBaGuideCatalogEntryListAdapter(
                displayedEntries = displayedEntries,
                hasMoreEntries = hasMoreEntries,
                favoriteCatalogEntries = favoriteCatalogEntries,
                accent = accent,
                loadingMoreText = uiState.loadingMoreText,
                onOpenGuide = onOpenGuide,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}
