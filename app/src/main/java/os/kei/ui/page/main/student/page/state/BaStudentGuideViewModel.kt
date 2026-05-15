package os.kei.ui.page.main.student.page.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import kotlin.time.Duration.Companion.milliseconds

internal data class BaStudentGuideDataUiState(
    val sourceUrl: String = "",
    val info: BaStudentGuideInfo? = null,
    val loading: Boolean = false,
    val error: String? = null
)

internal data class BaStudentGuidePrefetchUiState(
    val sourceUrl: String = "",
    val guideSyncToken: Long = -1L,
    val galleryPrefetchRequested: Boolean = false,
    val staticImagePrefetchStage: Int = 0,
    val galleryCacheRevision: Int = 0
)

private data class BaStudentGuideBinding(
    val transitionAnimationsEnabled: Boolean,
    val initialFetchDelayMs: Int,
    val loadFailedText: String,
    val refreshFailedKeepCacheText: String
)

internal class BaStudentGuideViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = BaStudentGuideRepository()
    private var binding: BaStudentGuideBinding? = null
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var lastLoadedSourceUrl: String = ""

    private val initialSourceUrl = repository.loadCurrentUrl()
    private val _dataState = MutableStateFlow(
        BaStudentGuideDataUiState(
            sourceUrl = initialSourceUrl,
            loading = initialSourceUrl.isNotBlank()
        )
    )
    val dataState: StateFlow<BaStudentGuideDataUiState> = _dataState.asStateFlow()

    private val _prefetchState = MutableStateFlow(BaStudentGuidePrefetchUiState())
    val prefetchState: StateFlow<BaStudentGuidePrefetchUiState> = _prefetchState.asStateFlow()

    fun bind(
        transitionAnimationsEnabled: Boolean,
        initialFetchDelayMs: Int,
        loadFailedText: String,
        refreshFailedKeepCacheText: String
    ) {
        val next = BaStudentGuideBinding(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            initialFetchDelayMs = initialFetchDelayMs,
            loadFailedText = loadFailedText,
            refreshFailedKeepCacheText = refreshFailedKeepCacheText
        )
        val latestStored = repository.loadCurrentUrl()
        if (latestStored.isNotBlank() && latestStored != _dataState.value.sourceUrl) {
            _dataState.value = BaStudentGuideDataUiState(
                sourceUrl = latestStored,
                loading = true
            )
            lastLoadedSourceUrl = ""
        }
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
            allowInitialDelay = true
        )
    }

    fun openGuide(rawSourceUrl: String) {
        val target = normalizeGuideUrl(rawSourceUrl)
        if (target.isBlank() || target == _dataState.value.sourceUrl) return
        repository.saveCurrentUrl(target)
        lastLoadedSourceUrl = ""
        _dataState.value = BaStudentGuideDataUiState(
            sourceUrl = target,
            loading = true
        )
        loadGuide(
            sourceUrl = target,
            manualRefresh = false,
            allowInitialDelay = false
        )
    }

    fun requestRefresh() {
        val currentSourceUrl = _dataState.value.sourceUrl
        if (currentSourceUrl.isBlank()) return
        loadGuide(
            sourceUrl = currentSourceUrl,
            manualRefresh = true,
            allowInitialDelay = false
        )
    }

    fun syncStaticImagePrefetch(
        info: BaStudentGuideInfo?,
        activeBottomTab: GuideBottomTab,
        initialPrefetchCount: Int,
        galleryExtraPrefetchCount: Int
    ) {
        val sourceUrl = _dataState.value.sourceUrl
        val guideSyncToken = info?.syncedAtMs ?: -1L
        val existing = _prefetchState.value
        if (existing.sourceUrl != sourceUrl || existing.guideSyncToken != guideSyncToken) {
            prefetchJob?.cancel()
            _prefetchState.value = BaStudentGuidePrefetchUiState(
                sourceUrl = sourceUrl,
                guideSyncToken = guideSyncToken
            )
        }
        if (activeBottomTab == GuideBottomTab.Gallery) {
            _prefetchState.update { state ->
                state.copy(galleryPrefetchRequested = true)
            }
        }
        val targetStage = if (_prefetchState.value.galleryPrefetchRequested) 2 else 1
        val currentStage = _prefetchState.value.staticImagePrefetchStage
        if (info == null || sourceUrl.isBlank() || currentStage >= targetStage) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            runPrefetchStages(
                info = info,
                sourceUrl = sourceUrl,
                targetStage = targetStage,
                initialPrefetchCount = initialPrefetchCount,
                galleryExtraPrefetchCount = galleryExtraPrefetchCount
            )
        }
    }

    private fun loadGuide(
        sourceUrl: String,
        manualRefresh: Boolean,
        allowInitialDelay: Boolean
    ) {
        val currentBinding = binding ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
            val result = repository.loadGuide(
                context = appContext,
                sourceUrl = sourceUrl,
                currentInfo = _dataState.value.info,
                manualRefresh = manualRefresh,
                loadFailedText = currentBinding.loadFailedText,
                refreshFailedKeepCacheText = currentBinding.refreshFailedKeepCacheText
            )
            _dataState.update { state ->
                if (state.sourceUrl == sourceUrl) {
                    lastLoadedSourceUrl = sourceUrl
                    state.copy(
                        info = result.info,
                        loading = false,
                        error = result.error
                    )
                } else {
                    state
                }
            }
        }
    }

    private suspend fun runPrefetchStages(
        info: BaStudentGuideInfo,
        sourceUrl: String,
        targetStage: Int,
        initialPrefetchCount: Int,
        galleryExtraPrefetchCount: Int
    ) {
        val guideSyncToken = info.syncedAtMs
        val allUrls = repository.collectStaticImagePrefetchUrls(info)
        fun updatePrefetchIfCurrent(
            transform: (BaStudentGuidePrefetchUiState) -> BaStudentGuidePrefetchUiState
        ) {
            _prefetchState.update { state ->
                if (state.sourceUrl == sourceUrl && state.guideSyncToken == guideSyncToken) {
                    transform(state)
                } else {
                    state
                }
            }
        }
        if (_prefetchState.value.staticImagePrefetchStage < 1 && targetStage >= 1) {
            val urls = allUrls.take(initialPrefetchCount)
            if (urls.isNotEmpty()) {
                repository.prefetchStaticImages(
                    context = appContext,
                    sourceUrl = sourceUrl,
                    rawUrls = urls
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
            val urls = allUrls
                .drop(initialPrefetchCount)
                .take(galleryExtraPrefetchCount)
            if (urls.isNotEmpty()) {
                repository.prefetchStaticImages(
                    context = appContext,
                    sourceUrl = sourceUrl,
                    rawUrls = urls
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
}
