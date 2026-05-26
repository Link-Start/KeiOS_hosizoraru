@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.student.GuideMediaProgressState
import os.kei.ui.page.main.student.GuideRemoteImageAdaptive
import os.kei.ui.page.main.widget.shape.appSquircleClip
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Replace

@Composable
internal fun GuideInlineVideoPreview(
    previewImageUrl: String,
    onOpenFullscreen: () -> Unit,
    previewProgressState: GuideMediaProgressState?,
    onPreviewLoadingChanged: ((Boolean) -> Unit)?
) {
    if (previewImageUrl.isNotBlank()) {
        Box(
            modifier = Modifier.clickable { onOpenFullscreen() }
        ) {
            GuideRemoteImageAdaptive(
                imageUrl = previewImageUrl,
                progressState = previewProgressState,
                onLoadingChanged = onPreviewLoadingChanged
            )
        }
    } else {
        LaunchedEffect(previewProgressState, onPreviewLoadingChanged) {
            previewProgressState?.set(1f)
            onPreviewLoadingChanged?.invoke(false)
        }
    }
}

@Composable
internal fun GuideInlineVideoPlayerBody(
    player: Player,
    videoRatio: Float,
    loopEnabled: Boolean,
    onToggleLoop: () -> Unit,
    onCollapse: () -> Unit,
    backdrop: Backdrop?
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(videoRatio)
            .appSquircleClip(14.dp),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = "",
            leadingIcon = MiuixIcons.Regular.Replace,
            textColor = if (loopEnabled) Color(0xFF34C759) else Color(0xFF3B82F6),
            variant = GlassVariant.Compact,
            onClick = onToggleLoop
        )
        AppLiquidTextButton(
            backdrop = backdrop,
            text = "",
            leadingIcon = MiuixIcons.Regular.ExpandLess,
            textColor = Color(0xFF3B82F6),
            variant = GlassVariant.Compact,
            onClick = onCollapse
        )
    }
}
