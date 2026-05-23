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
    val showTransferSheet: Boolean = false,
    val importPreviewState: BaGuideCatalogImportPreviewState? = null,
    val searchVisible: Boolean = false,
    val searchInputActive: Boolean = false,
)

@Stable
internal class BaGuideCatalogPageStateHolder(
    private val chromeState: () -> BaGuideCatalogPageChromeState,
    private val actions: () -> BaGuideCatalogPageChromeActions,
) {
    val selectedTabIndex: Int
        get() = chromeState().selectedTabIndex

    val searchQueries: Map<String, String>
        get() = chromeState().searchQueries

    val showTransferSheet: Boolean
        get() = chromeState().showTransferSheet

    val importPreviewState: BaGuideCatalogImportPreviewState?
        get() = chromeState().importPreviewState

    val searchVisible: Boolean
        get() = chromeState().searchVisible

    val searchInputActive: Boolean
        get() = chromeState().searchInputActive

    var playbackSliderPreview by mutableStateOf<Float?>(null)
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
            actions().onSelectedTabIndexChange(pageIndex)
        }
    }

    fun updateSelectedTabIndex(index: Int) {
        actions().onSelectedTabIndexChange(index)
    }

    fun updateSearchQuery(
        tab: BaGuideCatalogPageTab,
        query: String,
    ) {
        actions().onSearchQueriesChange(searchQueries + (tab.name to query))
    }

    fun openSearch(autoFocus: Boolean) {
        actions().onSearchVisibilityChange(true, autoFocus)
    }

    fun closeSearch() {
        actions().onSearchVisibilityChange(false, false)
    }

    fun updateSearchInputActive(active: Boolean) {
        actions().onSearchVisibilityChange(active || searchVisible, active)
    }

    fun updateSliderInteractionActive(active: Boolean) {
        sliderInteractionActive = active
        if (!active) playbackSliderPreview = null
    }

    fun updatePlaybackSliderPreview(progress: Float?) {
        playbackSliderPreview = progress
    }

    fun openTransferSheet() {
        actions().onShowTransferSheetChange(true)
    }

    fun closeTransferSheet() {
        actions().onShowTransferSheetChange(false)
    }

    fun updateImportPreviewState(state: BaGuideCatalogImportPreviewState?) {
        actions().onImportPreviewStateChange(state)
    }

    fun activatePlaybackTab(playbackTabIndex: Int) {
        actions().onSelectedTabIndexChange(playbackTabIndex)
        closeSearch()
    }
}

@Stable
internal data class BaGuideCatalogPageChromeActions(
    val onSelectedTabIndexChange: (Int) -> Unit,
    val onSearchQueriesChange: (Map<String, String>) -> Unit,
    val onShowTransferSheetChange: (Boolean) -> Unit,
    val onImportPreviewStateChange: (BaGuideCatalogImportPreviewState?) -> Unit,
    val onSearchVisibilityChange: (visible: Boolean, inputActive: Boolean) -> Unit,
)

@Composable
internal fun rememberBaGuideCatalogPageStateHolder(
    chromeState: BaGuideCatalogPageChromeState,
    chromeActions: BaGuideCatalogPageChromeActions,
): BaGuideCatalogPageStateHolder {
    val currentChromeState = rememberUpdatedState(chromeState)
    val currentChromeActions = rememberUpdatedState(chromeActions)
    return remember {
        BaGuideCatalogPageStateHolder(
            chromeState = { currentChromeState.value },
            actions = { currentChromeActions.value },
        )
    }
}
