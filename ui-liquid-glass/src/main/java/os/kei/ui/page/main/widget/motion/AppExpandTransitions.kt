package os.kei.ui.page.main.widget.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun appExpandIn(): EnterTransition {
    if (!LocalTransitionAnimationsEnabled.current) return EnterTransition.None
    return fadeIn(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeInMs)) +
        expandVertically(
            animationSpec = tween(durationMillis = AppMotionTokens.expandSizeInMs),
            expandFrom = Alignment.Top
        )
}

@Composable
fun appExpandOut(): ExitTransition {
    if (!LocalTransitionAnimationsEnabled.current) return ExitTransition.None
    return fadeOut(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeOutMs)) +
        shrinkVertically(
            animationSpec = tween(durationMillis = AppMotionTokens.expandSizeOutMs),
            shrinkTowards = Alignment.Top
        )
}

@Composable
fun appFloatingEnter(): EnterTransition {
    if (!LocalTransitionAnimationsEnabled.current) return EnterTransition.None
    return fadeIn(animationSpec = tween(durationMillis = 130)) +
        slideInVertically(
            animationSpec = tween(durationMillis = 260),
            initialOffsetY = { fullHeight -> (fullHeight * 0.32f).toInt() }
        )
}

@Composable
fun appFloatingExit(): ExitTransition {
    if (!LocalTransitionAnimationsEnabled.current) return ExitTransition.None
    return fadeOut(animationSpec = tween(durationMillis = 100)) +
        slideOutVertically(
            animationSpec = tween(durationMillis = 220),
            targetOffsetY = { fullHeight -> (fullHeight * 0.36f).toInt() }
        )
}
