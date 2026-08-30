@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.snapChromeTranslationPx
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import os.kei.ui.testing.KeiOsTestTags

private const val CompactBottomBarMotionMs = 240

@Composable
internal fun CompactBottomBarDock(
    backdrop: Backdrop?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AppLiquidFloatingSurface(
        modifier =
            modifier
                .then(if (enabled) Modifier else Modifier.clearAndSetSemantics {})
                // One tag for every collapsed dock in the app: only one is ever on screen, and a
                // baseline-profile journey that finds a bar collapsed needs a way to open it again.
                // Scrolling back is not one — a tab whose content does not fill the screen has no scroll
                // range to give, which is how a capture died on the catalog's empty favourites tab.
                .testTag(KeiOsTestTags.CompactBottomBarDock)
                .size(AppChromeTokens.floatingBottomBarOuterHeight),
        shape = CircleShape,
        backdrop = backdrop,
        onClick = onClick.takeIf { enabled },
        pressSafePadding = AppInteractiveTokens.denseLiquidPressSafePadding,
        pressDurationMillis = 120,
        pressLabel = "compact_bottom_bar_dock_press",
        content = content,
    )
}

@Composable
internal fun AnimatedCompactBottomBar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    expandedContent: @Composable BoxScope.(Modifier, Boolean) -> Unit,
    compactContent: @Composable BoxScope.(Modifier, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 16.dp.toPx() }
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val transition =
        updateTransition(
            targetState = expanded,
            label = "compact_bottom_bar",
        )
    val animationSpec =
        tween<Float>(
            durationMillis = resolvedMotionDuration(CompactBottomBarMotionMs, animationsEnabled),
            easing = FastOutSlowInEasing,
        )
    val expandedProgress =
        transition.animateFloat(
            transitionSpec = { animationSpec },
            label = "compact_bottom_bar_expanded_progress",
        ) { isExpanded ->
            if (isExpanded) 1f else 0f
        }
    val compactProgress =
        transition.animateFloat(
            transitionSpec = { animationSpec },
            label = "compact_bottom_bar_compact_progress",
        ) { isExpanded ->
            if (isExpanded) 0f else 1f
        }

    Box(modifier = modifier.fillMaxWidth()) {
        if (transition.currentState || transition.targetState) {
            expandedContent(
                Modifier.graphicsLayer {
                    val progress = expandedProgress.value
                    alpha = progress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = snapChromeTranslationPx(-slideOffsetPx * (1f - progress))
                    scaleX = 0.86f + 0.14f * progress
                    scaleY = 0.94f + 0.06f * progress
                },
                expanded,
            )
        }
        if (!transition.currentState || !transition.targetState) {
            compactContent(
                Modifier.graphicsLayer {
                    val progress = compactProgress.value
                    alpha = progress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = snapChromeTranslationPx(slideOffsetPx * (1f - progress))
                    scaleX = 0.88f + 0.12f * progress
                    scaleY = 0.88f + 0.12f * progress
                },
                !expanded,
            )
        }
    }
}
