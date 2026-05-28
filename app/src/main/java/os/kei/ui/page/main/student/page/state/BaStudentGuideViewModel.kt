@file:Suppress("ktlint:standard:backing-property-naming")

package os.kei.ui.page.main.student.page.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.mapLatest
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
    private var pendingCustomMediaSaveRequest: GuideMediaSaveRequest? = null
    private var pendingFixedMediaSaveRequest: GuideMediaSaveRequest? = null
    private var pendingCustomMediaPackSaveRequest: GuideMediaPackSaveRequest? = null
    private var pendingFixedMediaPackSaveRequest: GuideMediaPackSaveRequest? = null
    private var lastLoadedSourceUrl: String = ""
    private val mediaSaveCoordinator =
        BaStudentGuideMediaSaveCoordinator(
            appContext = appContext,
            scope = viewModelScope,
        )
    private val chromeController = BaStudentGuideChromeController()
    private val prefetchController =
        BaStudentGuidePrefetchController(
            scope = viewModelScope,
            appContext = appContext,
            repository = repository,
        )

    private val _dataState = MutableStateFlow(BaStudentGuideDataUiState())
    val dataState: StateFlow<BaStudentGuideDataUiState> = _dataState.asStateFlow()

    val prefetchState: StateFlow<BaStudentGuidePrefetchUiState> = prefetchController.state
    val pageChromeState: StateFlow<BaStudentGuidePageChromeState> = chromeController.pageChromeState
    val voiceUiState: StateFlow<BaStudentGuideVoiceUiState> = chromeController.voiceUiState
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
                    prefetchState = prefetchState.value,
                    bgmFavoriteAudioUrls = bgmFavoriteAudioUrls.value,
                    isNpcSatelliteGuide = auxState.value.isNpcSatelliteGuide,
                    mediaSettings = auxState.value.mediaSettings,
                    requestedInitialBottomTab = auxState.value.requestedInitialBottomTab,
                ),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val contentPresentationState: StateFlow<BaStudentGuideContentPresentationState> =
        combine(
            dataState.map { state -> state.info }.distinctUntilChanged(),
            _isNpcSatelliteGuide,
        ) { info, isNpcSatelliteGuide ->
            info to isNpcSatelliteGuide
        }.mapLatest { (info, isNpcSatelliteGuide) ->
            deriveBaStudentGuideContentPresentationState(
                info = info,
                isNpcSatelliteGuide = isNpcSatelliteGuide,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BaStudentGuideContentPresentationState(),
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

    suspend fun reloadIfStoredUrlChanged() {
        val storedSourceUrl = repository.loadCurrentUrlAsync()
        if (storedSourceUrl.isBlank()) return
        if (storedSourceUrl == _dataState.value.sourceUrl) return
        openGuide(storedSourceUrl)
    }

    fun requestInitialBottomTabHandled() {
        _requestedInitialBottomTab.value = null
    }

    fun coerceSelectedBottomTab(bottomTabs: List<GuideBottomTab>) {
        chromeController.coerceSelectedBottomTab(bottomTabs)
    }

    fun updateSelectedBottomTab(tab: GuideBottomTab) {
        chromeController.updateSelectedBottomTab(tab)
    }

    fun updateSelectedVoiceLanguage(language: String) {
        chromeController.updateSelectedVoiceLanguage(language)
    }

    fun updatePlayingVoiceUrl(url: String) {
        chromeController.updatePlayingVoiceUrl(url)
    }

    fun updateIsVoicePlaying(isPlaying: Boolean) {
        chromeController.updateIsVoicePlaying(isPlaying)
    }

    fun updateVoicePlayProgress(progress: Float) {
        chromeController.updateVoicePlayProgress(progress)
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

    fun armPendingCustomMediaSaveRequest(request: GuideMediaSaveRequest) {
        pendingCustomMediaSaveRequest = request
    }

    fun consumePendingCustomMediaSaveRequest(): GuideMediaSaveRequest? =
        pendingCustomMediaSaveRequest.also {
            pendingCustomMediaSaveRequest = null
        }

    fun armPendingFixedMediaSaveRequest(request: GuideMediaSaveRequest) {
        pendingFixedMediaSaveRequest = request
    }

    fun consumePendingFixedMediaSaveRequest(): GuideMediaSaveRequest? =
        pendingFixedMediaSaveRequest.also {
            pendingFixedMediaSaveRequest = null
        }

    fun armPendingCustomMediaPackSaveRequest(request: GuideMediaPackSaveRequest) {
        pendingCustomMediaPackSaveRequest = request
    }

    fun consumePendingCustomMediaPackSaveRequest(): GuideMediaPackSaveRequest? =
        pendingCustomMediaPackSaveRequest.also {
            pendingCustomMediaPackSaveRequest = null
        }

    fun armPendingFixedMediaPackSaveRequest(request: GuideMediaPackSaveRequest) {
        pendingFixedMediaPackSaveRequest = request
    }

    fun consumePendingFixedMediaPackSaveRequest(): GuideMediaPackSaveRequest? =
        pendingFixedMediaPackSaveRequest.also {
            pendingFixedMediaPackSaveRequest = null
        }

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
    ) = prefetchController.syncStaticImagePrefetch(
        sourceUrl = _dataState.value.sourceUrl,
        info = info,
        prefetchBottomTab = prefetchBottomTab,
        initialPrefetchCount = initialPrefetchCount,
        galleryExtraPrefetchCount = galleryExtraPrefetchCount,
    )

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
        chromeController.resetForNewSource()
        prefetchController.resetForSource(
            sourceUrl = _dataState.value.sourceUrl,
            guideSyncToken = _dataState.value.info?.syncedAtMs ?: -1L,
        )
    }

    override fun onCleared() {
        loadJob?.cancel()
        currentUrlLoadJob?.cancel()
        currentUrlSaveJob?.cancel()
        npcSatelliteResolveJob?.cancel()
        prefetchController.cancel()
        mediaImageLoader.clearLoadingState()
        profileLinkTitleLoader.clearLoadingState()
        super.onCleared()
    }
}

private fun List<GuideBgmFavoriteItem>.toAudioUrlSet(): Set<String> = mapTo(hashSetOf()) { it.audioUrl }
