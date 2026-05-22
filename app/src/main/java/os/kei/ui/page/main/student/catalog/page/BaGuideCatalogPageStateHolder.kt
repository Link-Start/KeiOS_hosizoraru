package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

@Stable
internal class BaGuideCatalogPageStateHolder(
    private val selectedTabIndexState: MutableIntState,
    private val searchQueriesState: MutableState<Map<String, String>>,
) {
    var selectedTabIndex: Int
        get() = selectedTabIndexState.intValue
        private set(value) {
            selectedTabIndexState.intValue = value
        }

    var searchQueries: Map<String, String>
        get() = searchQueriesState.value
        private set(value) {
            searchQueriesState.value = value
        }

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
            selectedTabIndex = pageIndex
        }
    }

    fun updateSelectedTabIndex(index: Int) {
        selectedTabIndex = index
    }

    fun updateSearchQuery(
        tab: BaGuideCatalogPageTab,
        query: String,
    ) {
        searchQueries = searchQueries + (tab.name to query)
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
        selectedTabIndex = playbackTabIndex
        closeSearch()
    }
}

@Composable
internal fun rememberBaGuideCatalogPageStateHolder(): BaGuideCatalogPageStateHolder {
    val selectedTabIndexState = rememberSaveable { mutableIntStateOf(0) }
    val searchQueriesState = rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    return remember(selectedTabIndexState, searchQueriesState) {
        BaGuideCatalogPageStateHolder(
            selectedTabIndexState = selectedTabIndexState,
            searchQueriesState = searchQueriesState,
        )
    }
}
