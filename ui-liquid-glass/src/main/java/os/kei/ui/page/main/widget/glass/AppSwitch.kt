@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.drawAppSquircleBackground
import androidx.compose.ui.graphics.lerp as lerpColor

val LocalLiquidControlsEnabled = staticCompositionLocalOf { true }

private val AppLiquidSwitchLightBlue = Color(0xFF3B82F6)
private val AppLiquidSwitchDarkBlue = Color(0xFF7AB8FF)

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val touchModifier =
        modifier
            .requiredSize(width = 64.dp, height = 48.dp)

    if (!appGlassRuntimeEffectsEnabled()) {
        AppFallbackSwitchToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = touchModifier,
            enabled = enabled,
        )
        return
    }

    val switchBackdrop = rememberLayerBackdrop()
    Box(
        modifier = touchModifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier
            .matchParentSize()
            .layerBackdrop(switchBackdrop))
        LiquidSwitchToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = switchBackdrop,
            enabled = enabled,
            modifier = Modifier.matchParentSize(),
            checkedColor =
                if (androidx.compose.foundation.isSystemInDarkTheme()) {
                    AppLiquidSwitchDarkBlue
                } else {
                    AppLiquidSwitchLightBlue
                },
        )
    }
}

@Composable
private fun AppFallbackSwitchToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    val accentColor = if (isLightTheme) AppLiquidSwitchLightBlue else AppLiquidSwitchDarkBlue
    val trackColor =
        if (isLightTheme) {
            // Match the glass switch: 0.20 alpha was too faint over white cards. See LiquidSwitchToggle.
            Color(0xFF787878).copy(alpha = 0.30f)
        } else {
            Color(0xFF787880).copy(alpha = 0.36f)
        }
    val thumbColor = if (isLightTheme) Color.White else Color(0xFFE5E7EB)
    val progressState =
        appMotionFloatState(
            targetValue = if (checked) 1f else 0f,
            durationMillis = 160,
            label = "app_fallback_switch_progress",
        )
    val progressProvider = remember(progressState) { { progressState.value } }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = onCheckedChange,
                )
                .graphicsLayer {
                    alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                }
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .drawAppSquircleBackground(999.dp) {
                        lerpColor(trackColor, accentColor, progressProvider())
                    }
                    .size(52.dp, 28.dp),
        )
        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        val padding = 2.dp.toPx()
                        val travel = 24.dp.toPx()
                        val progress = progressProvider()
                        translationX =
                            if (isLtr) {
                                lerp(-travel / 2f + padding, travel / 2f - padding, progress)
                            } else {
                                lerp(travel / 2f - padding, -travel / 2f + padding, progress)
                            }
                    }
                    .drawAppSquircleBackground(999.dp) {
                        thumbColor
                    }
                    .size(24.dp),
        )
    }
}

@Composable
private fun LiquidSwitchToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color.Unspecified,
) {
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    val accentColor =
        if (checkedColor.isSpecified) {
            checkedColor
        } else if (isLightTheme) {
            Color(0xFF34C759)
        } else {
            Color(0xFF30D158)
        }
    val trackColor =
        if (isLightTheme) {
            // OFF track in light mode. Bumped from 0.20 -> 0.30 alpha: at 0.20 the gray pill nearly
            // vanished over white settings cards, making it hard to tell an OFF switch was there.
            // 0.30 reads as a clear ~#D6D6D6 gray while staying subtle, matching the iOS off-track.
            Color(0xFF787878).copy(alpha = 0.30f)
        } else {
            Color(0xFF787880).copy(alpha = 0.36f)
        }
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val currentEnabled by rememberUpdatedState(enabled)
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val toggleInteractionSource = remember { MutableInteractionSource() }
    val dampedDragAnimation =
        remember(animationScope, dragWidth, isLtr, touchSlop) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = fraction,
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.5f,
                consumeDragChanges = true,
                onDragStarted = {
                    dragDistancePx = 0f
                },
                onDragStopped = {
                    if (!currentEnabled) return@DampedDragAnimation
                    if (didDrag) {
                        fraction = if (targetValue >= 0.5f) 1f else 0f
                        currentOnSelect(fraction == 1f)
                    } else {
                        fraction = if (currentSelected()) 1f else 0f
                    }
                    didDrag = false
                    dragDistancePx = 0f
                },
                onDrag = { _, dragAmount ->
                    if (!currentEnabled) return@DampedDragAnimation
                    dragDistancePx += dragAmount.getDistance()
                    if (!didDrag) {
                        didDrag = dragDistancePx > touchSlop
                    }
                    if (!didDrag) {
                        return@DampedDragAnimation
                    }
                    val delta = dragAmount.x / dragWidth
                    fraction =
                        if (isLtr) {
                            (fraction + delta).fastCoerceIn(0f, 1f)
                        } else {
                            (fraction - delta).fastCoerceIn(0f, 1f)
                        }
                },
            )
        }
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(dampedDragAnimation, snapshotFlowManager) {
        snapshotFlowManager
            .snapshotFlow { fraction }
            .collectLatest { value -> dampedDragAnimation.updateValue(value) }
    }
    val externalSelected = selected()
    LaunchedEffect(externalSelected) {
        val target = if (externalSelected) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }
    val glassRuntime = glassEffectRuntime()

    val trackBackdrop = rememberLayerBackdrop()
    val combinedBackdrop =
        rememberCombinedBackdrop(
            backdrop,
            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                val progress = dampedDragAnimation.pressProgress
                val scaleX = lerp(2f / 3f, 0.75f, progress)
                val scaleY = lerp(0f, 0.75f, progress)
                scale(scaleX, scaleY) { drawBackdrop() }
            },
        )

    Box(
        modifier =
            modifier
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .toggleable(
                    value = externalSelected,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = toggleInteractionSource,
                    indication = null,
                    onValueChange = onSelect,
                )
                .graphicsLayer {
                    alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                }
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(externalSelected)
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .drawAppSquircleBackground(999.dp) {
                    lerpColor(trackColor, accentColor, dampedDragAnimation.value)
                }
                .size(64.dp, 28.dp),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX =
                        if (isLtr) {
                            lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                        } else {
                            lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                        }
                }
                .semantics { role = Role.Switch }
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(
                            lerp(
                                6.dp.toPx() * glassRuntime.blurScaleFor(GlassVariant.Compact),
                                2.dp.toPx() * glassRuntime.blurScaleFor(GlassVariant.Compact),
                                progress,
                            ),
                        )
                        val lensScale = glassRuntime.lensScaleFor(GlassVariant.Compact)
                        lens(
                            lerp(5.dp.toPx(), 12.dp.toPx(), progress) * lensScale,
                            lerp(10.dp.toPx(), 22.dp.toPx(), progress) * lensScale,
                            chromaticAberration = true,
                            depthEffect = true,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = lerp(0.34f, 1f, progress),
                        )
                    },
                    shadow = {
                        // The thumb's drop shadow is what separates a white thumb from a light track
                        // on a white card — the prior 0.05 alpha was almost invisible, which is why
                        // the OFF switch was hard to see in light mode. A stronger, slightly dropped
                        // shadow gives the iOS-style floating thumb clear edge definition.
                        Shadow(
                            radius = 5.dp,
                            offset = DpOffset(0.dp, 1.dp),
                            color = Color.Black.copy(alpha = 0.18f),
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 1.dp + (3.dp * progress),
                            alpha = lerp(0.12f, 1f, progress),
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - dampedDragAnimation.pressProgress))
                    },
                )
                .size(40.dp, 24.dp),
        )
    }
}
