package os.kei.ui.page.main.student.page.state

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveResult
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.copyGuideMediaPackToUriAsync
import os.kei.ui.page.main.student.page.support.copyGuideMediaToUriAsync
import os.kei.ui.page.main.student.page.support.createUniqueDocumentUriInTreeAsync

internal data class BaStudentGuideMediaSaveLocation(
    val useFixedLocation: Boolean,
    val fixedTreeUriRaw: String,
)

internal data class BaStudentGuideFixedMediaSaveResult(
    val saved: Boolean,
    val needsFolder: Boolean,
)

internal data class BaStudentGuideFixedMediaPackSaveResult(
    val result: GuideMediaPackSaveResult?,
    val needsFolder: Boolean,
)

internal class BaStudentGuideMediaSaveRepository(
    private val derivationDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val settingsDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val mediaDispatcher: CoroutineDispatcher = AppDispatchers.media,
) {
    suspend fun buildMediaSaveRequest(
        rawUrl: String,
        rawTitle: String,
        rawPrefix: String,
    ): GuideMediaSaveRequest? =
        withContext(derivationDispatcher) {
            buildGuideMediaSaveRequest(
                rawUrl = rawUrl,
                rawTitle = rawTitle,
                rawPrefix = rawPrefix,
            )
        }

    suspend fun buildMediaPackSaveRequest(
        rawItems: List<Pair<String, String>>,
        rawPackTitle: String,
        rawPrefix: String,
    ): GuideMediaPackSaveRequest? =
        withContext(derivationDispatcher) {
            buildGuideMediaPackSaveRequest(
                rawItems = rawItems,
                rawPackTitle = rawPackTitle,
                rawPrefix = rawPrefix,
            )
        }

    suspend fun loadSaveLocation(): BaStudentGuideMediaSaveLocation =
        withContext(settingsDispatcher) {
            BaStudentGuideMediaSaveLocation(
                useFixedLocation = BASettingsStore.loadMediaSaveCustomEnabled(),
                fixedTreeUriRaw = BASettingsStore.loadMediaSaveFixedTreeUri(),
            )
        }

    suspend fun saveFixedTreeUri(treeUriRaw: String) {
        withContext(settingsDispatcher) {
            BASettingsStore.saveMediaSaveFixedTreeUri(treeUriRaw)
        }
    }

    suspend fun clearFixedTreeUri() {
        saveFixedTreeUri("")
    }

    suspend fun copyMediaToUri(
        context: Context,
        request: GuideMediaSaveRequest,
        targetUri: Uri,
    ): Boolean =
        copyGuideMediaToUriAsync(
            context = context,
            sourceUrl = request.sourceUrl,
            outputUri = targetUri,
            ioDispatcher = mediaDispatcher,
        )

    suspend fun copyMediaPackToUri(
        context: Context,
        request: GuideMediaPackSaveRequest,
        targetUri: Uri,
    ): GuideMediaPackSaveResult =
        copyGuideMediaPackToUriAsync(
            context = context,
            request = request,
            outputUri = targetUri,
            ioDispatcher = mediaDispatcher,
        )

    suspend fun copyMediaToFixedTree(
        context: Context,
        request: GuideMediaSaveRequest,
        treeUri: Uri,
    ): BaStudentGuideFixedMediaSaveResult {
        val targetUri =
            createUniqueDocumentUriInTreeAsync(
                context = context,
                treeUri = treeUri,
                mimeType = request.mimeType,
                fileName = request.fileName,
                ioDispatcher = mediaDispatcher,
            )
        if (targetUri == null) {
            clearFixedTreeUri()
            return BaStudentGuideFixedMediaSaveResult(saved = false, needsFolder = true)
        }
        val success = copyMediaToUri(context, request, targetUri)
        return BaStudentGuideFixedMediaSaveResult(saved = success, needsFolder = false)
    }

    suspend fun copyMediaPackToFixedTree(
        context: Context,
        request: GuideMediaPackSaveRequest,
        treeUri: Uri,
    ): BaStudentGuideFixedMediaPackSaveResult {
        val targetUri =
            createUniqueDocumentUriInTreeAsync(
                context = context,
                treeUri = treeUri,
                mimeType = "application/zip",
                fileName = request.fileName,
                ioDispatcher = mediaDispatcher,
            )
        if (targetUri == null) {
            clearFixedTreeUri()
            return BaStudentGuideFixedMediaPackSaveResult(result = null, needsFolder = true)
        }
        return BaStudentGuideFixedMediaPackSaveResult(
            result = copyMediaPackToUri(context, request, targetUri),
            needsFolder = false,
        )
    }

    fun fixedTreeUriOrNull(saveLocation: BaStudentGuideMediaSaveLocation): Uri? =
        saveLocation
            .fixedTreeUriRaw
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
}
