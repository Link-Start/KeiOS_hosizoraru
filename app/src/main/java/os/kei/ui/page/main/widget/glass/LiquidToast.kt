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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

/** Animation durations (millis) — kept as Int because Compose's tween() takes Int millis. */
private const val TOAST_ENTER_DURATION_MS = 380
private const val TOAST_EXIT_DURATION_MS = 240

/** Default on-screen time for a Short / Long toast. */
private val TOAST_DEFAULT_DISPLAY = 2800.milliseconds
private val TOAST_LONG_DISPLAY = 4500.milliseconds

/** Shortened on-screen time used while a backlog is waiting, so newer toasts surface sooner. */
private val TOAST_BACKLOG_DISPLAY = 1400.milliseconds

/**
 * Hard floor for the expedited display time. Backlog acceleration must never drop a toast below
 * the time it physically takes to animate in, settle, and animate out — otherwise a burst would
 * flash unreadable blips. enter(380) + exit(240) + a ~480ms settle/read margin.
 */
private val TOAST_MIN_VISIBLE = 1100.milliseconds

/** Timer poll interval so a backlog appearing mid-display can still expedite the current toast. */
private val TOAST_TIMER_TICK = 250.milliseconds

/** At most this many toasts are shown stacked at once; the rest are queued. */
private const val MAX_VISIBLE_TOASTS = 2

/** Upper bound on queued toasts so a runaway burst can't backlog into a multi-minute replay. */
private const val MAX_TOAST_QUEUE = 4

/** Vertical anchor of the toast stack: fraction from the top of the screen (iOS HUD zone). */
private const val TOAST_TOP_FRACTION = 0.28f

/**
 * Toast display duration presets.
 */
enum class LiquidToastDuration(
    internal val duration: Duration,
) {
    Short(TOAST_DEFAULT_DISPLAY),
    Long(TOAST_LONG_DISPLAY),
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
 * A single display slot: the toast payload plus a monotonic, always-unique [token].
 *
 * The token is what the host keys its state notifications and auto-dismiss timer on. Keying on the
 * [LiquidToastData] value (a data class) was the original bug: two identical messages compare
 * equal, so promoting an equal-valued queued item neither triggered a Compose state change nor
 * restarted the dismiss timer — leaving the toast stuck on screen forever.
 */
@Stable
internal data class LiquidToastSlot(
    val data: LiquidToastData,
    val token: Long,
)

/**
 * State holder for [LiquidToastHost]. Create via [rememberLiquidToastState].
 *
 * Display model:
 * - Up to [MAX_VISIBLE_TOASTS] toasts are shown stacked at once (each in its own slot/position).
 * - Further toasts wait in a FIFO [queue] (capped at [MAX_TOAST_QUEUE]); when a visible toast
 *   dismisses, the next queued one is promoted into its place.
 * - Identical messages already visible (or just queued) are collapsed so a double-tap doesn't
 *   replay the same toast.
 *
 * All mutations are guarded by [lock] so concurrent `show`/`dismiss` from different threads
 * (ViewModels, coroutines, the main thread) can't interleave into an inconsistent state.
 */
@Stable
class LiquidToastState {
    private val lock = Any()

    internal var visibleSlots by mutableStateOf<List<LiquidToastSlot>>(emptyList())
        private set

    private val queue = ArrayDeque<LiquidToastSlot>()
    private var tokenSeq = 0L

    private fun nextToken(): Long = ++tokenSeq

    /** True when more toasts are waiting behind the currently visible ones. */
    internal val hasBacklog: Boolean
        get() = synchronized(lock) { queue.isNotEmpty() }

    /**
     * Show a toast message. If the visible stack is full, the message is enqueued and promoted
     * when a slot frees up.
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
        synchronized(lock) {
            // Collapse a repeated identical message (already on screen or last queued) so a
            // double-tap doesn't stack/replay the same toast.
            if (visibleSlots.any { it.data == data } || queue.lastOrNull()?.data == data) {
                return
            }
            val slot = LiquidToastSlot(data = data, token = nextToken())
            if (visibleSlots.size < MAX_VISIBLE_TOASTS) {
                visibleSlots = visibleSlots + slot
            } else {
                // Favor newer toasts: when the queue is full, drop the oldest still-waiting one to
                // make room, so the most recent message is the one that eventually surfaces.
                if (queue.size >= MAX_TOAST_QUEUE) {
                    queue.removeFirstOrNull()
                }
                queue.addLast(slot)
            }
        }
    }

    /**
     * Dismiss a specific visible toast by token, promoting a queued one into its place if any.
     * Called by the host once a toast's exit animation completes. Each promoted slot carries a
     * fresh unique token, so the host reliably arms a new timer for it.
     */
    internal fun dismiss(token: Long) {
        synchronized(lock) {
            val remaining = visibleSlots.filterNot { it.token == token }
            if (remaining.size == visibleSlots.size) return // already gone
            val next = queue.removeFirstOrNull()
            visibleSlots = if (next != null) remaining + next else remaining
        }
    }

    /** Clear every toast immediately (e.g. on navigation away). Public convenience. */
    fun dismissAll() {
        synchronized(lock) {
            queue.clear()
            visibleSlots = emptyList()
        }
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
    val slots = state.visibleSlots

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val topPadding = maxHeight * TOAST_TOP_FRACTION
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // key() per token keeps each toast's own AnimatedVisibility / timer state stable as the
            // list mutates, so promoting a queued toast never disturbs the one still on screen.
            slots.forEach { slot ->
                key(slot.token) {
                    LiquidToastStackItem(
                        slot = slot,
                        backdrop = backdrop,
                        // Read backlog live inside the timer so a backlog appearing mid-display can
                        // expedite this toast (shorten only, never extend).
                        hasBacklog = { state.hasBacklog },
                        onDismiss = { state.dismiss(slot.token) },
                    )
                }
            }
        }
    }
}

