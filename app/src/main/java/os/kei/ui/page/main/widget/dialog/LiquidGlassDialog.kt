package os.kei.ui.page.main.widget.dialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v2 Liquid Glass Dialog — a frosted-glass confirmation dialog with spring scale animation.
 *
 * Replaces Miuix [WindowDialog] when the user enables liquid glass dialogs in settings.
 * Matches the API surface of WindowDialog (show, title, summary, onDismissRequest, content)
 * so migration is a drop-in replacement at the routing layer.
 *
 * Design:
 * - Centered card with rounded corners and semi-transparent glass surface
 * - Spring scale-in animation (0.85 → 1.0) for a bouncy, alive entrance
 * - Scrim dim behind the dialog
 * - Title + summary + custom content slot (typically action buttons)
 */

private val LiquidDialogCornerRadius = 24.dp
private val LiquidDialogMaxWidth = 320.dp
private const val LiquidDialogScrimAlpha = 0.38f

@Composable
fun LiquidGlassDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    dismissible: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    if (!show) return

    val scale by animateFloatAsState(
        targetValue = if (show) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
        label = "liquid_glass_dialog_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 600f),
        label = "liquid_glass_dialog_alpha"
    )

    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismissRequest?.invoke()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Remove system's default dim background — we draw our own scrim.
        top.yukonga.miuix.kmp.utils.RemovePlatformDialogDefaultEffects()

        val dialogShape = RoundedRectangle(LiquidDialogCornerRadius)
        // Official Backdrop recommendation: simple semi-transparent white surface.
        val surfaceColor = Color.White.copy(alpha = 0.5f)
        val titleColor = MiuixTheme.colorScheme.onBackground
        val summaryColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f)

        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = LiquidDialogScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissible
                ) {
                    onDismissRequest?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            // Dialog card
            Column(
                modifier = Modifier
                    .widthIn(max = LiquidDialogMaxWidth)
                    .fillMaxWidth(0.85f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(dialogShape)
                    .background(surfaceColor, dialogShape)
                    // Block clicks from passing through to scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Summary
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        color = summaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Custom content (typically action buttons)
                content()
            }
        }
    }
}
