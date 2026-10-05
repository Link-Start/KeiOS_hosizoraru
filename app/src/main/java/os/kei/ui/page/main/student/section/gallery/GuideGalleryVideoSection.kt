@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.GuideMediaProgressState
import os.kei.ui.page.main.student.GuideVideoControlAction
import os.kei.ui.page.main.student.GuideVideoFullscreenActivity
import os.kei.ui.page.main.student.guideLocalizedLabel
import os.kei.ui.page.main.student.normalizeGuideMediaSource
import os.kei.ui.page.main.student.section.buildGuideCopyPayload
import os.kei.ui.page.main.student.section.guideCopyable
import os.kei.ui.page.main.student.component.GuidePassiveMetadataPillMinHeight
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.page.main.widget.support.CopyModeSelectionContainer
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GuideGalleryVideoGroupCardItem(
    title: String,
    items: List<BaGuideGalleryItem>,
    previewFallbackUrl: String = "",
    backdrop: Backdrop?,
    onOpenMedia: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit = { _, _ -> },
    mediaUrlResolver: (String) -> String = { it },
    mediaUrlResolverCacheKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    items.filter { it.webMemoryLobby != null }.forEachIndexed { index, item ->
        if (index > 0) Spacer(Modifier.height(CardLayoutRhythm.sectionGap))
        GuideWebMemoryLobbyCard(item = item, previewFallbackUrl = previewFallbackUrl, backdrop = backdrop)
    }
    val videoItems = items.filter { it.webMemoryLobby == null }
    if (videoItems.isEmpty()) return
    if (videoItems.size < items.size) Spacer(Modifier.height(CardLayoutRhythm.sectionGap))
    val context = LocalContext.current
    val displayTitle =
        if (title.isBlank()) {
            stringResource(R.string.guide_gallery_video_format, 1)
        } else {
            guideLocalizedLabel(title)
        }
    var selectedIndex by rememberSaveable(title, videoItems.size) { mutableStateOf(0) }
    LaunchedEffect(videoItems.size) {
        if (selectedIndex !in videoItems.indices) selectedIndex = 0
    }
    val selectedItem = videoItems.getOrElse(selectedIndex) { videoItems.first() }
    val resolverCacheKey = mediaUrlResolverCacheKey ?: mediaUrlResolver
    val displayMediaUrl =
        remember(selectedItem.mediaUrl, resolverCacheKey) {
            mediaUrlResolver(selectedItem.mediaUrl)
        }
    val previewRaw = selectedItem.imageUrl.ifBlank { previewFallbackUrl }
    val displayPreviewUrl =
        remember(previewRaw, resolverCacheKey) {
            mediaUrlResolver(previewRaw)
        }
    val saveTargetUrl =
        remember(displayMediaUrl, displayPreviewUrl) {
            displayMediaUrl.ifBlank { displayPreviewUrl }
        }
    var videoInlineExpanded by remember(displayMediaUrl) { mutableStateOf(false) }
    var videoInlinePlaying by remember(displayMediaUrl) { mutableStateOf(false) }
    var videoControlRequestId by remember(displayMediaUrl) { mutableIntStateOf(0) }
    val noteText = selectedItem.note.trim()
    val optionLabels =
        if (videoItems.size <= 1) {
            listOf(stringResource(R.string.guide_gallery_video_format, 1))
        } else {
            videoItems.mapIndexed { index, item ->
                val normalized = item.title.trim()
                if (normalized.isNotBlank() && normalized != title) {
                    guideLocalizedLabel(normalized, R.string.guide_gallery_item_fallback)
                } else {
                    stringResource(R.string.guide_gallery_video_format, index + 1)
                }
            }
        }

    AppFeatureCard(
        title = displayTitle,
        subtitle = noteText,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color(0x223B82F6),
        headerEndActions = {
            GuideGalleryVideoGroupHeaderActions(
                itemsSize = videoItems.size,
                optionLabels = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { selectedIndex = it },
                displayMediaUrl = displayMediaUrl,
                saveTargetUrl = saveTargetUrl,
                videoInlineExpanded = videoInlineExpanded,
                videoInlinePlaying = videoInlinePlaying,
                backdrop = backdrop,
                onToggleInlinePlay = {
                    if (normalizeGuideMediaSource(displayMediaUrl).isBlank()) {
                        context.showToast(context.resolveString(R.string.guide_media_video_url_invalid))
                    } else if (!videoInlineExpanded) {
                        videoInlineExpanded = true
                    } else {
                        videoControlRequestId += 1
                    }
                },
                onOpenFullscreen = {
                    val normalized = normalizeGuideMediaSource(displayMediaUrl)
                    if (normalized.isBlank()) {
                        context.showToast(context.resolveString(R.string.guide_media_video_url_invalid))
                    } else {
                        GuideVideoFullscreenActivity.launch(
                            context = context,
                            mediaUrl = normalized,
                            previewImageUrl = displayPreviewUrl,
                        )
                    }
                },
                onSaveMedia = {
                    onSaveMedia(
                        saveTargetUrl,
                        optionLabels.getOrElse(selectedIndex) { title },
                    )
                },
            )
        },
    ) {
        if (displayMediaUrl.isBlank()) {
            Text(
                text = stringResource(R.string.guide_gallery_video_not_found),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        } else {
            GuideInlineVideoPlayer(
                mediaUrl = displayMediaUrl,
                previewImageUrl = displayPreviewUrl,
                backdrop = backdrop,
                expanded = videoInlineExpanded,
                onExpandedChange = { expanded -> videoInlineExpanded = expanded },
                controlAction = GuideVideoControlAction.TogglePlayPause,
                controlActionToken = videoControlRequestId,
                onIsPlayingChange = { playing -> videoInlinePlaying = playing },
                showCollapsedPreview = false,
            )
        }
    }
}

@Composable
fun GuideGalleryUnlockLevelCardItem(
    level: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (level.isBlank()) return
    val unlockLevelLabel = stringResource(R.string.guide_gallery_memory_unlock_level)
    val rowCopyPayload =
        remember(unlockLevelLabel, level) {
            buildGuideCopyPayload(unlockLevelLabel, level)
        }
    AppSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color(0x223B82F6),
    ) {
        CopyModeSelectionContainer {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .guideCopyable(rowCopyPayload)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = unlockLevelLabel,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(
                    label = level,
                    color = Color(0xFF3B82F6),
                    backdrop = backdrop,
                    modifier = Modifier.heightIn(min = GuidePassiveMetadataPillMinHeight),
                    size = AppStatusPillSize.Compact,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun GuideInlineVideoPlayer(
    mediaUrl: String,
    previewImageUrl: String = "",
    backdrop: Backdrop?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    controlAction: GuideVideoControlAction? = null,
    controlActionToken: Int = 0,
    onIsPlayingChange: (Boolean) -> Unit = {},
    onBufferingChange: (Boolean) -> Unit = {},
    previewProgressState: GuideMediaProgressState? = null,
    onPreviewLoadingChanged: ((Boolean) -> Unit)? = null,
    showCollapsedPreview: Boolean = true,
    pictureInPictureRequestToken: Int = 0,
    onPictureInPictureRequest: (positionMs: Long) -> Unit = {},
    onVideoBoundsChanged: (Rect?) -> Unit = {},
) {
    val context = LocalContext.current
    val normalizedUrl = remember(mediaUrl) { normalizeGuideMediaSource(mediaUrl) }
    val normalizedPreviewUrl = remember(previewImageUrl) { normalizeGuideMediaSource(previewImageUrl) }
    var videoRatio by remember(normalizedUrl) { mutableStateOf(16f / 9f) }
    var isBuffering by remember(normalizedUrl) { mutableStateOf(false) }
    var isPlaying by remember(normalizedUrl) { mutableStateOf(false) }
    var loadError by remember(normalizedUrl) { mutableStateOf<String?>(null) }
    var loopEnabled by remember(normalizedUrl) { mutableStateOf(false) }
    var retryToken by remember(normalizedUrl) { mutableIntStateOf(0) }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val latestOnPictureInPictureRequest by rememberUpdatedState(onPictureInPictureRequest)
    val openFullscreen =
        remember(context, normalizedUrl) {
            {
                if (normalizedUrl.isBlank()) {
                    context.showToast(context.resolveString(R.string.guide_media_video_url_invalid))
                } else {
                    GuideVideoFullscreenActivity.launch(
                        context = context,
                        mediaUrl = normalizedUrl,
                    )
                }
            }
        }

    if (!expanded) {
        LaunchedEffect(
            showCollapsedPreview,
            previewProgressState,
            onPreviewLoadingChanged,
            onBufferingChange,
            onIsPlayingChange,
            onVideoBoundsChanged,
        ) {
            if (!showCollapsedPreview) {
                previewProgressState?.set(1f)
                onPreviewLoadingChanged?.invoke(false)
            }
            onBufferingChange(false)
            onIsPlayingChange(false)
            onVideoBoundsChanged(null)
        }
        if (showCollapsedPreview) {
            GuideInlineVideoPreview(
                previewImageUrl = normalizedPreviewUrl,
                onOpenFullscreen = {
                    onExpandedChange(false)
                    openFullscreen()
                },
                previewProgressState = previewProgressState,
                onPreviewLoadingChanged = onPreviewLoadingChanged,
            )
        }
        return
    }

    val player =
        rememberGuidePreparedVideoPlayer(
            context = context,
            mediaUrl = normalizedUrl,
            active = expanded && loadError == null,
            restartToken = retryToken,
        )
    BindGuideVideoPlayerState(
        player = player,
        onVideoRatioChanged = { ratio -> videoRatio = ratio },
        onBufferingChanged = { buffering ->
            isBuffering = buffering
            onBufferingChange(buffering)
        },
        onIsPlayingChanged = { playing ->
            isPlaying = playing
            onIsPlayingChange(playing)
        },
        onPlayerErrorChanged = { errorCode ->
            isBuffering = false
            loadError = errorCode
        },
        onDispose = {
            isBuffering = false
            isPlaying = false
            onBufferingChange(false)
            onIsPlayingChange(false)
        },
    )
    BindGuideVideoForegroundPlaybackGuard(
        player = player,
        onForegroundStopped = {
            isBuffering = false
            isPlaying = false
            onBufferingChange(false)
            onIsPlayingChange(false)
        },
    )

    LaunchedEffect(player, loopEnabled) {
        player?.repeatMode =
            if (loopEnabled) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
    }

    val activePlayer = player
    if (activePlayer == null) {
        LaunchedEffect(normalizedUrl, loadError, onBufferingChange, onIsPlayingChange) {
            isBuffering = false
            isPlaying = false
            onBufferingChange(false)
            onIsPlayingChange(false)
        }
        val error = loadError
        if (error != null) {
            GuideInlineVideoFailureBody(
                videoRatio = videoRatio,
                loadError = error,
                mediaUrl = normalizedUrl,
                backdrop = backdrop,
                onRetry = {
                    loadError = null
                    retryToken += 1
                },
                onCollapse = { onExpandedChange(false) },
            )
        } else {
            GuideInlineVideoUnavailableHint()
        }
        return
    }

    LaunchedEffect(controlActionToken, controlAction, activePlayer) {
        if (controlActionToken <= 0 || controlAction == null) return@LaunchedEffect
        when (controlAction) {
            GuideVideoControlAction.TogglePlayPause -> {
                if (activePlayer.isPlaying) {
                    activePlayer.pause()
                } else {
                    activePlayer.play()
                }
            }
        }
    }

    LaunchedEffect(pictureInPictureRequestToken, activePlayer, normalizedUrl) {
        if (pictureInPictureRequestToken <= 0 || !activePlayer.isPlaying) return@LaunchedEffect
        latestOnPictureInPictureRequest(activePlayer.currentPosition.coerceAtLeast(0L))
        latestOnExpandedChange(false)
    }

    GuideInlineVideoPlayerBody(
        player = activePlayer,
        videoRatio = videoRatio,
        loopEnabled = loopEnabled,
        onToggleLoop = { loopEnabled = !loopEnabled },
        onCollapse = { onExpandedChange(false) },
        onVideoBoundsChanged = onVideoBoundsChanged,
        backdrop = backdrop,
    )
    GuideInlineVideoStatusHints(
        isBuffering = isBuffering,
        loadError = null,
        mediaUrl = normalizedUrl,
        backdrop = backdrop,
    )
}
