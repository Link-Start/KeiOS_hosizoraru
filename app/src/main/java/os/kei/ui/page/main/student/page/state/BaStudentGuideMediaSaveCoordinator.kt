package os.kei.ui.page.main.student.page.state

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveResult
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest

internal sealed interface BaStudentGuideEvent {
    data class LaunchCustomMediaSave(
        val request: GuideMediaSaveRequest,
    ) : BaStudentGuideEvent

    data class RequestFixedMediaFolder(
        val request: GuideMediaSaveRequest,
        val withInitialDownload: Boolean,
    ) : BaStudentGuideEvent

    data class MediaSaveCompleted(
        val request: GuideMediaSaveRequest,
        val success: Boolean,
    ) : BaStudentGuideEvent

    data class LaunchCustomMediaPackSave(
        val request: GuideMediaPackSaveRequest,
    ) : BaStudentGuideEvent

    data class RequestFixedMediaPackFolder(
        val request: GuideMediaPackSaveRequest,
        val withInitialDownload: Boolean,
    ) : BaStudentGuideEvent

    data class MediaPackSaveCompleted(
        val request: GuideMediaPackSaveRequest,
        val result: GuideMediaPackSaveResult,
    ) : BaStudentGuideEvent

    data object EmptyMediaSaveRequest : BaStudentGuideEvent

    data class MediaSaveFailed(
        val error: Throwable,
    ) : BaStudentGuideEvent
}

