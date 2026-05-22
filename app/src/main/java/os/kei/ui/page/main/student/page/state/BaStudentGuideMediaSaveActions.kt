package os.kei.ui.page.main.student.page.state

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.copyGuideMediaPackToUriAsync
import os.kei.ui.page.main.student.page.support.copyGuideMediaToUriAsync
import os.kei.ui.page.main.student.page.support.createUniqueDocumentUriInTreeAsync

@Composable
internal fun rememberBaStudentGuideMediaSaveAction(
    pageScope: CoroutineScope,
    currentStudentNamePrefix: () -> String,
): (String, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCustomSaveRequest by remember { mutableStateOf<GuideMediaSaveRequest?>(null) }
    var pendingFixedSaveRequest by remember { mutableStateOf<GuideMediaSaveRequest?>(null) }

    val customSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = pendingCustomSaveRequest
            pendingCustomSaveRequest = null
            val targetUri = result.data?.data
            if (result.resultCode != Activity.RESULT_OK || request == null || targetUri == null) {
                return@rememberLauncherForActivityResult
            }
            pageScope.launch {
                val success = copyGuideMediaToUriAsync(context, request.sourceUrl, targetUri)
                if (success) {
                    context.showToast(context.resolveString(R.string.guide_media_save_success, request.fileName))
                } else {
                    context.showToast(R.string.guide_media_save_failed)
                }
            }
        }

    val fixedFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = pendingFixedSaveRequest
            if (result.resultCode != Activity.RESULT_OK) {
                pendingFixedSaveRequest = null
                return@rememberLauncherForActivityResult
            }
            val treeUri = result.data?.data
            if (request == null || treeUri == null) {
                pendingFixedSaveRequest = null
                return@rememberLauncherForActivityResult
            }
            persistGuideMediaTreePermission(result.data, treeUri, context.contentResolver)
            pendingFixedSaveRequest = null
            pageScope.launch {
                BaStudentGuideMediaSaveRepository.saveFixedTreeUri(treeUri.toString())
                val targetUri =
                    createUniqueDocumentUriInTreeAsync(
                        context = context,
                        treeUri = treeUri,
                        mimeType = request.mimeType,
                        fileName = request.fileName,
                    )
                val success =
                    targetUri?.let { uri ->
                        copyGuideMediaToUriAsync(context, request.sourceUrl, uri)
                    } == true
                if (success) {
                    context.showToast(context.resolveString(R.string.guide_media_save_success, request.fileName))
                } else {
                    context.showToast(R.string.guide_media_save_failed)
                }
            }
        }

    return remember(context, pageScope, customSaveLauncher, fixedFolderLauncher) {
        { rawMediaUrl: String, rawTitle: String ->
            val request =
                buildGuideMediaSaveRequest(
                    rawUrl = rawMediaUrl,
                    rawTitle = rawTitle,
                    rawPrefix = studentNamePrefixState.value(),
                )
            when {
                request == null -> {
                    context.showToast(R.string.guide_media_save_empty)
                }

                else -> {
                    pageScope.launch {
                        val saveLocation = BaStudentGuideMediaSaveRepository.loadSaveLocation()
                        if (saveLocation.useFixedLocation) {
                            dispatchFixedGuideMediaSaveRequest(
                                request = request,
                                context = context,
                                saveLocation = saveLocation,
                                onRequestFolder = {
                                    pendingFixedSaveRequest = request
                                    fixedFolderLauncher.launch(guideMediaFolderPickerIntent(withInitialDownload = it))
                                },
                            )
                        } else {
                            pendingCustomSaveRequest = request
                            customSaveLauncher.launch(createGuideMediaDocumentIntent(request))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberBaStudentGuideMediaPackSaveAction(
    pageScope: CoroutineScope,
    currentStudentNamePrefix: () -> String,
): (List<Pair<String, String>>, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCustomPackSaveRequest by remember { mutableStateOf<GuideMediaPackSaveRequest?>(null) }
    var pendingFixedPackSaveRequest by remember { mutableStateOf<GuideMediaPackSaveRequest?>(null) }

    val customPackSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { targetUri ->
            val request = pendingCustomPackSaveRequest
            pendingCustomPackSaveRequest = null
            if (request == null || targetUri == null) return@rememberLauncherForActivityResult
            pageScope.launch {
                val result =
                    copyGuideMediaPackToUriAsync(
                        context = context,
                        request = request,
                        outputUri = targetUri,
                    )
                if (result.success) {
                    context.showToast(
                        context.resolveString(
                            R.string.guide_media_pack_save_success,
                            result.savedCount,
                            result.totalCount,
                            request.fileName,
                        ),
                    )
                } else {
                    context.showToast(R.string.guide_media_pack_save_failed)
                }
            }
        }

    val fixedFolderPackSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = pendingFixedPackSaveRequest
            if (result.resultCode != Activity.RESULT_OK) {
                pendingFixedPackSaveRequest = null
                return@rememberLauncherForActivityResult
            }
            val treeUri = result.data?.data
            if (request == null || treeUri == null) {
                pendingFixedPackSaveRequest = null
                return@rememberLauncherForActivityResult
            }
            persistGuideMediaTreePermission(result.data, treeUri, context.contentResolver)
            pendingFixedPackSaveRequest = null
            pageScope.launch {
                BaStudentGuideMediaSaveRepository.saveFixedTreeUri(treeUri.toString())
                saveGuideMediaPackToTree(
                    context = context,
                    request = request,
                    treeUri = treeUri,
                    onNeedFolder = {
                        context.showToast(R.string.guide_media_pack_save_failed)
                    },
                )
            }
        }

    return remember(context, pageScope, customPackSaveLauncher, fixedFolderPackSaveLauncher) {
        { rawItems: List<Pair<String, String>>, rawPackTitle: String ->
            val request =
                buildGuideMediaPackSaveRequest(
                    rawItems = rawItems,
                    rawPackTitle = rawPackTitle,
                    rawPrefix = studentNamePrefixState.value(),
                )
            when {
                request == null -> {
                    context.showToast(R.string.guide_media_save_empty)
                }

                else -> {
                    pageScope.launch {
                        val saveLocation = BaStudentGuideMediaSaveRepository.loadSaveLocation()
                        if (saveLocation.useFixedLocation) {
                            dispatchFixedGuideMediaPackSaveRequest(
                                request = request,
                                context = context,
                                saveLocation = saveLocation,
                                onRequestFolder = {
                                    pendingFixedPackSaveRequest = request
                                    fixedFolderPackSaveLauncher.launch(guideMediaFolderPickerIntent(withInitialDownload = it))
                                },
                            )
                        } else {
                            pendingCustomPackSaveRequest = request
                            customPackSaveLauncher.launch(request.fileName)
                        }
                    }
                }
            }
        }
    }
}

private fun createGuideMediaDocumentIntent(request: GuideMediaSaveRequest): Intent =
    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = request.mimeType
        putExtra(Intent.EXTRA_TITLE, request.fileName)
    }

private fun guideMediaFolderPickerIntent(withInitialDownload: Boolean): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
        if (withInitialDownload) {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri(),
            )
        }
    }

private fun persistGuideMediaTreePermission(
    data: Intent?,
    treeUri: android.net.Uri,
    contentResolver: android.content.ContentResolver,
) {
    runCatching {
        val persistableFlags =
            data?.flags.orZero() and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (persistableFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (persistableFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}

private suspend fun dispatchFixedGuideMediaSaveRequest(
    request: GuideMediaSaveRequest,
    context: android.content.Context,
    saveLocation: BaStudentGuideMediaSaveLocation,
    onRequestFolder: (Boolean) -> Unit,
) {
    val fixedTreeUri =
        saveLocation
            .fixedTreeUriRaw
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
    if (fixedTreeUri == null) {
        onRequestFolder(true)
        return
    }
    val targetUri =
        createUniqueDocumentUriInTreeAsync(
            context = context,
            treeUri = fixedTreeUri,
            mimeType = request.mimeType,
            fileName = request.fileName,
        )
    val success =
        targetUri?.let { uri ->
            copyGuideMediaToUriAsync(context, request.sourceUrl, uri)
        } == true
    if (success) {
        context.showToast(context.resolveString(R.string.guide_media_save_success, request.fileName))
    } else {
        BaStudentGuideMediaSaveRepository.clearFixedTreeUri()
        onRequestFolder(false)
    }
}

private suspend fun dispatchFixedGuideMediaPackSaveRequest(
    request: GuideMediaPackSaveRequest,
    context: android.content.Context,
    saveLocation: BaStudentGuideMediaSaveLocation,
    onRequestFolder: (Boolean) -> Unit,
) {
    val fixedTreeUri =
        saveLocation
            .fixedTreeUriRaw
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
    if (fixedTreeUri == null) {
        onRequestFolder(true)
        return
    }
    saveGuideMediaPackToTree(
        context = context,
        request = request,
        treeUri = fixedTreeUri,
        onNeedFolder = {
            BaStudentGuideMediaSaveRepository.clearFixedTreeUri()
            onRequestFolder(false)
        },
    )
}

private suspend fun saveGuideMediaPackToTree(
    context: android.content.Context,
    request: GuideMediaPackSaveRequest,
    treeUri: android.net.Uri,
    onNeedFolder: suspend () -> Unit,
) {
    val targetUri =
        createUniqueDocumentUriInTreeAsync(
            context = context,
            treeUri = treeUri,
            mimeType = "application/zip",
            fileName = request.fileName,
        )
    val result =
        targetUri?.let { uri ->
            copyGuideMediaPackToUriAsync(
                context = context,
                request = request,
                outputUri = uri,
            )
        }
    when {
        result?.success == true -> {
            context.showToast(
                context.resolveString(
                    R.string.guide_media_pack_save_success,
                    result.savedCount,
                    result.totalCount,
                    request.fileName,
                ),
            )
        }

        targetUri != null -> {
            context.showToast(R.string.guide_media_pack_save_failed)
        }

        else -> {
            onNeedFolder()
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
