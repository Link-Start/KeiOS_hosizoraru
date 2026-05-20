@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v2 Liquid Glass Toast — iOS-style HUD toast positioned at the upper-third of the screen.
 *
 * Design principles:
 * - Positioned at ~30% from the top of the screen (like iOS HUD alerts), not at the very top
 *   or bottom. This avoids obscuring both navigation chrome and content the user is interacting
 *   with, while remaining clearly visible in the user's natural focal area.
 * - Strong liquid glass effect: elevated blur radius, prominent lens distortion, vibrancy tint,
 *   and depth shadow create a clearly frosted-glass pill that floats above the content.
 * - Enters with a spring scale + fade animation for a bouncy, alive feel.
 * - Auto-dismisses after a configurable duration.
 * - Supports an optional leading icon for visual context.
 */

private const val TOAST_ENTER_DURATION_MS = 380
private const val TOAST_EXIT_DURATION_MS = 240
private const val TOAST_DEFAULT_DISPLAY_MS = 2800L
private const val TOAST_LONG_DISPLAY_MS = 4500L

/** Vertical position: fraction from top of screen (0.30 = upper third, iOS HUD style). */
private const val TOAST_VERTICAL_BIAS = -0.40f

/**
 * Toast display duration presets.
 */
enum class LiquidToastDuration(
    internal val durationMs: Long,
) {
    Short(TOAST_DEFAULT_DISPLAY_MS),
    Long(TOAST_LONG_DISPLAY_MS),
}

/**
 * Data class representing a single toast message.
 */
data class LiquidToastData(
    val message: String,
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified,
    val duration: LiquidToastDuration = LiquidToastDuration.Short,
)

/**
 * State holder for [LiquidToastHost]. Create via [rememberLiquidToastState].
 *
 * Supports a simple FIFO queue: if a toast is already showing, new messages are enqueued and
 * displayed sequentially after the current one dismisses. This matches system Toast behavior
 * where rapid calls don't lose messages.
 */
@Stable
class LiquidToastState {
    internal var currentToast by mutableStateOf<LiquidToastData?>(null)
        private set

    private val queue = java.util.concurrent.ConcurrentLinkedQueue<LiquidToastData>()

    /**
     * Show a toast message. If a toast is already showing, the new message is enqueued and will
     * display after the current one dismisses.
     */
    fun show(
        message: String,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short,
    ) {
        val data =
            LiquidToastData(
                message = message,
                icon = icon,
                iconTint = iconTint,
                duration = duration,
            )
        if (currentToast == null) {
            currentToast = data
        } else {
            queue.offer(data)
        }
    }

    /**
     * Dismiss the current toast. If there are queued messages, the next one is shown immediately.
     */
    fun dismiss() {
        currentToast = queue.poll()
    }
}

/**
 * Remember a [LiquidToastState] across recompositions.
 */
@Composable
fun rememberLiquidToastState(): LiquidToastState = remember { LiquidToastState() }

/**
 * Host composable that displays liquid glass toasts at the upper-third of the screen (iOS style).
 *
 * Place this at the root of your page scaffold, overlaying all content. It uses the provided
 * [backdrop] to render the frosted-glass effect against whatever is behind it.
 *
 * @param state The [LiquidToastState] that controls toast visibility.
 * @param backdrop The [Backdrop] to use for the liquid glass effect.
 * @param modifier Modifier for the host container (typically `Modifier.fillMaxSize()`).
 */
@Composable
fun LiquidToastHost(
    state: LiquidToastState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val toast = state.currentToast
    val visibleState = remember { MutableTransitionState(false) }
    var renderedToast by remember { mutableStateOf<LiquidToastData?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            renderedToast = toast
        }
    }
    visibleState.targetState = toast != null

    // Auto-dismiss after duration
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(toast.duration.durationMs)
            state.dismiss()
        }
    }
    LaunchedEffect(
        visibleState.isIdle,
        visibleState.currentState,
        toast,
    ) {
        if (visibleState.isIdle && !visibleState.currentState && toast == null) {
            renderedToast = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        // Vertical bias -0.40 places the toast at roughly 30% from the top — the iOS HUD zone.
        contentAlignment = Alignment.Center,
    ) {
        val screenHeight = LocalWindowInfo.current.containerDpSize.height
        val verticalOffset = screenHeight * TOAST_VERTICAL_BIAS / 2

        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.offset(y = verticalOffset),
            enter =
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                    initialScale = 0.70f,
                ) +
                    fadeIn(
                        animationSpec = tween(TOAST_ENTER_DURATION_MS),
                    ),
            exit =
                scaleOut(
                    animationSpec = tween(TOAST_EXIT_DURATION_MS),
                    targetScale = 0.85f,
                ) +
                    fadeOut(
                        animationSpec = tween(TOAST_EXIT_DURATION_MS),
                    ),
        ) {
            renderedToast?.let { data ->
                LiquidToastContent(
                    backdrop = backdrop,
                    data = data,
                )
            }
        }
    }
}

/**
 * The actual toast pill content with strong liquid glass styling.
 *
 * Key visual differences from a plain surface:
 * - Higher blur radius (12dp vs default 8dp) for a more prominent frosted-glass look
 * - Larger lens radius (32dp vs default 24dp) for visible refraction distortion
 * - Semi-transparent tint color that shifts with dark/light mode
 * - Depth effect + shadow for floating elevation
 * - Slightly larger padding and bolder text for readability against the blurred background
 */
@Composable
private fun LiquidToastContent(
    backdrop: Backdrop,
    data: LiquidToastData,
) {
    // Official Backdrop recommendation: simple semi-transparent white surface overlay.
    // The liquid glass effect comes from lens refraction, not from complex color layering.
    LiquidSurface(
        backdrop = backdrop,
        modifier =
            Modifier
                .widthIn(min = 140.dp, max = 300.dp)
                .padding(horizontal = 16.dp),
        shape = ContinuousCapsule,
        isInteractive = false,
        surfaceColor = Color.White.copy(alpha = 0.5f),
        blurRadius = 4.dp,
        lensRadius = 32.dp,
        chromaticAberration = true,
        depthEffect = true,
        shadow = true,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            data.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(end = 10.dp)
                            .size(20.dp),
                    tint =
                        if (data.iconTint.isSpecified) {
                            data.iconTint
                        } else {
                            MiuixTheme.colorScheme.onBackground.copy(alpha = 0.90f)
                        },
                )
            }
            Text(
                text = data.message,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
