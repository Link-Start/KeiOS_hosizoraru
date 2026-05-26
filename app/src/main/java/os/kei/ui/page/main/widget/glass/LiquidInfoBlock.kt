@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidInfoBlock(
    backdrop: Backdrop?,
    title: String,
    subtitle: String,
    body: String = "",
    accent: Color,
    content: (@Composable () -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val cardSurface =
        if (isDark) {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f)
        } else {
            Color.White.copy(alpha = 0.66f)
        }
    val cornerRadius = 16.dp
    if (backdrop != null) {
        LiquidInfoBlockSurface(
            backdrop = backdrop,
            captureBackdrop = null,
            title = title,
            subtitle = subtitle,
            body = body,
            accent = accent,
            content = content,
            cardSurface = cardSurface,
            cornerRadius = cornerRadius,
        )
    } else {
        val localBackdrop = rememberLayerBackdrop()
        LiquidInfoBlockSurface(
            backdrop = localBackdrop,
            captureBackdrop = localBackdrop,
            title = title,
            subtitle = subtitle,
            body = body,
            accent = accent,
            content = content,
            cardSurface = cardSurface,
            cornerRadius = cornerRadius,
        )
    }
}

@Composable
private fun LiquidInfoBlockSurface(
    backdrop: Backdrop,
    captureBackdrop: LayerBackdrop?,
    title: String,
    subtitle: String,
    body: String,
    accent: Color,
    content: (@Composable () -> Unit)?,
    cardSurface: Color,
    cornerRadius: Dp,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (captureBackdrop != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(captureBackdrop),
            )
        }
        LiquidSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedRectangle(cornerRadius),
            isInteractive = false,
            surfaceColor = cardSurface,
            blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Content),
            lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Content),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier.then(
                                Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                            ),
                    ) {
                        LiquidSurface(
                            backdrop = backdrop,
                            modifier = Modifier,
                            shape = RoundedRectangle(999.dp),
                            isInteractive = false,
                            surfaceColor = accent.copy(alpha = 0.18f),
                            blurRadius = 4.dp,
                            lensRadius = 18.dp,
                            shadow = false,
                        ) {
                            Text(
                                text = title,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (content != null) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        content()
                    }
                } else if (body.isNotBlank()) {
                    Text(
                        text = body,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
