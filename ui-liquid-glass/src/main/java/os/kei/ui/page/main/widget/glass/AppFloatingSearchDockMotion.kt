@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration

internal enum class AppFloatingVerticalSearchDockMode {
    FullDock,
    FullSearch,
    CompactButton,
    CompactSearch,
}

@Composable
internal fun rememberAppFloatingVerticalSearchDockMotion(
    mode: AppFloatingVerticalSearchDockMode,
    dockSide: AppFloatingDockSide,
    expandedHeight: Dp,
    compactHeight: Dp,
    availableWidth: Dp,
    fullSearchFieldWidth: Dp,
    compactSearchFieldWidth: Dp,
    compactSearchReservedWidth: Dp,
    size: Dp,
    gap: Dp,
): AppFloatingVerticalSearchDockMotion {
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val transition = updateTransition(targetState = mode, label = "app_vertical_floating_search_mode")
    val widthState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockWidthMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_width",
        ) { targetMode ->
            if (targetMode.searchVisible) availableWidth else size
        }
    val heightState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_height",
        ) { targetMode ->
            if (targetMode.fullDockVisible) expandedHeight else compactHeight
        }
    val fieldWidthState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockWidthMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_field_width",
        ) { targetMode ->
            when (targetMode) {
                AppFloatingVerticalSearchDockMode.FullSearch -> fullSearchFieldWidth
                AppFloatingVerticalSearchDockMode.CompactSearch -> compactSearchFieldWidth
                AppFloatingVerticalSearchDockMode.FullDock,
                AppFloatingVerticalSearchDockMode.CompactButton,
                -> 0.dp
            }
        }
    val fieldXState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockWidthMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_field_x",
        ) { targetMode ->
            when (targetMode) {
                AppFloatingVerticalSearchDockMode.CompactSearch -> compactSearchReservedWidth
                AppFloatingVerticalSearchDockMode.FullSearch ->
                    if (dockSide == AppFloatingDockSide.Start) size + gap else 0.dp
                AppFloatingVerticalSearchDockMode.FullDock,
                AppFloatingVerticalSearchDockMode.CompactButton,
                -> if (dockSide == AppFloatingDockSide.Start) size + gap else 0.dp
            }
        }
    val fullDockXState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockWidthMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_full_dock_x",
        ) { targetMode ->
            when (targetMode) {
                AppFloatingVerticalSearchDockMode.FullSearch ->
                    if (dockSide == AppFloatingDockSide.End) availableWidth - size else 0.dp
                AppFloatingVerticalSearchDockMode.FullDock,
                AppFloatingVerticalSearchDockMode.CompactButton,
                AppFloatingVerticalSearchDockMode.CompactSearch,
                -> 0.dp
            }
        }
    val fieldAlphaState =
        transition.animateFloat(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockFadeMotionMs,
                            animationsEnabled,
                        ),
                )
            },
            label = "app_vertical_floating_search_mode_field_alpha",
        ) { targetMode ->
            if (targetMode.searchVisible) 1f else 0f
        }
    val fullDockProgressState =
        transition.animateFloat(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_full_progress",
        ) { targetMode ->
            if (targetMode.fullDockVisible) 1f else 0f
        }
    val compactButtonProgressState =
        transition.animateFloat(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_compact_progress",
        ) { targetMode ->
            if (targetMode == AppFloatingVerticalSearchDockMode.CompactButton) 1f else 0f
        }
    val compactSearchProgressState =
        transition.animateFloat(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            VerticalSearchDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "app_vertical_floating_search_mode_compact_search_progress",
        ) { targetMode ->
            if (targetMode == AppFloatingVerticalSearchDockMode.CompactSearch) 1f else 0f
        }
    val widthProvider = remember(widthState) { { widthState.value } }
    val heightProvider = remember(heightState) { { heightState.value } }
    val fieldWidthProvider = remember(fieldWidthState) { { fieldWidthState.value } }
    val fieldXProvider = remember(fieldXState) { { fieldXState.value } }
    val fullDockXProvider = remember(fullDockXState) { { fullDockXState.value } }
    val fieldAlphaProvider = remember(fieldAlphaState) { { fieldAlphaState.value } }
    val fullDockProgressProvider = remember(fullDockProgressState) { { fullDockProgressState.value } }
    val compactButtonProgressProvider =
        remember(compactButtonProgressState) { { compactButtonProgressState.value } }
    val compactSearchProgressProvider =
        remember(compactSearchProgressState) { { compactSearchProgressState.value } }
    return AppFloatingVerticalSearchDockMotion(
        width = widthProvider,
        height = heightProvider,
        fieldWidth = fieldWidthProvider,
        fieldX = fieldXProvider,
        fullDockX = fullDockXProvider,
        fieldAlpha = fieldAlphaProvider,
        showFieldContent = transition.currentState.searchVisible || transition.targetState.searchVisible,
        showFullDockContent = transition.currentState.fullDockVisible || transition.targetState.fullDockVisible,
        showCompactButtonContent =
            transition.currentState == AppFloatingVerticalSearchDockMode.CompactButton ||
                transition.targetState == AppFloatingVerticalSearchDockMode.CompactButton,
        showCompactSearchButtonContent =
            transition.currentState == AppFloatingVerticalSearchDockMode.CompactSearch ||
                transition.targetState == AppFloatingVerticalSearchDockMode.CompactSearch,
        fullDockModifier =
            Modifier.graphicsLayer {
                val progress = fullDockProgressProvider()
                alpha = progress
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.90f + 0.10f * progress
            },
        compactButtonModifier =
            Modifier.graphicsLayer {
                val progress = compactButtonProgressProvider()
                alpha = progress
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.88f + 0.12f * progress
            },
        compactSearchButtonModifier =
            Modifier.graphicsLayer {
                val progress = compactSearchProgressProvider()
                alpha = progress
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.88f + 0.12f * progress
            },
    )
}

private val AppFloatingVerticalSearchDockMode.searchVisible: Boolean
    get() =
        this == AppFloatingVerticalSearchDockMode.FullSearch ||
            this == AppFloatingVerticalSearchDockMode.CompactSearch

private val AppFloatingVerticalSearchDockMode.fullDockVisible: Boolean
    get() =
        this == AppFloatingVerticalSearchDockMode.FullDock ||
            this == AppFloatingVerticalSearchDockMode.FullSearch

internal data class AppFloatingVerticalSearchDockMotion(
    val width: () -> Dp,
    val height: () -> Dp,
    val fieldWidth: () -> Dp,
    val fieldX: () -> Dp,
    val fullDockX: () -> Dp,
    val fieldAlpha: () -> Float,
    val showFieldContent: Boolean,
    val showFullDockContent: Boolean,
    val showCompactButtonContent: Boolean,
    val showCompactSearchButtonContent: Boolean,
    val fullDockModifier: Modifier,
    val compactButtonModifier: Modifier,
    val compactSearchButtonModifier: Modifier,
)

private const val VerticalSearchDockCompactMotionMs = 240
private const val VerticalSearchDockWidthMotionMs = 220
private const val VerticalSearchDockFadeMotionMs = 120
