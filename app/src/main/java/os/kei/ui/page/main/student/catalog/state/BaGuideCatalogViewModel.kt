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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportKind
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportPreviewState
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
    private val imageRepository = BaGuideCatalogImageRepository()
    private var binding: BaGuideCatalogBinding? = null
    private var loadJob: Job? = null
    private var hydrateJob: Job? = null
    private var hydratedSyncedAtMs: Long = -1L
    private val listDerivationJobs = mutableMapOf<BaGuideCatalogTab, Job>()
    private val listDerivationInputs = mutableMapOf<BaGuideCatalogTab, BaGuideCatalogListInput>()
    private var studentBgmListDerivationJob: Job? = null
    private var studentBgmListDerivationInput: BaGuideStudentBgmListInput? = null
    private var favoriteBgmListDerivationJob: Job? = null
    private var favoriteBgmListDerivationInput: BaGuideFavoriteBgmListInput? = null
    private var studentBgmDisplayedDerivationJob: Job? = null
    private var studentBgmDisplayedDerivationInput: BaGuideStudentBgmDisplayedInput? = null
    private var bgmCacheSnapshotJob: Job? = null
    private var bgmCacheSnapshotInput: List<GuideBgmFavoriteItem>? = null
    private var favoriteBgmOfflineCacheJob: Job? = null
    private var favoriteBgmOfflineCacheInput: List<GuideBgmFavoriteItem>? = null
    private var imageLoadingUrls: Set<String> = emptySet()

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

    private val _catalogListDerivedStates =
        MutableStateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>>(emptyMap())
    val catalogListDerivedStates: StateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>> =
        _catalogListDerivedStates.asStateFlow()

    private val _studentBgmListDerivedState =
        MutableStateFlow(BaGuideStudentBgmListDerivedState.Empty)
    val studentBgmListDerivedState: StateFlow<BaGuideStudentBgmListDerivedState> =
        _studentBgmListDerivedState.asStateFlow()

    private val _favoriteBgmListDerivedState =
        MutableStateFlow(BaGuideFavoriteBgmListDerivedState.Empty)
    val favoriteBgmListDerivedState: StateFlow<BaGuideFavoriteBgmListDerivedState> =
        _favoriteBgmListDerivedState.asStateFlow()

    private val _studentBgmDisplayedDerivedState =
        MutableStateFlow(BaGuideStudentBgmDisplayedDerivedState.Empty)
    val studentBgmDisplayedDerivedState: StateFlow<BaGuideStudentBgmDisplayedDerivedState> =
        _studentBgmDisplayedDerivedState.asStateFlow()

    private val _bgmCacheSnapshot = MutableStateFlow(BaGuideFavoriteBgmCacheSnapshot())
    val bgmCacheSnapshot: StateFlow<BaGuideFavoriteBgmCacheSnapshot> =
        _bgmCacheSnapshot.asStateFlow()

    private val _favoriteBgmOfflineCacheState =
        MutableStateFlow(BaGuideFavoriteBgmOfflineCacheUiState())
    val favoriteBgmOfflineCacheState: StateFlow<BaGuideFavoriteBgmOfflineCacheUiState> =
        _favoriteBgmOfflineCacheState.asStateFlow()
    private val _imageState = MutableStateFlow(BaGuideCatalogImageUiState())
    val imageState: StateFlow<BaGuideCatalogImageUiState> =
        _imageState.asStateFlow()

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

    fun requestCatalogImages(imageUrls: List<String>) {
        val normalizedUrls =
            imageUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (normalizedUrls.isEmpty()) return

        val currentState = _imageState.value
        val missingUrls =
            normalizedUrls.filter { imageUrl ->
                !currentState.bitmaps.containsKey(imageUrl) &&
                    !currentState.missingUrls.contains(imageUrl) &&
                    !imageLoadingUrls.contains(imageUrl) &&
                    imageRepository.cachedBitmap(imageUrl) == null
            }
        val cachedBitmaps =
            normalizedUrls
                .filterNot { currentState.bitmaps.containsKey(it) }
                .mapNotNull { imageUrl ->
                    imageRepository.cachedBitmap(imageUrl)?.let { bitmap -> imageUrl to bitmap }
                }.toMap()
        if (cachedBitmaps.isNotEmpty()) {
            _imageState.update { state ->
                state.copy(bitmaps = state.bitmaps + cachedBitmaps)
            }
        }
        if (missingUrls.isEmpty()) return

        imageLoadingUrls += missingUrls
        viewModelScope.launch {
            try {
                val result =
                    imageRepository.loadImages(
                        context = appContext,
                        imageUrls = missingUrls,
                    )
                _imageState.update { state ->
                    state.copy(
                        bitmaps = state.bitmaps + result.bitmaps,
                        missingUrls = state.missingUrls + result.missingUrls,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _imageState.update { state ->
                    state.copy(missingUrls = state.missingUrls + missingUrls)
                }
            } finally {
                imageLoadingUrls -= missingUrls
            }
        }
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
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = favoriteBgmOfflineCacheInput,
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
        val previousInput = listDerivationInputs[input.tab]
        val hasDerivedState = _catalogListDerivedStates.value.containsKey(input.tab)
        if (previousInput == input && hasDerivedState) return

        listDerivationInputs[input.tab] = input
        listDerivationJobs[input.tab]?.cancel()
        _catalogListDerivedStates.update { states ->
            val current = states[input.tab] ?: BaGuideCatalogListDerivedState.Empty
            states + (input.tab to current.copy(deriving = true))
        }
        listDerivationJobs[input.tab] =
            viewModelScope.launch {
                val derivedState = repository.deriveCatalogListState(input)
                if (listDerivationInputs[input.tab] != input) return@launch
                _catalogListDerivedStates.update { states ->
                    states + (input.tab to derivedState)
                }
            }
    }

    fun requestStudentBgmListDerivedState(input: BaGuideStudentBgmListInput) {
        val previousInput = studentBgmListDerivationInput
        if (previousInput == input && _studentBgmListDerivedState.value !== BaGuideStudentBgmListDerivedState.Empty) return

        studentBgmListDerivationInput = input
        studentBgmListDerivationJob?.cancel()
        _studentBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        studentBgmListDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveStudentBgmListState(input)
                if (studentBgmListDerivationInput != input) return@launch
                _studentBgmListDerivedState.value = derivedState
            }
    }

    fun requestFavoriteBgmListDerivedState(input: BaGuideFavoriteBgmListInput) {
        val previousInput = favoriteBgmListDerivationInput
        if (previousInput == input && _favoriteBgmListDerivedState.value !== BaGuideFavoriteBgmListDerivedState.Empty) return

        favoriteBgmListDerivationInput = input
        favoriteBgmListDerivationJob?.cancel()
        _favoriteBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        favoriteBgmListDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveFavoriteBgmListState(input)
                if (favoriteBgmListDerivationInput != input) return@launch
                _favoriteBgmListDerivedState.value = derivedState
            }
    }

    fun requestStudentBgmDisplayedDerivedState(input: BaGuideStudentBgmDisplayedInput) {
        val previousInput = studentBgmDisplayedDerivationInput
        if (previousInput == input && _studentBgmDisplayedDerivedState.value.input == input) return

        studentBgmDisplayedDerivationInput = input
        studentBgmDisplayedDerivationJob?.cancel()
        _studentBgmDisplayedDerivedState.update { state ->
            state.copy(
                input = input,
                deriving = true,
            )
        }
        studentBgmDisplayedDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveStudentBgmDisplayedState(input)
                if (studentBgmDisplayedDerivationInput != input) return@launch
                _studentBgmDisplayedDerivedState.value = derivedState
            }
    }

    fun requestBgmCacheSnapshot(
        favorites: List<GuideBgmFavoriteItem>,
        force: Boolean = false,
    ) {
        if (!force && bgmCacheSnapshotInput == favorites) return
        bgmCacheSnapshotInput = favorites
        bgmCacheSnapshotJob?.cancel()
        bgmCacheSnapshotJob =
            viewModelScope.launch {
                _bgmCacheSnapshot.value =
                    BaGuideFavoriteBgmCacheRepository.loadCacheSnapshot(
                        context = appContext,
                        favorites = favorites,
                    )
            }
    }

    fun cacheMissingBgms(favorites: List<GuideBgmFavoriteItem>) {
        viewModelScope.launch {
            val targetCount =
                BaGuideFavoriteBgmCacheRepository.cacheMissingFavorites(
                    context = appContext,
                    favorites = favorites,
                )
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = favoriteBgmOfflineCacheInput,
            )
            _events.emit(BaGuideCatalogEvent.BgmCacheBatchDone(targetCount))
        }
    }

    fun cleanInvalidBgmCache(favorites: List<GuideBgmFavoriteItem>) {
        viewModelScope.launch {
            val cleaned =
                BaGuideFavoriteBgmCacheRepository.cleanInvalidFavorites(
                    context = appContext,
                    favorites = favorites,
                )
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = favoriteBgmOfflineCacheInput,
            )
            _events.emit(BaGuideCatalogEvent.BgmCacheCleaned(cleaned))
        }
    }

    fun requestFavoriteBgmOfflineCache(
        favorites: List<GuideBgmFavoriteItem>,
        isPageActive: Boolean,
        force: Boolean = false,
    ) {
        if (!isPageActive) return
        if (!force && favoriteBgmOfflineCacheInput == favorites) return
        favoriteBgmOfflineCacheInput = favorites
        favoriteBgmOfflineCacheJob?.cancel()
        favoriteBgmOfflineCacheJob =
            viewModelScope.launch {
                val offlineAudioUrls =
                    BaGuideFavoriteBgmCacheRepository.loadCachedAudioUrls(
                        context = appContext,
                        favorites = favorites,
                    )
                _favoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = offlineAudioUrls)
                }
            }
    }

    fun toggleFavoriteBgmOfflineCache(
        favorite: GuideBgmFavoriteItem,
        displayedFavorites: List<GuideBgmFavoriteItem>,
    ) {
        val audioUrl = favorite.audioUrl
        if (audioUrl.isBlank()) return
        val current = _favoriteBgmOfflineCacheState.value
        if (audioUrl in current.offlineAudioUrls) {
            viewModelScope.launch {
                BaGuideFavoriteBgmCacheRepository.clearFavorite(appContext, favorite)
                _favoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = state.offlineAudioUrls - audioUrl)
                }
                refreshBgmCacheStates(
                    allFavorites = favoriteBgms.value,
                    displayedFavorites = displayedFavorites,
                )
                _events.emit(BaGuideCatalogEvent.FavoriteBgmCacheRemoved)
            }
            return
        }
        if (audioUrl in current.cachingAudioUrls) return
        _favoriteBgmOfflineCacheState.update { state ->
            state.copy(cachingAudioUrls = state.cachingAudioUrls + audioUrl)
        }
        viewModelScope.launch {
            val success =
                try {
                    BaGuideFavoriteBgmCacheRepository.cacheFavorite(appContext, favorite)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                } finally {
                    _favoriteBgmOfflineCacheState.update { state ->
                        state.copy(cachingAudioUrls = state.cachingAudioUrls - audioUrl)
                    }
                }
            if (success) {
                _favoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = state.offlineAudioUrls + audioUrl)
                }
            }
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = displayedFavorites,
            )
            _events.emit(
                if (success) {
                    BaGuideCatalogEvent.FavoriteBgmCacheSuccess
                } else {
                    BaGuideCatalogEvent.FavoriteBgmCacheFailed
                },
            )
        }
    }

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
        listDerivationJobs.values.forEach { job -> job.cancel() }
        studentBgmListDerivationJob?.cancel()
        favoriteBgmListDerivationJob?.cancel()
        studentBgmDisplayedDerivationJob?.cancel()
        bgmCacheSnapshotJob?.cancel()
        favoriteBgmOfflineCacheJob?.cancel()
        imageLoadingUrls = emptySet()
        super.onCleared()
    }

    private suspend fun refreshBgmCacheStates(
        allFavorites: List<GuideBgmFavoriteItem>,
        displayedFavorites: List<GuideBgmFavoriteItem>?,
    ) {
        bgmCacheSnapshotInput = allFavorites
        _bgmCacheSnapshot.value =
            BaGuideFavoriteBgmCacheRepository.loadCacheSnapshot(
                context = appContext,
                favorites = allFavorites,
            )
        displayedFavorites?.let { favorites ->
            favoriteBgmOfflineCacheInput = favorites
            val offlineAudioUrls =
                BaGuideFavoriteBgmCacheRepository.loadCachedAudioUrls(
                    context = appContext,
                    favorites = favorites,
                )
            _favoriteBgmOfflineCacheState.update { state ->
                state.copy(offlineAudioUrls = offlineAudioUrls)
            }
        }
    }
}