internal class BaStudentGuideMediaSaveCoordinator(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repository: BaStudentGuideMediaSaveRepository = BaStudentGuideMediaSaveRepository,
) {
    private val mutableEvents = MutableSharedFlow<BaStudentGuideEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<BaStudentGuideEvent> = mutableEvents.asSharedFlow()

    fun requestMediaSave(
        rawMediaUrl: String,
        rawTitle: String,
        studentNamePrefix: String,
    ) {
        scope.launch {
            runMediaSave {
                val request =
                    repository.buildMediaSaveRequest(
                        rawUrl = rawMediaUrl,
                        rawTitle = rawTitle,
                        rawPrefix = studentNamePrefix,
                    )
                if (request == null) {
                    mutableEvents.emit(BaStudentGuideEvent.EmptyMediaSaveRequest)
                    return@runMediaSave
                }
                dispatchMediaSaveRequest(request)
            }
        }
    }

    fun requestMediaPackSave(
        rawItems: List<Pair<String, String>>,
        rawPackTitle: String,
        studentNamePrefix: String,
    ) {
        scope.launch {
            runMediaSave {
                val request =
                    repository.buildMediaPackSaveRequest(
                        rawItems = rawItems,
                        rawPackTitle = rawPackTitle,
                        rawPrefix = studentNamePrefix,
                    )
                if (request == null) {
                    mutableEvents.emit(BaStudentGuideEvent.EmptyMediaSaveRequest)
                    return@runMediaSave
                }
                dispatchMediaPackSaveRequest(request)
            }
        }
    }

    fun completeCustomMediaSave(
        request: GuideMediaSaveRequest,
        targetUri: Uri,
    ) {
        scope.launch {
            runMediaSave {
                val success =
                    repository.copyMediaToUri(
                        context = appContext,
                        request = request,
                        targetUri = targetUri,
                    )
                mutableEvents.emit(BaStudentGuideEvent.MediaSaveCompleted(request, success))
            }
        }
    }

    fun completeFixedMediaSave(
        request: GuideMediaSaveRequest,
        treeUri: Uri,
    ) {
        scope.launch {
            runMediaSave {
                repository.saveFixedTreeUri(treeUri.toString())
                emitFixedMediaSaveResult(
                    request = request,
                    result =
                        repository.copyMediaToFixedTree(
                            context = appContext,
                            request = request,
                            treeUri = treeUri,
                        ),
                )
            }
        }
    }

    fun completeCustomMediaPackSave(
        request: GuideMediaPackSaveRequest,
        targetUri: Uri,
    ) {
        scope.launch {
            runMediaSave {
                val result =
                    repository.copyMediaPackToUri(
                        context = appContext,
                        request = request,
                        targetUri = targetUri,
                    )
                mutableEvents.emit(BaStudentGuideEvent.MediaPackSaveCompleted(request, result))
            }
        }
    }

    fun completeFixedMediaPackSave(
        request: GuideMediaPackSaveRequest,
        treeUri: Uri,
    ) {
        scope.launch {
            runMediaSave {
                repository.saveFixedTreeUri(treeUri.toString())
                emitFixedMediaPackSaveResult(
                    request = request,
                    result =
                        repository.copyMediaPackToFixedTree(
                            context = appContext,
                            request = request,
                            treeUri = treeUri,
                        ),
                )
            }
        }
    }

    private suspend fun dispatchMediaSaveRequest(request: GuideMediaSaveRequest) {
        val saveLocation = repository.loadSaveLocation()
        if (!saveLocation.useFixedLocation) {
            mutableEvents.emit(BaStudentGuideEvent.LaunchCustomMediaSave(request))
            return
        }
        val fixedTreeUri = repository.fixedTreeUriOrNull(saveLocation)
        if (fixedTreeUri == null) {
            mutableEvents.emit(BaStudentGuideEvent.RequestFixedMediaFolder(request, withInitialDownload = true))
            return
        }
        emitFixedMediaSaveResult(
            request = request,
            result =
                repository.copyMediaToFixedTree(
                    context = appContext,
                    request = request,
                    treeUri = fixedTreeUri,
                ),
        )
    }

    private suspend fun dispatchMediaPackSaveRequest(request: GuideMediaPackSaveRequest) {
        val saveLocation = repository.loadSaveLocation()
        if (!saveLocation.useFixedLocation) {
            mutableEvents.emit(BaStudentGuideEvent.LaunchCustomMediaPackSave(request))
            return
        }
        val fixedTreeUri = repository.fixedTreeUriOrNull(saveLocation)
        if (fixedTreeUri == null) {
            mutableEvents.emit(BaStudentGuideEvent.RequestFixedMediaPackFolder(request, withInitialDownload = true))
            return
        }
        emitFixedMediaPackSaveResult(
            request = request,
            result =
                repository.copyMediaPackToFixedTree(
                    context = appContext,
                    request = request,
                    treeUri = fixedTreeUri,
                ),
        )
    }

    private suspend fun emitFixedMediaSaveResult(
        request: GuideMediaSaveRequest,
        result: BaStudentGuideFixedMediaSaveResult,
    ) {
        when {
            result.saved -> mutableEvents.emit(BaStudentGuideEvent.MediaSaveCompleted(request, success = true))
            result.needsFolder -> mutableEvents.emit(BaStudentGuideEvent.RequestFixedMediaFolder(request, withInitialDownload = false))
            else -> mutableEvents.emit(BaStudentGuideEvent.MediaSaveCompleted(request, success = false))
        }
    }

    private suspend fun emitFixedMediaPackSaveResult(
        request: GuideMediaPackSaveRequest,
        result: BaStudentGuideFixedMediaPackSaveResult,
    ) {
        when {
            result.result != null -> mutableEvents.emit(BaStudentGuideEvent.MediaPackSaveCompleted(request, result.result))
            result.needsFolder -> mutableEvents.emit(BaStudentGuideEvent.RequestFixedMediaPackFolder(request, withInitialDownload = false))
            else ->
                mutableEvents.emit(
                    BaStudentGuideEvent.MediaPackSaveCompleted(
                        request = request,
                        result = GuideMediaPackSaveResult(totalCount = request.entries.size, savedCount = 0),
                    ),
                )
        }
    }

    private suspend fun runMediaSave(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableEvents.emit(BaStudentGuideEvent.MediaSaveFailed(error))
        }
    }
}
