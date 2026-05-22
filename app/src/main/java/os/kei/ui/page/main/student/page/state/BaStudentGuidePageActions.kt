package os.kei.ui.page.main.student.page.state

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.support.normalizeGuidePlaybackSource

internal data class BaStudentGuidePageActions(
    val shareSource: () -> Unit,
    val openExternal: (String) -> Unit,
    val openGuideInPage: (String) -> Unit,
    val saveGuideMedia: (String, String) -> Unit,
    val saveGuideMediaPack: (List<Pair<String, String>>, String) -> Unit,
    val toggleVoicePlayback: (String) -> Unit,
    val requestRefresh: () -> Unit,
)

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
    saveGuideMediaPack: (List<Pair<String, String>>, String) -> Unit,
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
        saveGuideMediaPack,
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
                        val chooser =
                            Intent.createChooser(intent, shareSourceChooserTitle).apply {
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
                val target =
                    if (contentId != null && contentId > 0L) {
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
                        context.showToast(
                            context.resolveString(
                                R.string.guide_toast_voice_play_failed_with_reason,
                                error.javaClass.simpleName,
                            ),
                        )
                    }
                }
            },
            requestRefresh = onRefresh,
        )
    }
}
