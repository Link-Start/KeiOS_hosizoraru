@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import os.kei.ui.page.main.widget.shape.appSquircleClip
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat

@Composable
internal fun BaGuideBgmDockSelectionPill(
    backdrop: Backdrop,
    isDark: Boolean,
    pressProgress: () -> Float,
    itemPressProgress: () -> Float,
    dragScaleX: () -> Float,
    dragScaleY: () -> Float,
    velocity: () -> Float,
    modifier: Modifier = Modifier,
) {
    val neutralFill =
        if (isDark) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        }
    val borderColor =
        if (isDark) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.White.copy(alpha = 0.38f)
        }
    Box(
        modifier =
            modifier.then(
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        val clampedPress = pressProgress().coerceIn(0f, 1f)
                        if (clampedPress > 0f) {
                            lens(
                                5.dp.toPx() * clampedPress,
                                7.dp.toPx() * clampedPress,
                                true,
                            )
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = pressProgress().coerceIn(0f, 1f))
                    },
                    shadow = {
                        Shadow(alpha = pressProgress().coerceIn(0f, 1f))
                    },
                    innerShadow = {
                        val clampedPress = pressProgress().coerceIn(0f, 1f)
                        InnerShadow(radius = 8.dp * clampedPress, alpha = clampedPress)
                    },
                    layerBlock = {
                        val clickScale =
                            lerpFloat(
                                1f,
                                BaGuideBgmDockClickScale,
                                itemPressProgress().coerceIn(0f, 1f),
                            )
                        val velocityScale = velocity() / 10f
                        scaleX =
                            dragScaleX() * clickScale /
                            (
                                1f -
                                    (velocityScale * BaGuideBgmDockVelocityScaleXFactor)
                                        .coerceIn(
                                            -BaGuideBgmDockVelocityScaleClamp,
                                            BaGuideBgmDockVelocityScaleClamp,
                                        )
                            )
                        scaleY =
                            dragScaleY() * clickScale *
                            (
                                1f -
                                    (velocityScale * BaGuideBgmDockVelocityScaleYFactor)
                                        .coerceIn(
                                            -BaGuideBgmDockVelocityScaleClamp,
                                            BaGuideBgmDockVelocityScaleClamp,
                                        )
                            )
                    },
                    onDrawSurface = {
                        val clampedPress = pressProgress().coerceIn(0f, 1f)
                        drawRect(neutralFill, alpha = 1f - clampedPress)
                        drawRect(Color.Black.copy(alpha = 0.03f * clampedPress))
                    },
                ),
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .appSquircleBackground(
                        if (isDark) {
                            Color.White.copy(alpha = 0.03f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                        999.dp,
                    ),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .appSquircleBorder(1.dp, borderColor, 999.dp),
        )
    }
}

@Composable
internal fun BaGuideBgmExpandedDockTab(
    tab: BaGuideBgmDockTab,
    selected: Boolean,
    selectionProgress: () -> Float,
    selectedContentScale: () -> Float,
    itemPressProgress: () -> Float,
    accent: Color,
    activeTint: Boolean,
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed, enabled) {
        if (enabled) onPressedChange(pressed)
    }
    DisposableEffect(enabled) {
        onDispose {
            if (enabled) onPressedChange(false)
        }
    }
    Box(
        modifier =
            modifier
                .appSquircleClip(999.dp)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.graphicsLayer {
                    val normalizedSelectionProgress = selectionProgress().coerceIn(0f, 1f)
                    val baseScale =
                        if (normalizedSelectionProgress > 0f) {
                            lerpFloat(
                                1f,
                                selectedContentScale(),
                                normalizedSelectionProgress,
                            )
                        } else {
                            1f
                        }
                    val pressedScale =
                        lerpFloat(
                            0.92f,
                            0.96f,
                            normalizedSelectionProgress,
                        )
                    val press = if (pressed && enabled) itemPressProgress().coerceIn(0f, 1f) else 0f
                    val itemScale = lerpFloat(baseScale, pressedScale, press)
                    scaleX = itemScale
                    scaleY = itemScale
                },
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tintProgress = if (activeTint) 1f else 0f
            BaGuideBgmDockTabIcon(
                icon = tab.icon,
                label = tab.label,
                selected = selected,
                accent = accent,
                selectionProgress = tintProgress,
            )
            Text(
                text = tab.label,
                color =
                    baGuideBgmDockTint(
                        selected = selected,
                        accent = accent,
                        selectionProgress = tintProgress,
                    ),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BaGuideBgmDockTabIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    iconSize: Dp = 24.dp,
    selectionProgress: Float = if (selected) 1f else 0f,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint =
            baGuideBgmDockTint(
                selected = selected,
                accent = accent,
                selectionProgress = selectionProgress,
            ),
        modifier = Modifier.size(iconSize),
    )
}

@Composable
internal fun baGuideBgmDockTint(
    selected: Boolean,
    accent: Color,
    selectionProgress: Float = if (selected) 1f else 0f,
): Color =
    lerpColor(
        MiuixTheme.colorScheme.onBackground.copy(alpha = 0.90f),
        accent,
        selectionProgress.coerceIn(0f, 1f),
    )
