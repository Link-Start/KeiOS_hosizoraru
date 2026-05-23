@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.state

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest

@Composable
internal fun BindBaStudentGuideMediaSaveEvents(guideViewModel: BaStudentGuideViewModel) {
    val context = LocalContext.current

    val customSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = guideViewModel.consumePendingCustomMediaSaveRequest()
            val targetUri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && request != null && targetUri != null) {
                guideViewModel.completeCustomMediaSave(request, targetUri)
            }
        }
    val fixedFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = guideViewModel.consumePendingFixedMediaSaveRequest()
            val treeUri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && request != null && treeUri != null) {
                persistGuideMediaTreePermission(result.data, treeUri, context.contentResolver)
                guideViewModel.completeFixedMediaSave(request, treeUri)
            }
        }
    val customPackSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { targetUri ->
            val request = guideViewModel.consumePendingCustomMediaPackSaveRequest()
            if (request != null && targetUri != null) {
                guideViewModel.completeCustomMediaPackSave(request, targetUri)
            }
        }
    val fixedFolderPackSaveLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val request = guideViewModel.consumePendingFixedMediaPackSaveRequest()
            val treeUri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && request != null && treeUri != null) {
                persistGuideMediaTreePermission(result.data, treeUri, context.contentResolver)
                guideViewModel.completeFixedMediaPackSave(request, treeUri)
            }
        }

    LaunchedEffect(guideViewModel) {
        guideViewModel.events.collect { event ->
            when (event) {
                is BaStudentGuideEvent.LaunchCustomMediaSave -> {
                    guideViewModel.armPendingCustomMediaSaveRequest(event.request)
                    customSaveLauncher.launch(createGuideMediaDocumentIntent(event.request))
                }

                is BaStudentGuideEvent.RequestFixedMediaFolder -> {
                    guideViewModel.armPendingFixedMediaSaveRequest(event.request)
                    fixedFolderLauncher.launch(guideMediaFolderPickerIntent(event.withInitialDownload))
                }

                is BaStudentGuideEvent.MediaSaveCompleted -> {
                    if (event.success) {
                        context.showToast(
                            context.resolveString(
                                R.string.guide_media_save_success,
                                event.request.fileName,
                            ),
                        )
                    } else {
                        context.showToast(R.string.guide_media_save_failed)
                    }
                }

                is BaStudentGuideEvent.LaunchCustomMediaPackSave -> {
                    guideViewModel.armPendingCustomMediaPackSaveRequest(event.request)
                    customPackSaveLauncher.launch(event.request.fileName)
                }

                is BaStudentGuideEvent.RequestFixedMediaPackFolder -> {
                    guideViewModel.armPendingFixedMediaPackSaveRequest(event.request)
                    fixedFolderPackSaveLauncher.launch(guideMediaFolderPickerIntent(event.withInitialDownload))
                }

                is BaStudentGuideEvent.MediaPackSaveCompleted -> {
                    if (event.result.success) {
                        context.showToast(
                            context.resolveString(
                                R.string.guide_media_pack_save_success,
                                event.result.savedCount,
                                event.result.totalCount,
                                event.request.fileName,
                            ),
                        )
                    } else {
                        context.showToast(R.string.guide_media_pack_save_failed)
                    }
                }

                BaStudentGuideEvent.EmptyMediaSaveRequest -> {
                    context.showToast(R.string.guide_media_save_empty)
                }

                is BaStudentGuideEvent.MediaSaveFailed -> {
                    context.showToast(R.string.guide_media_save_failed)
                }

                BaStudentGuideEvent.BgmFavoriteAdded -> {
                    context.showToast(R.string.guide_bgm_toast_favorite_added)
                }

                BaStudentGuideEvent.BgmFavoriteRemoved -> {
                    context.showToast(R.string.guide_bgm_toast_favorite_removed)
                }
            }
        }
    }
}

@Composable
internal fun rememberBaStudentGuideMediaSaveAction(
    guideViewModel: BaStudentGuideViewModel,
    currentStudentNamePrefix: () -> String,
): (String, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    return remember(guideViewModel) {
        { rawMediaUrl: String, rawTitle: String ->
            guideViewModel.requestMediaSave(
                rawMediaUrl = rawMediaUrl,
                rawTitle = rawTitle,
                studentNamePrefix = studentNamePrefixState.value(),
            )
        }
    }
}

@Composable
internal fun rememberBaStudentGuideMediaPackSaveAction(
    guideViewModel: BaStudentGuideViewModel,
    currentStudentNamePrefix: () -> String,
): (List<Pair<String, String>>, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    return remember(guideViewModel) {
        { rawItems: List<Pair<String, String>>, rawPackTitle: String ->
            guideViewModel.requestMediaPackSave(
                rawItems = rawItems,
                rawPackTitle = rawPackTitle,
                studentNamePrefix = studentNamePrefixState.value(),
            )
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
    treeUri: Uri,
    contentResolver: ContentResolver,
) {
    runCatching {
        val persistableFlags =
            data.orZeroFlags() and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (persistableFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (persistableFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}

private fun Intent?.orZeroFlags(): Int = this?.flags ?: 0
