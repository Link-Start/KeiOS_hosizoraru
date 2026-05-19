package os.kei.ui.page.main.student.page.state

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import os.kei.core.ext.showToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.support.GuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.GuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaPackSaveRequest
import os.kei.ui.page.main.student.page.support.buildGuideMediaSaveRequest
import os.kei.ui.page.main.student.page.support.copyGuideMediaPackToUriAsync
import os.kei.ui.page.main.student.page.support.copyGuideMediaToUriAsync
import os.kei.ui.page.main.student.page.support.createUniqueDocumentUriInTreeAsync
import os.kei.ui.page.main.student.page.support.normalizeGuidePlaybackSource

internal data class BaStudentGuidePageActions(
    val shareSource: () -> Unit,
    val openExternal: (String) -> Unit,
    val openGuideInPage: (String) -> Unit,
    val saveGuideMedia: (String, String) -> Unit,
    val saveGuideMediaPack: (List<Pair<String, String>>, String) -> Unit,
    val toggleVoicePlayback: (String) -> Unit,
    val requestRefresh: () -> Unit
)

@Composable
internal fun rememberBaStudentGuideMediaSaveAction(
    pageScope: CoroutineScope,
    currentStudentNamePrefix: () -> String
): (String, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCustomSaveRequest by remember { mutableStateOf<GuideMediaSaveRequest?>(null) }
    var pendingFixedSaveRequest by remember { mutableStateOf<GuideMediaSaveRequest?>(null) }

    val customSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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

    val fixedFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        BASettingsStore.saveMediaSaveFixedTreeUri(treeUri.toString())
        pendingFixedSaveRequest = null
        pageScope.launch {
            val targetUri = createUniqueDocumentUriInTreeAsync(
                context = context,
                treeUri = treeUri,
                mimeType = request.mimeType,
                fileName = request.fileName
            )
            val success = targetUri?.let { uri ->
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
            val request = buildGuideMediaSaveRequest(
                rawUrl = rawMediaUrl,
                rawTitle = rawTitle,
                rawPrefix = studentNamePrefixState.value()
            )
            if (request == null) {
                context.showToast(R.string.guide_media_save_empty)
            } else {
                val useFixedSaveLocation = BASettingsStore.loadMediaSaveCustomEnabled()
                if (!useFixedSaveLocation) {
                    pendingCustomSaveRequest = request
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = request.mimeType
                        putExtra(Intent.EXTRA_TITLE, request.fileName)
                    }
                    customSaveLauncher.launch(intent)
                } else {
                    val fixedTreeUriRaw = BASettingsStore.loadMediaSaveFixedTreeUri()
                    val fixedTreeUri = fixedTreeUriRaw.takeIf { it.isNotBlank() }?.let { raw ->
                        runCatching { raw.toUri() }.getOrNull()
                    }
                    if (fixedTreeUri != null) {
                        pageScope.launch {
                            val targetUri = createUniqueDocumentUriInTreeAsync(
                                context = context,
                                treeUri = fixedTreeUri,
                                mimeType = request.mimeType,
                                fileName = request.fileName
                            )
                            val success = targetUri?.let { uri ->
                                copyGuideMediaToUriAsync(context, request.sourceUrl, uri)
                            } == true
                            if (success) {
                                context.showToast(context.resolveString(
                                    R.string.guide_media_save_success,
                                    request.fileName
                                ))
                            } else {
                                BASettingsStore.saveMediaSaveFixedTreeUri("")
                                pendingFixedSaveRequest = request
                                val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    )
                                }
                                fixedFolderLauncher.launch(pickerIntent)
                            }
                        }
                    } else {
                        pendingFixedSaveRequest = request
                        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                            putExtra(
                                DocumentsContract.EXTRA_INITIAL_URI,
                                "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri()
                            )
                        }
                        fixedFolderLauncher.launch(pickerIntent)
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberBaStudentGuideMediaPackSaveAction(
    pageScope: CoroutineScope,
    currentStudentNamePrefix: () -> String
): (List<Pair<String, String>>, String) -> Unit {
    val studentNamePrefixState = rememberUpdatedState(currentStudentNamePrefix)
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCustomPackSaveRequest by remember { mutableStateOf<GuideMediaPackSaveRequest?>(null) }
    var pendingFixedPackSaveRequest by remember { mutableStateOf<GuideMediaPackSaveRequest?>(null) }

    val customPackSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        val request = pendingCustomPackSaveRequest
        pendingCustomPackSaveRequest = null
        if (request == null || targetUri == null) {
            return@rememberLauncherForActivityResult
        }
        pageScope.launch {
            val result = copyGuideMediaPackToUriAsync(
                context = context,
                request = request,
                outputUri = targetUri
            )
            if (result.success) {
                context.showToast(context.resolveString(
                    R.string.guide_media_pack_save_success,
                    result.savedCount,
                    result.totalCount,
                    request.fileName
                ))
            } else {
                context.showToast(R.string.guide_media_pack_save_failed)
            }
        }
    }

    val fixedFolderPackSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        BASettingsStore.saveMediaSaveFixedTreeUri(treeUri.toString())
        pendingFixedPackSaveRequest = null
        pageScope.launch {
            val targetUri = createUniqueDocumentUriInTreeAsync(
                context = context,
                treeUri = treeUri,
                mimeType = "application/zip",
                fileName = request.fileName
            )
            val result = targetUri?.let { uri ->
                copyGuideMediaPackToUriAsync(
                    context = context,
                    request = request,
                    outputUri = uri
                )
            }
            if (result?.success == true) {
                context.showToast(context.resolveString(
                    R.string.guide_media_pack_save_success,
                    result.savedCount,
                    result.totalCount,
                    request.fileName
                ))
            } else {
                context.showToast(R.string.guide_media_pack_save_failed)
            }
        }
    }

    return remember(context, pageScope, customPackSaveLauncher, fixedFolderPackSaveLauncher) {
        { rawItems: List<Pair<String, String>>, rawPackTitle: String ->
            val request = buildGuideMediaPackSaveRequest(
                rawItems = rawItems,
                rawPackTitle = rawPackTitle,
                rawPrefix = studentNamePrefixState.value()
            )
            if (request == null) {
                context.showToast(R.string.guide_media_save_empty)
            } else {
                val useFixedSaveLocation = BASettingsStore.loadMediaSaveCustomEnabled()
                if (!useFixedSaveLocation) {
                    pendingCustomPackSaveRequest = request
                    customPackSaveLauncher.launch(request.fileName)
                } else {
                    val fixedTreeUriRaw = BASettingsStore.loadMediaSaveFixedTreeUri()
                    val fixedTreeUri = fixedTreeUriRaw.takeIf { it.isNotBlank() }?.let { raw ->
                        runCatching { raw.toUri() }.getOrNull()
                    }
                    if (fixedTreeUri != null) {
                        pageScope.launch {
                            val targetUri = createUniqueDocumentUriInTreeAsync(
                                context = context,
                                treeUri = fixedTreeUri,
                                mimeType = "application/zip",
                                fileName = request.fileName
                            )
                            val result = targetUri?.let { uri ->
                                copyGuideMediaPackToUriAsync(
                                    context = context,
                                    request = request,
                                    outputUri = uri
                                )
                            }
                            if (result?.success == true) {
                                context.showToast(context.resolveString(
                                    R.string.guide_media_pack_save_success,
                                    result.savedCount,
                                    result.totalCount,
                                    request.fileName
                                ))
                            } else if (targetUri != null) {
                                context.showToast(R.string.guide_media_pack_save_failed)
                            } else {
                                BASettingsStore.saveMediaSaveFixedTreeUri("")
                                pendingFixedPackSaveRequest = request
                                val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    )
                                }
                                fixedFolderPackSaveLauncher.launch(pickerIntent)
                            }
                        }
                    } else {
                        pendingFixedPackSaveRequest = request
                        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                            putExtra(
                                DocumentsContract.EXTRA_INITIAL_URI,
                                "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri()
                            )
                        }
                        fixedFolderPackSaveLauncher.launch(pickerIntent)
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberBaStudentGuidePageActions(
    info: BaStudentGuideInfo?,
    sourceUrl: String,
    shareSourceEmptyText: String,
    shareSourceChooserTitle: String,
    shareSourceFailedText: String,
    openLinkFailedText: String,
    voicePlayerController: BaStudentGuideVoicePlayerController,
    playingVoiceUrl: String,
    onPlayingVoiceUrlChange: (String) -> Unit,
    onIsVoicePlayingChange: (Boolean) -> Unit,
    onVoicePlayProgressChange: (Float) -> Unit,
    onOpenGuideInPage: (String) -> Unit,
    onRefresh: () -> Unit,
    saveGuideMedia: (String, String) -> Unit,
    saveGuideMediaPack: (List<Pair<String, String>>, String) -> Unit
): BaStudentGuidePageActions {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(
        context,
        info,
        sourceUrl,
        shareSourceEmptyText,
        shareSourceChooserTitle,
        shareSourceFailedText,
        openLinkFailedText,
        voicePlayerController,
        playingVoiceUrl,
        onPlayingVoiceUrlChange,
        onIsVoicePlayingChange,
        onVoicePlayProgressChange,
        onOpenGuideInPage,
        onRefresh,
        saveGuideMedia,
        saveGuideMediaPack
    ) {
        BaStudentGuidePageActions(
            shareSource = {
                val raw = info?.sourceUrl?.ifBlank { sourceUrl } ?: sourceUrl
                val target = normalizeGuideUrl(raw)
                if (target.isBlank()) {
                    context.showToast(shareSourceEmptyText)
                } else {
                    runCatching {
                        val intent = SafeExternalIntents.textShareIntent(text = target)
                        val chooser = Intent.createChooser(intent, shareSourceChooserTitle).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    }.onFailure {
                        context.showToast(shareSourceFailedText)
                    }
                }
            },
            openExternal = { rawUrl ->
                val target = normalizeGuideUrl(rawUrl)
                if (target.isNotBlank()) {
                    if (!SafeExternalIntents.startBrowsableUrl(context, target, newTask = true)) {
                        context.showToast(openLinkFailedText)
                    }
                }
            },
            openGuideInPage = { rawUrl ->
                val normalized = normalizeGuideUrl(rawUrl)
                val contentId = extractGuideContentIdFromUrl(normalized)
                val target = if (contentId != null && contentId > 0L) {
                    "https://www.gamekee.com/ba/tj/$contentId.html"
                } else {
                    normalized
                }
                if (target.isNotBlank() && target != sourceUrl) {
                    onOpenGuideInPage(target)
                }
            },
            saveGuideMedia = saveGuideMedia,
            saveGuideMediaPack = saveGuideMediaPack,
            toggleVoicePlayback = { rawAudioUrl ->
                val target = normalizeGuidePlaybackSource(rawAudioUrl)
                if (target.isNotBlank()) {
                    runCatching {
                        if (playingVoiceUrl == target) {
                            val existingPlayer = voicePlayerController.player
                            if (existingPlayer?.isPlaying == true) {
                                existingPlayer.pause()
                                onPlayingVoiceUrlChange("")
                                onIsVoicePlayingChange(false)
                                onVoicePlayProgressChange(0f)
                            } else if (existingPlayer != null) {
                                existingPlayer.play()
                                onPlayingVoiceUrlChange(target)
                                onIsVoicePlayingChange(true)
                            } else {
                                val voicePlayer = voicePlayerController.getOrCreate()
                                voicePlayer.setMediaItem(MediaItem.fromUri(target))
                                voicePlayer.prepare()
                                voicePlayer.play()
                                onPlayingVoiceUrlChange(target)
                                onIsVoicePlayingChange(true)
                                onVoicePlayProgressChange(0f)
                            }
                        } else {
                            val voicePlayer = voicePlayerController.getOrCreate()
                            voicePlayer.setMediaItem(MediaItem.fromUri(target))
                            voicePlayer.prepare()
                            voicePlayer.play()
                            onPlayingVoiceUrlChange(target)
                            onIsVoicePlayingChange(true)
                            onVoicePlayProgressChange(0f)
                        }
                    }.onFailure { error ->
                        onPlayingVoiceUrlChange("")
                        onIsVoicePlayingChange(false)
                        onVoicePlayProgressChange(0f)
                        context.showToast(context.resolveString(
                            R.string.guide_toast_voice_play_failed_with_reason,
                            error.javaClass.simpleName
                        ))
                    }
                }
            },
            requestRefresh = onRefresh
        )
    }
}
