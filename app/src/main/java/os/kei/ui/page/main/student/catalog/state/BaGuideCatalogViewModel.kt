package os.kei.ui.page.main.student.catalog.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportKind
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportPreviewState
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogJsonExportRequest
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogPageChromeState
import kotlin.time.Duration.Companion.milliseconds

internal data class BaGuideCatalogDataUiState(
    val catalog: BaGuideCatalogBundle = BaGuideCatalogBundle.EMPTY,
    val loading: Boolean = false,
    val error: String? = null,
)

private data class BaGuideCatalogBinding(
    val transitionAnimationsEnabled: Boolean,
    val initialFetchDelayMs: Int,
    val loadFailedText: String,
    val refreshFailedKeepCacheText: String,
)

internal class BaGuideCatalogViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = BaGuideCatalogRepository()
    private var binding: BaGuideCatalogBinding? = null
    private var loadJob: Job? = null
    private var hydrateJob: Job? = null
    private var hydratedSyncedAtMs: Long = -1L
    private val listDerivationController =
        BaGuideCatalogListDerivationController(
            scope = viewModelScope,
            repository = repository,
        )
    private var pendingSafJsonExportRequest: BaGuideCatalogJsonExportRequest? = null
    private var pendingFixedJsonExportRequest: BaGuideCatalogJsonExportRequest? = null
    private val imageController =
        BaGuideCatalogImageController(
            scope = viewModelScope,
            appContext = appContext,
        )

    private val _dataState = MutableStateFlow(BaGuideCatalogDataUiState())
    val dataState: StateFlow<BaGuideCatalogDataUiState> = _dataState.asStateFlow()
    private val _events = MutableSharedFlow<BaGuideCatalogEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BaGuideCatalogEvent> = _events.asSharedFlow()
    private val _catalogFavoriteEntries = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val catalogFavoriteEntries: StateFlow<Map<Long, Long>> = _catalogFavoriteEntries.asStateFlow()

    val favoriteBgms: StateFlow<List<GuideBgmFavoriteItem>> =
        repository
            .bgmFavoritesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
    private val _nativeBgmMediaNotificationEnabled =
        MutableStateFlow(false)
    val nativeBgmMediaNotificationEnabled: StateFlow<Boolean> =
        _nativeBgmMediaNotificationEnabled.asStateFlow()
    private val _transferSettings =
        MutableStateFlow(BaGuideCatalogTransferSettingsUiState())
    val transferSettings: StateFlow<BaGuideCatalogTransferSettingsUiState> =
        _transferSettings.asStateFlow()
    private val _pageChromeState = MutableStateFlow(BaGuideCatalogPageChromeState())
    val pageChromeState: StateFlow<BaGuideCatalogPageChromeState> =
        _pageChromeState.asStateFlow()
    private val _filterSortState = MutableStateFlow(BaGuideCatalogFilterSortSnapshot())
    val filterSortState: StateFlow<BaGuideCatalogFilterSortSnapshot> =
        _filterSortState.asStateFlow()

    val catalogListDerivedStates: StateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>> =
        listDerivationController.catalogListDerivedStates

    /**
     * Per-catalog-tab visible filter definitions (type == 0), pre-computed off the main thread
     * each time the catalog bundle changes so the chrome composable can do an O(1) map lookup
     * keyed on the active tab.
     */
    val catalogVisibleFilterDefinitions: StateFlow<Map<BaGuideCatalogTab, List<BaGuideCatalogFilterDefinition>>> =
        _dataState
            .map { it.catalog }
            .distinctUntilChanged()
            .map { catalog ->
                BaGuideCatalogTab.entries.associateWith { tab ->
                    catalog.filterDefinitions(tab).filter { it.type == 0 }
                }
            }
            .flowOn(AppDispatchers.uiDerivation)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    val studentBgmListDerivedState: StateFlow<BaGuideStudentBgmListDerivedState> =
        listDerivationController.studentBgmListDerivedState

    val favoriteBgmListDerivedState: StateFlow<BaGuideFavoriteBgmListDerivedState> =
        listDerivationController.favoriteBgmListDerivedState

    val studentBgmDisplayedDerivedState: StateFlow<BaGuideStudentBgmDisplayedDerivedState> =
        listDerivationController.studentBgmDisplayedDerivedState

    private val bgmCacheController =
        BaGuideCatalogBgmCacheController(
            scope = viewModelScope,
            appContext = appContext,
            favoriteBgms = favoriteBgms,
            events = _events,
        )
    val bgmCacheSnapshot: StateFlow<BaGuideFavoriteBgmCacheSnapshot> =
        bgmCacheController.bgmCacheSnapshot

    val favoriteBgmOfflineCacheState: StateFlow<BaGuideFavoriteBgmOfflineCacheUiState> =
        bgmCacheController.favoriteBgmOfflineCacheState
    val imageState: StateFlow<BaGuideCatalogImageUiState> =
        imageController.state

    val routeState: StateFlow<BaGuideCatalogRouteState> =
        buildBaGuideCatalogRouteStateFlow(
            scope = viewModelScope,
            dataState = dataState,
            catalogListDerivedStates = catalogListDerivedStates,
            studentBgmListDerivedState = studentBgmListDerivedState,
            favoriteBgmListDerivedState = favoriteBgmListDerivedState,
            studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
            catalogFavoriteEntries = catalogFavoriteEntries,
            favoriteBgms = favoriteBgms,
            bgmCacheSnapshot = bgmCacheSnapshot,
            favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
            nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
            transferSettings = transferSettings,
        )

    init {
        viewModelScope.launch {
            _catalogFavoriteEntries.value = repository.loadCatalogFavorites()
        }
        viewModelScope.launch {
            repository.hydrateBgmFavorites()
        }
        viewModelScope.launch {
            _nativeBgmMediaNotificationEnabled.value = repository.loadNativeBgmMediaNotificationEnabled()
        }
        viewModelScope.launch {
            _transferSettings.value = BaGuideCatalogTransferSettingsRepository.loadSettings()
        }
    }

    fun bind(
        transitionAnimationsEnabled: Boolean,
        initialFetchDelayMs: Int,
        loadFailedText: String,
        refreshFailedKeepCacheText: String,
    ) {
        val next =
            BaGuideCatalogBinding(
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                initialFetchDelayMs = initialFetchDelayMs,
                loadFailedText = loadFailedText,
                refreshFailedKeepCacheText = refreshFailedKeepCacheText,
            )
        if (binding == next &&
            _dataState.value.catalog.entriesByTab.values
                .any { it.isNotEmpty() }
        ) {
            return
        }
        binding = next
        loadCatalog(manualRefresh = false, allowInitialDelay = true)
    }

    fun requestRefresh() {
        loadCatalog(manualRefresh = true, allowInitialDelay = false)
    }

    fun updateCatalogSelectedTabIndex(index: Int) {
        _pageChromeState.update { state ->
            val normalized = index.coerceAtLeast(0)
            if (state.selectedTabIndex == normalized) {
                state
            } else {
                state.copy(selectedTabIndex = normalized)
            }
        }
    }

    fun updateCatalogSearchQueries(searchQueries: Map<String, String>) {
        val normalized =
            searchQueries
                .mapValues { (_, value) -> value.trim() }
                .filterValues { it.isNotBlank() }
        _pageChromeState.update { state ->
            if (state.searchQueries == normalized) {
                state
            } else {
                state.copy(searchQueries = normalized)
            }
        }
    }

    fun updateCatalogTransferSheetVisible(visible: Boolean) {
        _pageChromeState.update { state ->
            if (state.showTransferSheet == visible) {
                state
            } else {
                state.copy(showTransferSheet = visible)
            }
        }
    }

    fun updateCatalogImportPreviewState(previewState: BaGuideCatalogImportPreviewState?) {
        _pageChromeState.update { state ->
            if (state.importPreviewState == previewState) {
                state
            } else {
                state.copy(importPreviewState = previewState)
            }
        }
    }

    fun updateCatalogSearchVisibility(
        visible: Boolean,
        inputActive: Boolean,
    ) {
        val normalizedInputActive = inputActive && visible
        _pageChromeState.update { state ->
            if (state.searchVisible == visible && state.searchInputActive == normalizedInputActive) {
                state
            } else {
                state.copy(
                    searchVisible = visible,
                    searchInputActive = normalizedInputActive,
                )
            }
        }
    }

    fun updateCatalogBgmVolumeControlVisible(visible: Boolean) {
        _pageChromeState.update { state ->
            if (state.bgmVolumeControlVisible == visible) {
                state
            } else {
                state.copy(bgmVolumeControlVisible = visible)
            }
        }
    }

    fun updateCatalogBgmLastAudibleVolume(volume: Float) {
        val normalized = volume.coerceIn(0.12f, 1f)
        _pageChromeState.update { state ->
            if (state.bgmLastAudibleVolume == normalized) {
                state
            } else {
                state.copy(bgmLastAudibleVolume = normalized)
            }
        }
    }

    fun updateCatalogPlaybackSliderInteractionActive(active: Boolean) {
        _pageChromeState.update { state ->
            if (state.sliderInteractionActive == active) {
                state
            } else {
                state.copy(sliderInteractionActive = active)
            }
        }
    }

    fun updateCatalogStudentBgmNowPlayingVisible(visible: Boolean) {
        _pageChromeState.update { state ->
            if (state.studentBgmNowPlayingVisible == visible) {
                state
            } else {
                state.copy(studentBgmNowPlayingVisible = visible)
            }
        }
    }

    fun updateCatalogStudentBgmNowPlayingExpanded(expanded: Boolean) {
        _pageChromeState.update { state ->
            if (state.studentBgmNowPlayingExpanded == expanded) {
                state
            } else {
                state.copy(studentBgmNowPlayingExpanded = expanded)
            }
        }
    }

    fun updateCatalogStudentBgmSliderInteractionActive(active: Boolean) {
        _pageChromeState.update { state ->
            if (state.studentBgmSliderInteractionActive == active) {
                state
            } else {
                state.copy(studentBgmSliderInteractionActive = active)
            }
        }
    }

    fun armPendingSafJsonExportRequest(request: BaGuideCatalogJsonExportRequest) {
        pendingSafJsonExportRequest = request
    }

    fun consumePendingSafJsonExportRequest(): BaGuideCatalogJsonExportRequest? =
        pendingSafJsonExportRequest.also {
            pendingSafJsonExportRequest = null
        }

    fun armPendingFixedJsonExportRequest(request: BaGuideCatalogJsonExportRequest) {
        pendingFixedJsonExportRequest = request
    }

    fun consumePendingFixedJsonExportRequest(): BaGuideCatalogJsonExportRequest? =
        pendingFixedJsonExportRequest.also {
            pendingFixedJsonExportRequest = null
        }

    fun clearPendingFixedJsonExportRequest() {
        pendingFixedJsonExportRequest = null
    }

    fun updateCatalogFilterSortState(snapshot: BaGuideCatalogFilterSortSnapshot) {
        val normalized =
            snapshot.copy(
                searchQuery = snapshot.searchQuery.trim(),
            )
        _filterSortState.update { state ->
            if (state == normalized) {
                state
            } else {
                normalized
            }
        }
    }

    fun requestCatalogImages(imageUrls: List<String>) {
        imageController.requestImages(imageUrls)
    }

    fun ensureCatalogFavoritesLoaded() {
        if (_catalogFavoriteEntries.value.isNotEmpty()) return
        viewModelScope.launch {
            _catalogFavoriteEntries.value = repository.loadCatalogFavorites()
        }
    }

    fun toggleCatalogFavorite(contentId: Long) {
        if (contentId <= 0L) return
        viewModelScope.launch {
            _catalogFavoriteEntries.value = repository.toggleCatalogFavorite(contentId)
        }
    }

    suspend fun buildCatalogFavoritesExportJson(): String = repository.buildStudentFavoritesExportJson(_catalogFavoriteEntries.value)

    suspend fun buildCatalogAllFavoritesExportJson(): String = repository.buildAllFavoritesExportJson(_catalogFavoriteEntries.value)

    suspend fun buildBgmFavoritesExportJson(): String = repository.buildBgmFavoritesExportJson()

    fun requestCatalogImportPreview(
        uri: Uri?,
        kind: BaGuideCatalogImportKind,
    ) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val preview =
                    repository.buildImportPreview(
                        context = appContext,
                        uri = uri,
                        kind = kind,
                        currentFavorites = _catalogFavoriteEntries.value,
                    )
                if (preview.hasImportableData) {
                    _events.emit(BaGuideCatalogEvent.CatalogImportPreviewReady(preview))
                } else {
                    _events.emit(BaGuideCatalogEvent.CatalogImportFailed(kind))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _events.emit(BaGuideCatalogEvent.CatalogImportFailed(kind))
            }
        }
    }

    fun confirmCatalogFavoritesImport(preview: BaGuideCatalogImportPreviewState) {
        viewModelScope.launch {
            try {
                val importResult = repository.applyFavoritesImport(preview)
                val studentFavorites = importResult.studentFavorites
                if (studentFavorites.isNotEmpty()) {
                    _catalogFavoriteEntries.value =
                        repository.replaceCatalogFavorites(
                            _catalogFavoriteEntries.value + studentFavorites,
                        )
                }
                _events.emit(
                    BaGuideCatalogEvent.CatalogImportApplied(
                        kind = preview.kind,
                        studentCount = studentFavorites.size,
                        bgmAddedCount = importResult.bgmResult?.addedCount ?: 0,
                        bgmUpdatedCount = importResult.bgmResult?.updatedCount ?: 0,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _events.emit(BaGuideCatalogEvent.CatalogImportFailed(preview.kind))
            }
        }
    }

    suspend fun toggleBgmFavorite(item: GuideBgmFavoriteItem): Boolean = repository.toggleBgmFavorite(item)

    suspend fun removeBgmFavorite(audioUrl: String) {
        repository.removeBgmFavorite(audioUrl)
    }

    fun requestToggleBgmFavorite(item: GuideBgmFavoriteItem) {
        viewModelScope.launch {
            val added = repository.toggleBgmFavorite(item)
            _events.emit(
                if (added) {
                    BaGuideCatalogEvent.BgmFavoriteAdded
                } else {
                    BaGuideCatalogEvent.BgmFavoriteRemoved
                },
            )
        }
    }

    fun requestRemoveBgmFavorite(
        audioUrl: String,
        showToast: Boolean = false,
    ) {
        val normalizedAudioUrl = audioUrl.trim()
        if (normalizedAudioUrl.isBlank()) return
        viewModelScope.launch {
            repository.removeBgmFavorite(normalizedAudioUrl)
            bgmCacheController.refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = null,
            )
            if (showToast) {
                _events.emit(BaGuideCatalogEvent.BgmFavoriteRemoved)
            }
        }
    }

    fun requestGuideDetailTab(
        sourceUrl: String,
        tab: GuideBottomTab,
    ) {
        repository.requestGuideDetailTab(sourceUrl, tab)
    }

    fun setNativeBgmMediaNotificationEnabled(enabled: Boolean) {
        if (_nativeBgmMediaNotificationEnabled.value == enabled) return
        _nativeBgmMediaNotificationEnabled.value = enabled
        viewModelScope.launch {
            repository.saveNativeBgmMediaNotificationEnabled(enabled)
        }
    }

    fun setTransferMediaSaveCustomEnabled(enabled: Boolean) {
        if (_transferSettings.value.mediaSaveCustomEnabled == enabled) return
        _transferSettings.update { state -> state.copy(mediaSaveCustomEnabled = enabled) }
        viewModelScope.launch {
            BaGuideCatalogTransferSettingsRepository.saveMediaSaveCustomEnabled(enabled)
        }
    }

    fun setTransferMediaSaveFixedTreeUri(uri: String) {
        if (_transferSettings.value.mediaSaveFixedTreeUri == uri) return
        _transferSettings.update { state -> state.copy(mediaSaveFixedTreeUri = uri) }
        viewModelScope.launch {
            BaGuideCatalogTransferSettingsRepository.saveMediaSaveFixedTreeUri(uri)
        }
    }

    fun clearTransferMediaSaveFixedTreeUri() {
        if (_transferSettings.value.mediaSaveFixedTreeUri.isEmpty()) return
        _transferSettings.update { state -> state.copy(mediaSaveFixedTreeUri = "") }
        viewModelScope.launch {
            BaGuideCatalogTransferSettingsRepository.clearMediaSaveFixedTreeUri()
        }
    }

    fun requestCatalogListDerivedState(input: BaGuideCatalogListInput) {
        listDerivationController.requestCatalogListState(input)
    }

    fun requestStudentBgmListDerivedState(input: BaGuideStudentBgmListInput) {
        listDerivationController.requestStudentBgmListState(input)
    }

    fun requestFavoriteBgmListDerivedState(input: BaGuideFavoriteBgmListInput) {
        listDerivationController.requestFavoriteBgmListState(input)
    }

    fun requestStudentBgmDisplayedDerivedState(input: BaGuideStudentBgmDisplayedInput) {
        listDerivationController.requestStudentBgmDisplayedState(input)
    }

    fun requestBgmCacheSnapshot(
        favorites: List<GuideBgmFavoriteItem>,
        force: Boolean = false,
    ) = bgmCacheController.requestBgmCacheSnapshot(
        favorites = favorites,
        force = force,
    )

    fun cacheMissingBgms(favorites: List<GuideBgmFavoriteItem>) {
        bgmCacheController.cacheMissingBgms(favorites)
    }

    fun cleanInvalidBgmCache(favorites: List<GuideBgmFavoriteItem>) {
        bgmCacheController.cleanInvalidBgmCache(favorites)
    }

    fun requestFavoriteBgmOfflineCache(
        favorites: List<GuideBgmFavoriteItem>,
        isPageActive: Boolean,
        force: Boolean = false,
    ) = bgmCacheController.requestFavoriteBgmOfflineCache(
        favorites = favorites,
        isPageActive = isPageActive,
        force = force,
    )

    fun toggleFavoriteBgmOfflineCache(
        favorite: GuideBgmFavoriteItem,
        displayedFavorites: List<GuideBgmFavoriteItem>,
    ) = bgmCacheController.toggleFavoriteBgmOfflineCache(
        favorite = favorite,
        displayedFavorites = displayedFavorites,
    )

    private fun loadCatalog(
        manualRefresh: Boolean,
        allowInitialDelay: Boolean,
    ) {
        val currentBinding = binding ?: return
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                if (
                    allowInitialDelay &&
                    currentBinding.transitionAnimationsEnabled &&
                    currentBinding.initialFetchDelayMs > 0
                ) {
                    delay(currentBinding.initialFetchDelayMs.milliseconds)
                }
                _dataState.update { state -> state.copy(loading = true) }
                val result =
                    repository.loadCatalog(
                        context = appContext,
                        currentCatalog = _dataState.value.catalog,
                        manualRefresh = manualRefresh,
                        loadFailedText = currentBinding.loadFailedText,
                        refreshFailedKeepCacheText = currentBinding.refreshFailedKeepCacheText,
                    )
                _dataState.value =
                    BaGuideCatalogDataUiState(
                        catalog = result.catalog,
                        loading = false,
                        error = result.error,
                    )
                hydrateReleaseDatesIfNeeded(result.catalog)
            }
    }

    private fun hydrateReleaseDatesIfNeeded(catalog: BaGuideCatalogBundle) {
        if (catalog.entriesByTab.values.all { it.isEmpty() }) return
        if (catalog.syncedAtMs <= 0L || catalog.syncedAtMs == hydratedSyncedAtMs) return
        hydratedSyncedAtMs = catalog.syncedAtMs
        hydrateJob?.cancel()
        hydrateJob =
            viewModelScope.launch {
                repository.hydrateReleaseDateIndex(
                    source = catalog,
                    onBundleUpdated = { updated ->
                        _dataState.update { state ->
                            if (state.catalog.syncedAtMs == catalog.syncedAtMs) {
                                state.copy(catalog = updated)
                            } else {
                                state
                            }
                        }
                    },
                )
            }
    }

    override fun onCleared() {
        loadJob?.cancel()
        hydrateJob?.cancel()
        listDerivationController.cancel()
        bgmCacheController.cancel()
        imageController.clearLoadingState()
        super.onCleared()
    }
}
