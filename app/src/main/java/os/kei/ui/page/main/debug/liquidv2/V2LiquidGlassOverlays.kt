package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BoxScope.V2GlassSheet(
    visible: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    dismissOnScrim: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.18f),
    showDragHandle: Boolean = true,
    maxWidth: Dp = 640.dp,
    actions: (@Composable RowScope.(LayerBackdrop) -> Unit)? = null,
    content: @Composable (LayerBackdrop) -> Unit
) {
    if (!visible) return
    val palette = rememberV2LiquidGlassPalette()
    if (scrimColor.alpha > 0f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissOnScrim && onDismiss != null,
                    onClick = { onDismiss?.invoke() }
                )
        )
    }
    val sheetBackdrop = rememberLayerBackdrop()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .padding(16.dp),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(34.dp),
            surfaceColor = palette.panelTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong,
            chromaticAberration = true,
            materialStyle = V2LiquidMaterialStyle.Regular,
            readabilityProfile = V2LiquidReadabilityProfile.RegularText,
            backdropPolicy = V2GlassBackdropPolicy.ExportChild
        ),
        exportedBackdrop = sheetBackdrop,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showDragHandle) {
                V2GlassSurface(
                    backdrop = sheetBackdrop,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(44.dp)
                        .height(5.dp),
                    spec = V2GlassSurfaceSpec.capsule(
                        surfaceColor = palette.secondary.copy(alpha = 0.24f),
                        interactive = false
                    ).copy(
                        blur = V2LiquidGlassTokens.blurSoft,
                        lensHeight = 6.dp,
                        lensAmount = 8.dp,
                        chromaticAberration = false
                    )
                )
            }
            content(sheetBackdrop)
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions(sheetBackdrop)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.V2GlassDialog(
    visible: Boolean,
    backdrop: Backdrop,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    dismissOnScrim: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.26f),
    closeAction: (@Composable RowScope.(LayerBackdrop) -> Unit)? = null
) {
    if (!visible) return
    val palette = rememberV2LiquidGlassPalette()
    val dialogBackdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissOnScrim,
                onClick = onDismiss
            )
    )
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .align(Alignment.Center)
            .padding(28.dp)
            .fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(30.dp),
            surfaceColor = palette.panelTint,
            blur = V2LiquidGlassTokens.blurStrong,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong,
            materialStyle = V2LiquidMaterialStyle.Regular,
            readabilityProfile = V2LiquidReadabilityProfile.RegularText,
            clearDimmingAlpha = 0.18f,
            backgroundReadability = 0.28f,
            rimLightAlpha = 0.30f,
            backdropPolicy = V2GlassBackdropPolicy.ExportChild
        ),
        exportedBackdrop = dialogBackdrop,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    color = palette.content,
                    fontSize = AppTypographyTokens.SectionTitle.fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (closeAction != null) {
                    Row(content = { closeAction(dialogBackdrop) })
                }
            }
            Text(
                text = message,
                color = palette.secondary,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondaryLabel != null && onSecondary != null) {
                    V2GlassButton(
                        text = secondaryLabel,
                        backdrop = dialogBackdrop,
                        size = V2GlassControlSize.Compact,
                        onClick = onSecondary
                    )
                }
                V2GlassButton(
                    text = confirmLabel,
                    backdrop = dialogBackdrop,
                    size = V2GlassControlSize.Compact,
                    role = V2GlassRole.Accent,
                    selected = true,
                    onClick = onDismiss
                )
            }
        }
    }
}
