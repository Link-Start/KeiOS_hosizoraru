@file:Suppress("ktlint:standard:backing-property-naming")

package os.kei.ui.page.main.student.page.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.GuideMediaImageLoader
import os.kei.ui.page.main.student.GuideMediaImageRequest
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileLinkTitleLoader
import kotlin.time.Duration.Companion.milliseconds

internal data class BaStudentGuideDataUiState(
    val sourceUrl: String = "",
    val info: BaStudentGuideInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal data class BaStudentGuidePrefetchUiState(
    val sourceUrl: String = "",
    val guideSyncToken: Long = -1L,
    val galleryPrefetchRequested: Boolean = false,
    val staticImagePrefetchStage: Int = 0,
    val galleryCacheRevision: Int = 0,
)

internal data class BaStudentGuideUiState(
    val dataState: BaStudentGuideDataUiState = BaStudentGuideDataUiState(),
    val prefetchState: BaStudentGuidePrefetchUiState = BaStudentGuidePrefetchUiState(),
    val bgmFavoriteAudioUrls: Set<String> = emptySet(),
    val isNpcSatelliteGuide: Boolean = false,
    val mediaSettings: BaStudentGuideMediaSettings = BaStudentGuideMediaSettings(),
    val requestedInitialBottomTab: GuideBottomTab? = null,
)

private data class BaStudentGuideBinding(
    val transitionAnimationsEnabled: Boolean,
    val initialFetchDelayMs: Int,
    val loadFailedText: String,
    val refreshFailedKeepCacheText: String,
)

private data class BaStudentGuideAuxUiState(
    val isNpcSatelliteGuide: Boolean = false,
    val mediaSettings: BaStudentGuideMediaSettings = BaStudentGuideMediaSettings(),
    val requestedInitialBottomTab: GuideBottomTab? = null,
)

internal class BaStudentGuideViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = BaStudentGuideRepository()
    private val mediaImageLoader =
        GuideMediaImageLoader(
            appContext = appContext,
            scope = viewModelScope,
        )
    private val profileLinkTitleLoader = GuideProfileLinkTitleLoader(viewModelScope)
    private var binding: BaStudentGuideBinding? = null
    private var loadJob: Job? = null
    private var currentUrlLoadJob: Job? = null
    private var currentUrlSaveJob: Job? = null
    private var npcSatelliteResolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastLoadedSourceUrl: String = ""
    private val mediaSaveCoordinator =
        BaStudentGuideMediaSaveCoordinator(
            appContext = appContext,
            scope = viewModelScope,
        )

    private val _dataState = MutableStateFlow(BaStudentGuideDataUiState())
    val dataState: StateFlow<BaStudentGuideDataUiState> = _dataState.asStateFlow()

    private val _prefetchState = MutableStateFlow(BaStudentGuidePrefetchUiState())
    val prefetchState: StateFlow<BaStudentGuidePrefetchUiState> = _prefetchState.asStateFlow()
    private val _pageChromeState = MutableStateFlow(BaStudentGuidePageChromeState())
    val pageChromeState: StateFlow<BaStudentGuidePageChromeState> = _pageChromeState.asStateFlow()
    private val _voiceUiState = MutableStateFlow(BaStudentGuideVoiceUiState())
    val voiceUiState: StateFlow<BaStudentGuideVoiceUiState> = _voiceUiState.asStateFlow()
    val mediaImageState = mediaImageLoader.state
    val profileLinkTitleState = profileLinkTitleLoader.state
    private val mutableEvents = MutableSharedFlow<BaStudentGuideEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<BaStudentGuideEvent> = mutableEvents.asSharedFlow()
    val bgmFavoriteAudioUrls: StateFlow<Set<String>> =
        repository
            .bgmFavoritesFlow()
            .map { favorites -> favorites.toAudioUrlSet() }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )
    private val _isNpcSatelliteGuide = MutableStateFlow(false)
    private val _mediaSettings = MutableStateFlow(BaStudentGuideMediaSettings())
    private val _requestedInitialBottomTab = MutableStateFlow<GuideBottomTab?>(null)
    private val auxState: StateFlow<BaStudentGuideAuxUiState> =
        combine(
            _isNpcSatelliteGuide,
            _mediaSettings,
            _requestedInitialBottomTab,
        ) { isNpcSatelliteGuide, mediaSettings, requestedInitialBottomTab ->
            BaStudentGuideAuxUiState(
                isNpcSatelliteGuide = isNpcSatelliteGuide,
                mediaSettings = mediaSettings,
                requestedInitialBottomTab = requestedInitialBottomTab,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                BaStudentGuideAuxUiState(
                    isNpcSatelliteGuide = _isNpcSatelliteGuide.value,
                    mediaSettings = _mediaSettings.value,
                    requestedInitialBottomTab = _requestedInitialBottomTab.value,
                ),
        )
    val uiState: StateFlow<BaStudentGuideUiState> =
        combine(
            dataState,
            prefetchState,
            bgmFavoriteAudioUrls,
            auxState,
        ) { dataState, prefetchState, bgmFavoriteAudioUrls, auxState ->
            BaStudentGuideUiState(
                dataState = dataState,
                prefetchState = prefetchState,
                bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                isNpcSatelliteGuide = auxState.isNpcSatelliteGuide,
                mediaSettings = auxState.mediaSettings,
                requestedInitialBottomTab = auxState.requestedInitialBottomTab,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                BaStudentGuideUiState(
                    dataState = _dataState.value,
                    prefetchState = _prefetchState.value,
                    bgmFavoriteAudioUrls = bgmFavoriteAudioUrls.value,
                    isNpcSatelliteGuide = auxState.value.isNpcSatelliteGuide,
                    mediaSettings = auxState.value.mediaSettings,
                    requestedInitialBottomTab = auxState.value.requestedInitialBottomTab,
                ),
        )

    init {
        viewModelScope.launch {
            _mediaSettings.value = repository.loadMediaSettings()
        }
        viewModelScope.launch {
            repository.hydrateBgmFavorites()
        }
        viewModelScope.launch {
            mediaSaveCoordinator.events.collect { event ->
                mutableEvents.emit(event)
            }
        }
        loadStoredCurrentGuide()
    }

    fun bind(
        transitionAnimationsEnabled: Boolean,
        initialFetchDelayMs: Int,
        loadFailedText: String,
        refreshFailedKeepCacheText: String,
    ) {
        val next =
            BaStudentGuideBinding(
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                initialFetchDelayMs = initialFetchDelayMs,
                loadFailedText = loadFailedText,
                refreshFailedKeepCacheText = refreshFailedKeepCacheText,
            )
        val bindingChanged = binding != next
        binding = next
        val currentSourceUrl = _dataState.value.sourceUrl
        if (currentSourceUrl.isBlank()) {
            _dataState.update { it.copy(loading = false) }
            return
        }
        if (!bindingChanged && lastLoadedSourceUrl == currentSourceUrl && _dataState.value.info != null) return
        loadGuide(
            sourceUrl = currentSourceUrl,
            manualRefresh = false,
            allowInitialDelay = true,
        )
    }

    fun openGuide(rawSourceUrl: String) {
        val target = normalizeGuideUrl(rawSourceUrl)
        if (target.isBlank() || target == _dataState.value.sourceUrl) return
        currentUrlSaveJob?.cancel()
        currentUrlSaveJob =
            viewModelScope.launch {
                repository.saveCurrentUrlAsync(target)
            }
        lastLoadedSourceUrl = ""
        resetGuideRuntimeState()
        _isNpcSatelliteGuide.value = false
        _requestedInitialBottomTab.value = repository.consumeInitialBottomTab(target)
        _dataState.value =
            BaStudentGuideDataUiState(
                sourceUrl = target,
                loading = true,
            )
        loadGuide(
            sourceUrl = target,
            manualRefresh = false,
            allowInitialDelay = false,
        )
    }

    fun requestInitialBottomTabHandled() {
        _requestedInitialBottomTab.value = null
    }

    fun coerceSelectedBottomTab(bottomTabs: List<GuideBottomTab>) {
        val selectedOrdinal = _pageChromeState.value.selectedBottomTabOrdinal
        if (bottomTabs.any { it.ordinal == selectedOrdinal }) return
        _pageChromeState.update { state ->
            state.copy(
                selectedBottomTabOrdinal =
                    bottomTabs
                        .firstOrNull()
                        ?.ordinal
                        ?: GuideBottomTab.Archive.ordinal,
            )
        }
    }

    fun updateSelectedBottomTab(tab: GuideBottomTab) {
        _pageChromeState.update { state ->
            if (state.selectedBottomTabOrdinal == tab.ordinal) {
                state
            } else {
                state.copy(selectedBottomTabOrdinal = tab.ordinal)
            }
        }
    }

    fun updateSelectedVoiceLanguage(language: String) {
        _pageChromeState.update { state ->
            if (state.selectedVoiceLanguage == language) {
                state
            } else {
                state.copy(selectedVoiceLanguage = language)
            }
        }
    }

    fun updatePlayingVoiceUrl(url: String) {
        _voiceUiState.update { state ->
            if (state.playingVoiceUrl == url) {
                state
            } else {
                state.copy(playingVoiceUrl = url)
            }
        }
    }

    fun updateIsVoicePlaying(isPlaying: Boolean) {
        _voiceUiState.update { state ->
            if (state.isVoicePlaying == isPlaying) {
                state
            } else {
                state.copy(isVoicePlaying = isPlaying)
            }
        }
    }

    fun updateVoicePlayProgress(progress: Float) {
        _voiceUiState.update { state ->
            state.withProgress(progress)
        }
    }

    private fun loadStoredCurrentGuide() {
        currentUrlLoadJob?.cancel()
        currentUrlLoadJob =
            viewModelScope.launch {
                val storedSourceUrl = repository.loadCurrentUrlAsync()
                if (storedSourceUrl.isBlank()) {
                    _dataState.update { state ->
                        if (state.sourceUrl.isBlank()) state.copy(loading = false) else state
                    }
                    return@launch
                }
                if (_dataState.value.sourceUrl.isNotBlank()) return@launch
                _requestedInitialBottomTab.value = repository.consumeInitialBottomTab(storedSourceUrl)
                resetGuideRuntimeState()
                _dataState.value =
                    BaStudentGuideDataUiState(
                        sourceUrl = storedSourceUrl,
                        loading = true,
                    )
                _isNpcSatelliteGuide.value = false
                lastLoadedSourceUrl = ""
                resolveNpcSatelliteGuideFor(
                    sourceUrl = storedSourceUrl,
                    info = null,
                )
                if (binding != null) {
                    loadGuide(
                        sourceUrl = storedSourceUrl,
                        manualRefresh = false,
                        allowInitialDelay = true,
                    )
                }
            }
    }

    fun requestRefresh() {
        val currentSourceUrl = _dataState.value.sourceUrl
        if (currentSourceUrl.isBlank()) return
        loadGuide(
            sourceUrl = currentSourceUrl,
            manualRefresh = true,
            allowInitialDelay = false,
        )
    }

    fun requestToggleBgmFavorite(item: GuideBgmFavoriteItem) {
        viewModelScope.launch {
            val added = repository.toggleBgmFavorite(item)
            mutableEvents.emit(
                if (added) {
                    BaStudentGuideEvent.BgmFavoriteAdded
                } else {
                    BaStudentGuideEvent.BgmFavoriteRemoved
                },
            )
        }
    }

    fun requestMediaSave(
        rawMediaUrl: String,
        rawTitle: String,
        studentNamePrefix: String,
    ) = mediaSaveCoordinator.requestMediaSave(
        rawMediaUrl = rawMediaUrl,
        rawTitle = rawTitle,
        studentNamePrefix = studentNamePrefix,
    )

    fun requestMediaPackSave(
        rawItems: List<Pair<String, String>>,
        rawPackTitle: String,
        studentNamePrefix: String,
    ) = mediaSaveCoordinator.requestMediaPackSave(
        rawItems = rawItems,
        rawPackTitle = rawPackTitle,
        studentNamePrefix = studentNamePrefix,
    )

    fun requestGuideMediaImages(requests: List<GuideMediaImageRequest>) = mediaImageLoader.requestImages(requests)

    fun requestGuideMediaGifTargets(rawTargets: List<String>) = mediaImageLoader.requestGifTargets(rawTargets)

    fun requestProfileLinkTitles(rawLinks: List<String>) = profileLinkTitleLoader.requestTitles(rawLinks)

    fun completeCustomMediaSave(
        request: GuideMediaSaveRequest,
        targetUri: Uri,
    ) = mediaSaveCoordinator.completeCustomMediaSave(
        request = request,
        targetUri = targetUri,
    )

    fun completeFixedMediaSave(
        request: GuideMediaSaveRequest,
        treeUri: Uri,
    ) = mediaSaveCoordinator.completeFixedMediaSave(
        request = request,
        treeUri = treeUri,
    )

    fun completeCustomMediaPackSave(
        request: GuideMediaPackSaveRequest,
        targetUri: Uri,
    ) = mediaSaveCoordinator.completeCustomMediaPackSave(
        request = request,
        targetUri = targetUri,
    )

    fun completeFixedMediaPackSave(
        request: GuideMediaPackSaveRequest,
        treeUri: Uri,
    ) = mediaSaveCoordinator.completeFixedMediaPackSave(
        request = request,
        treeUri = treeUri,
    )

    fun syncStaticImagePrefetch(
        info: BaStudentGuideInfo?,
        prefetchBottomTab: GuideBottomTab,
        initialPrefetchCount: Int,
        galleryExtraPrefetchCount: Int,
    ) {
        val sourceUrl = _dataState.value.sourceUrl
        val guideSyncToken = info?.syncedAtMs ?: -1L
        val existing = _prefetchState.value
        if (existing.sourceUrl != sourceUrl || existing.guideSyncToken != guideSyncToken) {
            prefetchJob?.cancel()
            _prefetchState.value =
                BaStudentGuidePrefetchUiState(
                    sourceUrl = sourceUrl,
                    guideSyncToken = guideSyncToken,
                )
        }
        if (prefetchBottomTab == GuideBottomTab.Gallery) {
            _prefetchState.update { state ->
                state.copy(galleryPrefetchRequested = true)
            }
        }
        val targetStage = if (_prefetchState.value.galleryPrefetchRequested) 2 else 1
        val currentStage = _prefetchState.value.staticImagePrefetchStage
        if (info == null || sourceUrl.isBlank() || currentStage >= targetStage) return
        prefetchJob?.cancel()
        prefetchJob =
            viewModelScope.launch {
                runPrefetchStages(
                    info = info,
                    sourceUrl = sourceUrl,
                    targetStage = targetStage,
                    initialPrefetchCount = initialPrefetchCount,
                    galleryExtraPrefetchCount = galleryExtraPrefetchCount,
                )
            }
    }

    private fun loadGuide(
        sourceUrl: String,
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
                _dataState.update { state ->
                    if (state.sourceUrl == sourceUrl) {
                        state.copy(loading = true, error = null)
                    } else {
                        state
                    }
                }
                val result =
                    repository.loadGuide(
                        context = appContext,
                        sourceUrl = sourceUrl,
                        currentInfo = _dataState.value.info,
                        manualRefresh = manualRefresh,
                        loadFailedText = currentBinding.loadFailedText,
                        refreshFailedKeepCacheText = currentBinding.refreshFailedKeepCacheText,
                    )
                _dataState.update { state ->
                    if (state.sourceUrl == sourceUrl) {
                        lastLoadedSourceUrl = sourceUrl
                        val nextState =
                            state.copy(
                                info = result.info,
                                loading = false,
                                error = result.error,
                            )
                        resolveNpcSatelliteGuideFor(sourceUrl, result.info)
                        nextState
                    } else {
                        state
                    }
                }
            }
    }

    private fun resolveNpcSatelliteGuideForCurrent() {
        val state = _dataState.value
        resolveNpcSatelliteGuideFor(
            sourceUrl = state.sourceUrl,
            info = state.info,
        )
    }

    private fun resolveNpcSatelliteGuideFor(
        sourceUrl: String,
        info: BaStudentGuideInfo?,
    ) {
        if (sourceUrl.isBlank()) {
            npcSatelliteResolveJob?.cancel()
            _isNpcSatelliteGuide.value = false
            return
        }
        npcSatelliteResolveJob?.cancel()
        npcSatelliteResolveJob =
            viewModelScope.launch {
                val resolved =
                    repository.resolveNpcSatelliteGuide(
                        sourceUrl = sourceUrl,
                        info = info,
                    )
                _isNpcSatelliteGuide.update { current ->
                    if (_dataState.value.sourceUrl == sourceUrl) {
                        resolved
                    } else {
                        current
                    }
                }
            }
    }

    private fun resetGuideRuntimeState() {
        _pageChromeState.update { state -> state.resetForNewSource() }
        _voiceUiState.update { state -> state.resetForNewSource() }
    }

    private suspend fun runPrefetchStages(
        info: BaStudentGuideInfo,
        sourceUrl: String,
        targetStage: Int,
        initialPrefetchCount: Int,
        galleryExtraPrefetchCount: Int,
    ) {
        val guideSyncToken = info.syncedAtMs
        val safeInitialPrefetchCount = initialPrefetchCount.coerceAtLeast(0)
        val safeGalleryExtraPrefetchCount = galleryExtraPrefetchCount.coerceAtLeast(0)
        val requestedPrefetchCount =
            if (targetStage >= 2) {
                safeInitialPrefetchCount + safeGalleryExtraPrefetchCount
            } else {
                safeInitialPrefetchCount
            }
        val allUrls =
            repository.collectStaticImagePrefetchUrls(
                info = info,
                maxCount = requestedPrefetchCount,
            )

        fun updatePrefetchIfCurrent(transform: (BaStudentGuidePrefetchUiState) -> BaStudentGuidePrefetchUiState) {
            _prefetchState.update { state ->
                if (state.sourceUrl == sourceUrl && state.guideSyncToken == guideSyncToken) {
                    transform(state)
                } else {
                    state
                }
            }
        }
        if (_prefetchState.value.staticImagePrefetchStage < 1 && targetStage >= 1) {
            val urls = allUrls.take(safeInitialPrefetchCount)
            if (urls.isNotEmpty()) {
                repository.prefetchStaticImages(
                    context = appContext,
                    sourceUrl = sourceUrl,
                    rawUrls = urls,
                )
                updatePrefetchIfCurrent { state ->
                    state.copy(galleryCacheRevision = state.galleryCacheRevision + 1)
                }
            }
            updatePrefetchIfCurrent { state ->
                state.copy(staticImagePrefetchStage = 1)
            }
        }
        if (_prefetchState.value.staticImagePrefetchStage < 2 && targetStage >= 2) {
            val urls =
                allUrls
                    .drop(safeInitialPrefetchCount)
                    .take(safeGalleryExtraPrefetchCount)
            if (urls.isNotEmpty()) {
                repository.prefetchStaticImages(
                    context = appContext,
                    sourceUrl = sourceUrl,
                    rawUrls = urls,
                )
                updatePrefetchIfCurrent { state ->
                    state.copy(galleryCacheRevision = state.galleryCacheRevision + 1)
                }
            }
            updatePrefetchIfCurrent { state ->
                state.copy(staticImagePrefetchStage = 2)
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        currentUrlLoadJob?.cancel()
        currentUrlSaveJob?.cancel()
        npcSatelliteResolveJob?.cancel()
        prefetchJob?.cancel()
        mediaImageLoader.clearLoadingState()
        profileLinkTitleLoader.clearLoadingState()
        super.onCleared()
    }
}

private fun List<GuideBgmFavoriteItem>.toAudioUrlSet(): Set<String> = mapTo(hashSetOf()) { it.audioUrl }
