package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
 * v2 Liquid Glass Toast — a modern, iOS-style toast positioned at the upper-center of the screen.
 *
 * Design principles:
 * - Positioned at the top (below status bar) like iOS Dynamic Island notifications, so it doesn't
 *   obscure content the user is interacting with at the bottom of the screen.
 * - Uses the Backdrop library's liquid glass effect for a frosted-glass appearance that blends
 *   with the content behind it.
 * - Enters with a spring-like scale + slide animation, exits with fade + slide up.
 * - Auto-dismisses after a configurable duration.
 * - Supports an optional leading icon for visual context.
 *
 * Usage:
 * ```kotlin
 * val toastState = rememberLiquidToastState()
 *
 * // In your scaffold/root composable:
 * LiquidToastHost(
 *     state = toastState,
 *     backdrop = backdrops.content // from your page's backdrop set
 * )
 *
 * // To show a toast:
 * toastState.show("Operation completed", icon = lucideCheckIcon())
 * ```
 */

private const val TOAST_ENTER_DURATION_MS = 340
private const val TOAST_EXIT_DURATION_MS = 260
private const val TOAST_DEFAULT_DISPLAY_MS = 2800L
private const val TOAST_LONG_DISPLAY_MS = 4500L

/**
 * Toast display duration presets.
 */
enum class LiquidToastDuration(internal val durationMs: Long) {
    Short(TOAST_DEFAULT_DISPLAY_MS),
    Long(TOAST_LONG_DISPLAY_MS)
}

/**
 * Data class representing a single toast message.
 */
data class LiquidToastData(
    val message: String,
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified,
    val duration: LiquidToastDuration = LiquidToastDuration.Short
)

/**
 * State holder for [LiquidToastHost]. Create via [rememberLiquidToastState].
 */
@Stable
class LiquidToastState {
    internal var currentToast by mutableStateOf<LiquidToastData?>(null)
        private set

    /**
     * Show a toast message. If a toast is already showing, it will be replaced immediately.
     */
    fun show(
        message: String,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short
    ) {
        currentToast = LiquidToastData(
            message = message,
            icon = icon,
            iconTint = iconTint,
            duration = duration
        )
    }

    /**
     * Dismiss the current toast immediately.
     */
    fun dismiss() {
        currentToast = null
    }
}

/**
 * Remember a [LiquidToastState] across recompositions.
 */
@Composable
fun rememberLiquidToastState(): LiquidToastState {
    return remember { LiquidToastState() }
}

/**
 * Host composable that displays liquid glass toasts at the upper-center of the screen.
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
    modifier: Modifier = Modifier
) {
    val toast = state.currentToast
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = toast != null

    // Auto-dismiss after duration
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(toast.duration.durationMs)
            state.dismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                animationSpec = tween(TOAST_ENTER_DURATION_MS),
                initialOffsetY = { -it }
            ) + fadeIn(
                animationSpec = tween(TOAST_ENTER_DURATION_MS)
            ) + scaleIn(
                animationSpec = tween(TOAST_ENTER_DURATION_MS),
                initialScale = 0.85f
            ),
            exit = slideOutVertically(
                animationSpec = tween(TOAST_EXIT_DURATION_MS),
                targetOffsetY = { -it / 2 }
            ) + fadeOut(
                animationSpec = tween(TOAST_EXIT_DURATION_MS)
            ) + scaleOut(
                animationSpec = tween(TOAST_EXIT_DURATION_MS),
                targetScale = 0.90f
            )
        ) {
            toast?.let { data ->
                LiquidToastContent(
                    backdrop = backdrop,
                    data = data
                )
            }
        }
    }
}

/**
 * The actual toast pill content with liquid glass styling.
 */
@Composable
private fun LiquidToastContent(
    backdrop: Backdrop,
    data: LiquidToastData
) {
    LiquidSurface(
        backdrop = backdrop,
        modifier = Modifier
            .widthIn(min = 120.dp, max = 320.dp)
            .padding(horizontal = 24.dp),
        shape = ContinuousCapsule,
        isInteractive = false,
        blurRadius = UiPerformanceBudget.backdropBlur,
        lensRadius = UiPerformanceBudget.backdropLens,
        shadow = true,
        depthEffect = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            data.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    tint = if (data.iconTint.isSpecified) {
                        data.iconTint
                    } else {
                        MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    }
                )
            }
            Text(
                text = data.message,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
