@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play

@Composable
internal fun GuideGalleryVideoGroupHeaderActions(
    itemsSize: Int,
    optionLabels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    displayMediaUrl: String,
    saveTargetUrl: String,
    videoInlineExpanded: Boolean,
    videoInlinePlaying: Boolean,
    backdrop: Backdrop?,
    onToggleInlinePlay: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onSaveMedia: () -> Unit,
) {
    val fullscreenIcon = appLucideFullscreenIcon()
    var showPicker by remember(itemsSize, optionLabels) { mutableStateOf(false) }
    if (itemsSize > 1) {
        AppDropdownSelector(
            selectedText =
                optionLabels.getOrElse(selectedIndex) {
                    stringResource(R.string.guide_gallery_video_format, 1)
                },
            options = optionLabels,
            selectedIndex = selectedIndex,
            expanded = showPicker,
            onExpandedChange = { showPicker = it },
            onSelectedIndexChange = onSelectedIndexChange,
            backdrop = backdrop,
            textColor = Color(0xFF3B82F6),
            variant = GlassVariant.Compact,
        )
    }

    if (displayMediaUrl.isNotBlank()) {
        AppLiquidIconButton(
            backdrop = backdrop,
            icon =
                if (videoInlineExpanded && videoInlinePlaying) {
                    MiuixIcons.Regular.Pause
                } else {
                    MiuixIcons.Regular.Play
                },
            contentDescription =
                stringResource(
                    if (videoInlineExpanded && videoInlinePlaying) {
                        R.string.guide_action_pause
                    } else {
                        R.string.guide_action_play
                    },
                ),
            width = 36.dp,
            height = 36.dp,
            variant = GlassVariant.Compact,
            iconTint = Color(0xFF3B82F6),
            onClick = onToggleInlinePlay,
        )
        AppLiquidIconButton(
            backdrop = backdrop,
            icon = fullscreenIcon,
            contentDescription = stringResource(R.string.guide_action_fullscreen),
            width = 36.dp,
            height = 36.dp,
            variant = GlassVariant.Compact,
            iconTint = Color(0xFF3B82F6),
            onClick = onOpenFullscreen,
        )
    }

    if (saveTargetUrl.isNotBlank()) {
        AppLiquidIconButton(
            backdrop = backdrop,
            icon = MiuixIcons.Regular.Download,
            contentDescription = stringResource(R.string.common_download),
            width = 36.dp,
            height = 36.dp,
            variant = GlassVariant.Compact,
            iconTint = Color(0xFF3B82F6),
            onClick = onSaveMedia,
        )
    }
}
