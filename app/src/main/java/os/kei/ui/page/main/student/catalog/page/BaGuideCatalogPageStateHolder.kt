package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    val bgmVolumeControlVisible: Boolean = true,
    val bgmLastAudibleVolume: Float = 0.72f,
    val sliderInteractionActive: Boolean = false,
    val studentBgmNowPlayingVisible: Boolean = false,
    val studentBgmNowPlayingExpanded: Boolean = false,
    val studentBgmSliderInteractionActive: Boolean = false,
)

@Stable
internal class BaGuideCatalogPageStateHolder(
    private val chromeState: () -> BaGuideCatalogPageChromeState,
    private val actions: () -> BaGuideCatalogPageChromeActions,
) {
    var scrollToTopSignal by mutableIntStateOf(0)
        private set

    fun emitScrollToTop() {
        scrollToTopSignal++
    }

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

    val bgmVolumeControlVisible: Boolean
        get() = chromeState().bgmVolumeControlVisible

    val bgmLastAudibleVolume: Float
        get() = chromeState().bgmLastAudibleVolume

    val sliderInteractionActive: Boolean
        get() = chromeState().sliderInteractionActive

    val studentBgmNowPlayingVisible: Boolean
        get() = chromeState().studentBgmNowPlayingVisible

    val studentBgmNowPlayingExpanded: Boolean
        get() = chromeState().studentBgmNowPlayingExpanded

    val studentBgmSliderInteractionActive: Boolean
        get() = chromeState().studentBgmSliderInteractionActive

    var playbackSliderPreview by mutableStateOf<Float?>(null)
        private set

    var studentBgmSeekPreviewProgress by mutableStateOf<Float?>(null)
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

    fun updateBgmVolumeControlVisible(visible: Boolean) {
        actions().onBgmVolumeControlVisibleChange(visible)
    }

    fun updateBgmLastAudibleVolume(volume: Float) {
        actions().onBgmLastAudibleVolumeChange(volume)
    }

    fun updateSliderInteractionActive(active: Boolean) {
        if (!active) playbackSliderPreview = null
        actions().onPlaybackSliderInteractionChange(active)
    }

    fun updatePlaybackSliderPreview(progress: Float?) {
        playbackSliderPreview = progress?.coerceIn(0f, 1f)
    }

    fun updateStudentBgmNowPlayingVisible(visible: Boolean) {
        actions().onStudentBgmNowPlayingVisibleChange(visible)
    }

    fun updateStudentBgmNowPlayingExpanded(expanded: Boolean) {
        actions().onStudentBgmNowPlayingExpandedChange(expanded)
    }

    fun updateStudentBgmSliderInteractionActive(active: Boolean) {
        if (!active) studentBgmSeekPreviewProgress = null
        actions().onStudentBgmSliderInteractionChange(active)
    }

    fun updateStudentBgmSeekPreviewProgress(progress: Float?) {
        studentBgmSeekPreviewProgress = progress?.coerceIn(0f, 1f)
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
    val onBgmVolumeControlVisibleChange: (Boolean) -> Unit,
    val onBgmLastAudibleVolumeChange: (Float) -> Unit,
    val onPlaybackSliderInteractionChange: (Boolean) -> Unit,
    val onStudentBgmNowPlayingVisibleChange: (Boolean) -> Unit,
    val onStudentBgmNowPlayingExpandedChange: (Boolean) -> Unit,
    val onStudentBgmSliderInteractionChange: (Boolean) -> Unit,
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