/**
 * A single toast within the stack. Owns its enter animation, timed wait, and exit animation, then
 * calls [onDismiss] only after the exit completes so the slot is released cleanly (which lets the
 * state promote any queued toast into its place).
 */
@Composable
private fun LiquidToastStackItem(
    slot: LiquidToastSlot,
    backdrop: Backdrop,
    hasBacklog: () -> Boolean,
    onDismiss: () -> Unit,
) {
    // Starts false so the first composition animates the enter transition in.
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true

    // Accessibility-aware base duration. Mirrors what Compose's own Snackbar does: when a screen
    // reader / accessibility service is active, calculateRecommendedTimeoutMillis EXTENDS the time
    // so low-vision and TalkBack users can finish reading.
    val accessibilityManager = LocalAccessibilityManager.current
    val baseDisplay =
        remember(slot.token, accessibilityManager) {
            val original = slot.data.duration.duration
            val recommendedMs =
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = original.inWholeMilliseconds,
                    containsIcons = slot.data.icon != null,
                    containsText = true,
                    containsControls = false,
                )
            recommendedMs?.milliseconds ?: original
        }
    // Only accelerate a backlog when no accessibility service is driving the timeout — never rush a
    // toast away from a user who relies on assistive tech.
    val accelerationAllowed =
        remember(accessibilityManager) {
            accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = TOAST_DEFAULT_DISPLAY.inWholeMilliseconds,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            ) == TOAST_DEFAULT_DISPLAY.inWholeMilliseconds
        }

    // Auto-dismiss timer. Keyed only on the unique token (always distinct), so a repeated identical
    // message can never collide here — this is what fixes the "stuck toast" bug. The backlog check
    // happens inside via elapsed time so a late-arriving backlog can shorten, but never extend, the
    // remaining on-screen time (re-keying would restart the timer and prolong it).
    val shownAt = remember(slot.token) { System.currentTimeMillis().milliseconds }
    LaunchedEffect(slot.token) {
        while (visibleState.targetState) {
            val elapsed = System.currentTimeMillis().milliseconds - shownAt
            val limit = resolveToastDisplayLimit(
                base = baseDisplay,
                expedited = accelerationAllowed && hasBacklog(),
            )
            val remaining = limit - elapsed
            if (remaining <= Duration.ZERO) {
                visibleState.targetState = false
                break
            }
            // Wake periodically so a backlog that appears mid-display can still expedite this toast.
            delay(minOf(remaining, TOAST_TIMER_TICK))
        }
    }

    // Once the exit animation has fully played out, release the slot.
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
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
        LiquidToastContent(
            backdrop = backdrop,
            data = slot.data,
        )
    }
}

/**
 * Resolve how long a toast should stay on screen.
 *
 * - Not expedited (no backlog, or accessibility-driven): use [base] verbatim.
 * - Expedited: shorten toward [TOAST_BACKLOG_DISPLAY] so newer toasts surface sooner, but clamp to
 *   [TOAST_MIN_VISIBLE] so a burst can never flash a toast away before it can be read. If [base] is
 *   itself shorter than the backlog target (unusual), it is respected — acceleration only shortens.
 *
 * Pure function (no Compose/Android deps) so the burst-vs-readability tradeoff is unit-testable.
 */
internal fun resolveToastDisplayLimit(
    base: Duration,
    expedited: Boolean,
): Duration {
    if (!expedited) return base
    return minOf(base, TOAST_BACKLOG_DISPLAY).coerceAtLeast(TOAST_MIN_VISIBLE)
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
