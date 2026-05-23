package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

@Stable
internal data class BaGuideCatalogPageChromeState(
    val selectedTabIndex: Int = 0,
    val searchQueries: Map<String, String> = emptyMap(),
)

@Stable
internal class BaGuideCatalogPageStateHolder(
    private val chromeState: () -> BaGuideCatalogPageChromeState,
    private val onSelectedTabIndexChange: (Int) -> Unit,
    private val onSearchQueriesChange: (Map<String, String>) -> Unit,
) {
    val selectedTabIndex: Int
        get() = chromeState().selectedTabIndex

    val searchQueries: Map<String, String>
        get() = chromeState().searchQueries

    var showTransferSheet by mutableStateOf(false)
        private set
    var importPreviewState by mutableStateOf<BaGuideCatalogImportPreviewState?>(null)
        private set
    var playbackSliderPreview by mutableStateOf<Float?>(null)
        private set
    var searchVisible by mutableStateOf(false)
        private set
    var searchInputActive by mutableStateOf(false)
        private set
    var sliderInteractionActive by mutableStateOf(false)
        private set

    val studentSearchQuery: String
        get() = searchQueries.catalogSearchQueryFor(BaGuideCatalogTab.Student)

    val npcSearchQuery: String
        get() = searchQueries.catalogSearchQueryFor(BaGuideCatalogTab.NpcSatellite)

    val studentBgmSearchQuery: String
        get() = searchQueries[BaGuideCatalogPageTab.StudentBgm.name].orEmpty()

    val favoriteBgmSearchQuery: String
        get() = searchQueries[BaGuideCatalogPageTab.Bgm.name].orEmpty()

    fun searchQueryFor(tab: BaGuideCatalogPageTab): String = searchQueries[tab.name].orEmpty()

    fun updateSettledPage(pageIndex: Int) {
        if (selectedTabIndex != pageIndex) {
            onSelectedTabIndexChange(pageIndex)
        }
    }

    fun updateSelectedTabIndex(index: Int) {
        onSelectedTabIndexChange(index)
    }

    fun updateSearchQuery(
        tab: BaGuideCatalogPageTab,
        query: String,
    ) {
        onSearchQueriesChange(searchQueries + (tab.name to query))
    }

    fun openSearch(autoFocus: Boolean) {
        searchVisible = true
        searchInputActive = autoFocus
    }

    fun closeSearch() {
        searchInputActive = false
        searchVisible = false
    }

    fun updateSearchInputActive(active: Boolean) {
        searchInputActive = active
        if (active) searchVisible = true
    }

    fun updateSliderInteractionActive(active: Boolean) {
        sliderInteractionActive = active
        if (!active) playbackSliderPreview = null
    }

    fun updatePlaybackSliderPreview(progress: Float?) {
        playbackSliderPreview = progress
    }

    fun openTransferSheet() {
        showTransferSheet = true
    }

    fun closeTransferSheet() {
        showTransferSheet = false
    }

    fun updateImportPreviewState(state: BaGuideCatalogImportPreviewState?) {
        importPreviewState = state
    }

    fun activatePlaybackTab(playbackTabIndex: Int) {
        onSelectedTabIndexChange(playbackTabIndex)
        closeSearch()
    }
}

@Composable
internal fun rememberBaGuideCatalogPageStateHolder(
    chromeState: BaGuideCatalogPageChromeState,
    onSelectedTabIndexChange: (Int) -> Unit,
    onSearchQueriesChange: (Map<String, String>) -> Unit,
): BaGuideCatalogPageStateHolder {
    val currentChromeState = rememberUpdatedState(chromeState)
    val currentOnSelectedTabIndexChange = rememberUpdatedState(onSelectedTabIndexChange)
    val currentOnSearchQueriesChange = rememberUpdatedState(onSearchQueriesChange)
    return remember {
        BaGuideCatalogPageStateHolder(
            chromeState = { currentChromeState.value },
            onSelectedTabIndexChange = { currentOnSelectedTabIndexChange.value(it) },
            onSearchQueriesChange = { currentOnSearchQueriesChange.value(it) },
        )
    }
}
