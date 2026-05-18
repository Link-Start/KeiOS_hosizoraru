package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

@Stable
internal data class BaGuideCatalogTabContentUiState(
    val tabTitle: String,
    val syncStatusTitle: String,
    val syncStatusBody: String,
    val showError: Boolean,
    val errorText: String,
    val showLoading: Boolean,
    val showEmpty: Boolean,
    val emptyTitle: String,
    val emptySubtitle: String,
    val loadingMoreText: String,
)

@Composable
internal fun rememberBaGuideCatalogTabContentUiState(
    tab: BaGuideCatalogTab,
    searchQuery: String,
    activeFilterCount: Int,
    loading: Boolean,
    error: String?,
    filteredEntriesEmpty: Boolean,
): BaGuideCatalogTabContentUiState {
    val tabLabel = stringResource(tab.labelRes)
    val tabTitle = stringResource(R.string.ba_catalog_tab_title, tabLabel)
    val syncStatusTitle = stringResource(R.string.ba_catalog_sync_status_title)
    val syncStatusBody = stringResource(R.string.ba_catalog_sync_status_body_retry)
    val emptyTitle = stringResource(R.string.ba_catalog_empty_title)
    val emptySubtitle =
        when {
            searchQuery.isNotBlank() -> stringResource(R.string.ba_catalog_empty_subtitle_search)
            activeFilterCount > 0 -> stringResource(R.string.ba_catalog_empty_subtitle_filter)
            else -> stringResource(R.string.ba_catalog_empty_subtitle_default)
        }
    val loadingMoreText = stringResource(R.string.ba_catalog_loading_more)
    return remember(
        tab,
        tabLabel,
        tabTitle,
        syncStatusTitle,
        syncStatusBody,
        loading,
        error,
        filteredEntriesEmpty,
        activeFilterCount,
        emptyTitle,
        emptySubtitle,
        loadingMoreText,
    ) {
        BaGuideCatalogTabContentUiState(
            tabTitle = tabTitle,
            syncStatusTitle = syncStatusTitle,
            syncStatusBody = syncStatusBody,
            showError = !error.isNullOrBlank(),
            errorText = error.orEmpty(),
            showLoading = loading && filteredEntriesEmpty,
            showEmpty = !loading && filteredEntriesEmpty,
            emptyTitle = emptyTitle,
            emptySubtitle = emptySubtitle,
            loadingMoreText = loadingMoreText,
        )
    }
}
