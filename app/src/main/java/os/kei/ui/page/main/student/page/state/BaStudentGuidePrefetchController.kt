package os.kei.ui.page.main.student.page.state

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBottomTab

internal class BaStudentGuidePrefetchController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val repository: BaStudentGuideRepository,
) {
    private val mutableState = MutableStateFlow(BaStudentGuidePrefetchUiState())
    private var prefetchJob: Job? = null

    val state: StateFlow<BaStudentGuidePrefetchUiState> = mutableState.asStateFlow()

    fun resetForSource(
        sourceUrl: String,
        guideSyncToken: Long,
    ) {
        prefetchJob?.cancel()
        mutableState.value =
            BaStudentGuidePrefetchUiState(
                sourceUrl = sourceUrl,
                guideSyncToken = guideSyncToken,
            )
    }

    fun syncStaticImagePrefetch(
        sourceUrl: String,
        info: BaStudentGuideInfo?,
        prefetchBottomTab: GuideBottomTab,
        initialPrefetchCount: Int,
        galleryExtraPrefetchCount: Int,
    ) {
        val guideSyncToken = info?.syncedAtMs ?: -1L
        val existing = mutableState.value
        if (existing.sourceUrl != sourceUrl || existing.guideSyncToken != guideSyncToken) {
            resetForSource(
                sourceUrl = sourceUrl,
                guideSyncToken = guideSyncToken,
            )
        }
        if (prefetchBottomTab == GuideBottomTab.Gallery) {
            mutableState.update { state ->
                state.copy(galleryPrefetchRequested = true)
            }
        }
        val targetStage = if (mutableState.value.galleryPrefetchRequested) 2 else 1
        val currentStage = mutableState.value.staticImagePrefetchStage
        if (info == null || sourceUrl.isBlank() || currentStage >= targetStage) return
        prefetchJob?.cancel()
        prefetchJob =
            scope.launch {
                runPrefetchStages(
                    info = info,
                    sourceUrl = sourceUrl,
                    targetStage = targetStage,
                    initialPrefetchCount = initialPrefetchCount,
                    galleryExtraPrefetchCount = galleryExtraPrefetchCount,
                )
            }
    }

    fun cancel() {
        prefetchJob?.cancel()
        prefetchJob = null
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

        if (mutableState.value.staticImagePrefetchStage < 1 && targetStage >= 1) {
            val urls = allUrls.take(safeInitialPrefetchCount)
            if (urls.isNotEmpty()) {
                repository.prefetchStaticImages(
                    context = appContext,
                    sourceUrl = sourceUrl,
                    rawUrls = urls,
                )
                updatePrefetchIfCurrent(sourceUrl, guideSyncToken) { state ->
                    state.copy(galleryCacheRevision = state.galleryCacheRevision + 1)
                }
            }
            updatePrefetchIfCurrent(sourceUrl, guideSyncToken) { state ->
                state.copy(staticImagePrefetchStage = 1)
            }
        }
        if (mutableState.value.staticImagePrefetchStage < 2 && targetStage >= 2) {
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
                updatePrefetchIfCurrent(sourceUrl, guideSyncToken) { state ->
                    state.copy(galleryCacheRevision = state.galleryCacheRevision + 1)
                }
            }
            updatePrefetchIfCurrent(sourceUrl, guideSyncToken) { state ->
                state.copy(staticImagePrefetchStage = 2)
            }
        }
    }

    private fun updatePrefetchIfCurrent(
        sourceUrl: String,
        guideSyncToken: Long,
        transform: (BaStudentGuidePrefetchUiState) -> BaStudentGuidePrefetchUiState,
    ) {
        mutableState.update { state ->
            if (state.sourceUrl == sourceUrl && state.guideSyncToken == guideSyncToken) {
                transform(state)
            } else {
                state
            }
        }
    }
}
