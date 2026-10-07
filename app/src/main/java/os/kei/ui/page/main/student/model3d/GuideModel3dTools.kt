@file:Suppress("FunctionName")
package os.kei.ui.page.main.student.model3d

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.settings.section.SettingsLiquidKeyPointSlider
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.*
import os.kei.ui.page.main.widget.sheet.*
import top.yukonga.miuix.kmp.basic.Text

/** Only this leaf reads the periodically sampled position; the WebView/chrome are not rebuilt per tick. */
@Composable
internal fun GuideModel3dTimeline(backdrop: Backdrop, position: FloatState, duration: FloatState, enabled: Boolean,
    onScrubbing: (Boolean) -> Unit, onSeek: (Float) -> Unit,
) {
    var preview by remember { mutableStateOf<Float?>(null) }
    val total = duration.floatValue
    val current = preview?.times(total) ?: position.floatValue
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.guide_model_3d_position_value, current, total), color = Color.White,
            fontSize = AppTypographyTokens.Supporting.fontSize, modifier = Modifier.padding(horizontal = 8.dp))
        LiquidMusicProgressSlider(
            value = { preview ?: if (total > 0) (position.floatValue / total).coerceIn(0f, 1f) else 0f },
            onValueChange = { preview = it }, onValueChangeFinished = { onSeek(it * total); preview = null },
            onInteractionChanged = { onScrubbing(it) }, valueRange = 0f..1f, visibilityThreshold = 0.001f,
            backdrop = backdrop, enabled = enabled && total > 0,
            contentDescription = stringResource(R.string.guide_model_3d_position),
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun GuideModel3dToolsSheet(show: Boolean, onDismiss: () -> Unit, hasAnimation: Boolean,
    speed: Float, loop: Boolean, outline: Boolean, outlineWidth: Float,
    onSpeed: (Float) -> Unit, onLoop: (Boolean) -> Unit, onOutline: (Boolean) -> Unit, onOutlineWidth: (Float) -> Unit,
    onRetry: () -> Unit,
) {
    SnapshotWindowBottomSheet(show = show, title = stringResource(R.string.guide_model_3d_tools), onDismissRequest = onDismiss) {
        SheetContentColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalSpacing = 12.dp) {
            SheetSurfaceCard {
                SheetSectionHeader(stringResource(R.string.guide_model_3d_speed),
                    summary = stringResource(R.string.guide_model_3d_percent, (speed * 100).toInt()))
                SettingsLiquidKeyPointSlider(speed, onSpeed, 0.25f..2f, listOf(0.25f, 0.5f, 1f, 1.5f, 2f), 0.07f, hasAnimation,
                    contentDescription = stringResource(R.string.guide_model_3d_speed))
                val loopLabel = stringResource(R.string.guide_model_3d_loop)
                SheetControlRow(label = loopLabel) { AppSwitch(checked = loop, onCheckedChange = onLoop, enabled = hasAnimation,
                    modifier = Modifier.semantics { contentDescription = loopLabel }) }
            }
            SheetSurfaceCard {
                val outlineLabel = stringResource(R.string.guide_model_3d_outline)
                SheetControlRow(label = outlineLabel) { AppSwitch(checked = outline, onCheckedChange = onOutline,
                    modifier = Modifier.semantics { contentDescription = outlineLabel }) }
                SheetSectionHeader(stringResource(R.string.guide_model_3d_outline_width),
                    summary = stringResource(R.string.guide_model_3d_percent, (outlineWidth * 100).toInt()))
                LiquidVolumeSlider(value = { outlineWidth }, onValueChange = onOutlineWidth, valueRange = 0f..1f,
                    visibilityThreshold = 0.001f, backdrop = LocalLiquidParentBackdrop.current, enabled = outline,
                    contentDescription = stringResource(R.string.guide_model_3d_outline_width), modifier = Modifier.fillMaxWidth().height(48.dp))
            }
            AppStandaloneLiquidTextButton(text = stringResource(R.string.guide_action_retry), onClick = onRetry,
                leadingIcon = appLucideRefreshIcon(), variant = GlassVariant.SheetAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
