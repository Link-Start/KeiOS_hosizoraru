package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.status.AppStatusColors

@Composable
fun rememberAppFloatingKeyboardLift(
    focusedLift: Dp = 18.dp,
    restingBottomGap: Dp = 0.dp,
    label: String = "app_floating_keyboard_lift"
): Dp {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val targetLift = appFloatingKeyboardLiftTarget(
        imeBottom = imeBottom,
        navigationBottom = navigationBottom,
        focusedLift = focusedLift,
        restingBottomGap = restingBottomGap
    )
    val lift by animateDpAsState(
        targetValue = targetLift,
        animationSpec = tween(durationMillis = AppFloatingKeyboardLiftMotionMs),
        label = label
    )
    return lift
}

internal fun appFloatingKeyboardLiftTarget(
    imeBottom: Dp,
    navigationBottom: Dp,
    focusedLift: Dp,
    restingBottomGap: Dp = 0.dp
): Dp {
    if (imeBottom <= navigationBottom) return 0.dp
    return (imeBottom + focusedLift - restingBottomGap).coerceAtLeast(focusedLift)
}

internal fun appFloatingVerticalDockHeight(
    itemSize: Dp,
    itemCount: Int
): Dp {
    if (itemCount <= 0) return 0.dp
    return itemSize * itemCount
}

@Composable
internal fun appFloatingRefreshTint(
    status: AppFloatingRefreshStatus,
    enabled: Boolean,
    neutral: Color,
    muted: Color,
    success: Color,
    danger: Color,
    active: Color
): Color {
    return when (status) {
        AppFloatingRefreshStatus.Refreshing -> AppStatusColors.Refreshing
        AppFloatingRefreshStatus.Success -> success
        AppFloatingRefreshStatus.Danger -> danger
        AppFloatingRefreshStatus.Cached -> AppStatusColors.Cached
        AppFloatingRefreshStatus.Idle -> if (enabled) neutral else muted
    }
}

private const val AppFloatingKeyboardLiftMotionMs = 160
