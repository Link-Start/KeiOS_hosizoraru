@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.glass.AppLiquidBadgedIcon
import os.kei.ui.page.main.widget.glass.appGlassRuntimeEffectsEnabled
import os.kei.ui.page.main.widget.glass.claimFloatingChromeDrags
import os.kei.ui.page.main.widget.glass.safeLiquidLens
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * One action in a [LiquidToolbar] group.
 *
 * Every action is independent. A toolbar has no selected item — that is a tab bar's job, and Apple's
 * toolbar guidance draws the line explicitly: "In contrast to a toolbar, a tab bar is specifically
 * for navigating between areas of an app."
 *
 * @param active the action's own on/off state — a toggle that is currently on, or a button whose
 *   popup is open. Renders the system-style tinted fill Apple describes for a toolbar item's
 *   selection appearance. Several actions may be active at once; they are unrelated.
 * @param prominent marks the one key action ("Done", "Submit"). Apple: use it for a single focal
 *   point and place it on the trailing side. Tints the action rather than the whole bar.
 */
data class LiquidToolbarAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val prominent: Boolean = false,
    val testTag: String? = null,
    val iconRotationDegrees: Float = 0f,
    val iconTint: Color? = null,
    val badgeLabel: String? = null,
    val badgeColor: Color? = null,
    val badgeContentColor: Color? = null,
    val tooltipText: String? = null,
)

/**
 * A trailing-edge toolbar: one or more groups of independent icon actions.
 *
 * Structure follows Apple's toolbar guidance. Each group is a single glass capsule, and the actions
 * inside it carry no border or glass of their own — "Borders (like outlined circle symbols) aren't
 * necessary because the section provides a visible container". Groups are separated by fixed space
 * so related actions read as one unit; Apple caps this at roughly three groups.
 *
 * That structure is also the cheap one. The blur is computed once per capsule rather than once per
 * action, which is what the Backdrop guidance means by keeping effects local to the surface that
 * needs them: a 52dp capsule is a small area, N separate button surfaces would not be.
 *
 * Press feedback is an [InteractiveHighlight] that blooms under the finger inside the capsule. It
 * observes pointers without consuming drags, so a horizontal swipe still reaches the pager beneath —
 * the reason the old implementation had to disable pager scrolling while the bar was touched.
 */
@Composable
fun LiquidToolbarGroups(
    backdrop: Backdrop,
    groups: List<List<LiquidToolbarAction>>,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true,
) {
    val visibleGroups = remember(groups) { groups.filter { it.isNotEmpty() } }
    if (visibleGroups.isEmpty()) return

    Row(
        modifier = modifier.height(AppChromeTokens.liquidActionBarOuterHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleGroups.forEachIndexed { index, actions ->
            if (index > 0) {
                Spacer(Modifier.width(AppChromeTokens.liquidToolbarGroupSpacing))
            }
            LiquidToolbarGroup(
                backdrop = backdrop,
                actions = actions,
                isBlurEnabled = isBlurEnabled,
            )
        }
    }
}

/**
 * One group of actions — the shape almost every page needs. Use [LiquidToolbarGroups] to separate
 * actions into distinct capsules.
 */
@Composable
fun LiquidToolbar(
    backdrop: Backdrop,
    actions: List<LiquidToolbarAction>,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true,
) {
    val groups = remember(actions) { listOf(actions) }
    LiquidToolbarGroups(
        backdrop = backdrop,
        groups = groups,
        modifier = modifier,
        isBlurEnabled = isBlurEnabled,
    )
}

@Composable
private fun LiquidToolbarGroup(
    backdrop: Backdrop,
    actions: List<LiquidToolbarAction>,
    isBlurEnabled: Boolean,
) {
    LiquidToolbarSurface(backdrop = backdrop, isBlurEnabled = isBlurEnabled) { palette, isInLightTheme ->
        actions.forEach { action ->
            LiquidToolbarActionSlot(
                action = action,
                palette = palette,
                accentColor = MiuixTheme.colorScheme.primary,
                isInLightTheme = isInLightTheme,
            )
        }
    }
}

/** A text action using the same untinted glass, lens and press feedback as an icon toolbar. */
@Composable
fun LiquidToolbarTextButton(
    backdrop: Backdrop,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBlurEnabled: Boolean = true,
) {
    LiquidToolbarSurface(backdrop = backdrop, modifier = modifier, isBlurEnabled = isBlurEnabled) { palette, _ ->
        Box(
            Modifier.weight(1f).height(AppChromeTokens.liquidActionBarInnerHeight)
                .clickable(enabled = enabled, role = Role.Button, indication = null,
                    interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = palette.inactiveContentColor.let { if (enabled) it else it.copy(alpha = AppInteractiveTokens.disabledContentAlpha) },
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiquidToolbarSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean,
    content: @Composable RowScope.(LiquidActionBarPalette, Boolean) -> Unit,
) {
    val isInLightTheme = !isAppInDarkTheme()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val effectiveBlurEnabled = isBlurEnabled && appGlassRuntimeEffectsEnabled()
    val material = liquidActionBarMaterial(isInLightTheme)
    val palette =
        rememberLiquidActionBarPalette(
            material = material,
            isBlurEnabled = effectiveBlurEnabled,
            isInLightTheme = isInLightTheme,
            primary = MiuixTheme.colorScheme.primary,
            onSurface = MiuixTheme.colorScheme.onSurface,
            surfaceContainer = MiuixTheme.colorScheme.surfaceContainer,
        )
    val animationScope = rememberCoroutineScope()
    val pressHighlight =
        if (effectiveBlurEnabled) {
            remember(animationScope, transitionAnimationsEnabled, palette) {
                // Default position tracks the finger, so the bloom lands on the action being
                // pressed without the bar needing to know which one that is.
                InteractiveHighlight(
                    animationScope = animationScope,
                    animationsEnabled = transitionAnimationsEnabled,
                    highlightColor = Color.White,
                    highlightStrength = liquidToolbarPressHighlightStrength(isInLightTheme),
                    highlightRadiusScale = LiquidToolbarPressHighlightRadiusScale,
                )
            }
        } else {
            null
        }

    val pressProgressProvider =
        remember(pressHighlight) { { pressHighlight?.pressProgress ?: 0f } }
    val dragOffsetProvider =
        remember(pressHighlight) { { pressHighlight?.offset ?: Offset.Zero } }
    Row(
        modifier =
            modifier
                .height(AppChromeTokens.liquidActionBarOuterHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        if (effectiveBlurEnabled) {
                            vibrancy()
                            blur(material.blur.toPx())
                            safeLiquidLens(
                                material.lensHeight.toPx(),
                                material.lensAmount.toPx(),
                            )
                        }
                    },
                    highlight = {
                        liquidToolbarHighlight(
                            material = material,
                            isBlurEnabled = effectiveBlurEnabled,
                            isInLightTheme = isInLightTheme,
                        )
                    },
                    shadow = {
                        liquidActionBarBaseShadow(isInLightTheme = isInLightTheme)
                    },
                    // Deformation belongs in layerBlock, not in a graphicsLayer on the node: it
                    // squishes the glass surface while leaving the sampled backdrop where it is.
                    // A graphicsLayer would drag the refraction along with the capsule and the
                    // glass would read as a moving decal instead of a lens.
                    layerBlock = {
                        val progress = pressProgressProvider()
                        val drag = dragOffsetProvider()
                        val maxSlide = LiquidToolbarDragSlide.toPx()
                        // Drag the glass and it follows, eased and capped, then springs back -- the
                        // damped give the old bar had, without the selection it used to commit on
                        // release.
                        val slideX = liquidToolbarDamp(drag.x, maxSlide)
                        val slideY = liquidToolbarDamp(drag.y, maxSlide)
                        translationX = slideX
                        translationY = slideY
                        // Stretch along the pull, squash across it, on top of the press.
                        val pull = (abs(slideX) / maxSlide).fastCoerceIn(0f, 1f)
                        scaleX = 1f + LiquidToolbarPressStretchX * progress + LiquidToolbarDragStretchX * pull
                        scaleY = 1f - LiquidToolbarPressSquashY * progress - LiquidToolbarDragSquashY * pull
                    },
                    onDrawSurface = { drawRect(palette.baseFillColor) },
                ).border(
                    width = 1.dp,
                    color = palette.outlineColor,
                    shape = ContinuousCapsule,
                ).then(pressHighlight?.modifier ?: Modifier)
                // Claim first (outer) so the highlight's gesture, which is inner, still reads the
                // drag on the Main pass. Reversed, the claim eats the position changes and the glass
                // stops following the finger.
                .claimFloatingChromeDrags()
                .then(pressHighlight?.gestureModifier ?: Modifier)
                .padding(horizontal = AppChromeTokens.liquidActionBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(palette, isInLightTheme)
    }
}

@Composable
private fun RowScope.LiquidToolbarActionSlot(
    action: LiquidToolbarAction,
    palette: LiquidActionBarPalette,
    accentColor: Color,
    isInLightTheme: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState =
        appMotionFloatState(
            targetValue = if (action.enabled && isPressed) AppInteractiveTokens.pressedScale else 1f,
            durationMillis = LiquidToolbarPressScaleDurationMillis,
            label = "liquid_toolbar_action_scale",
        )
    val contentColor =
        when {
            !action.enabled -> palette.inactiveContentColor.copy(alpha = 0.38f)
            action.iconTint != null -> action.iconTint
            action.prominent -> palette.activeContentColor
            action.active -> palette.activeContentColor
            else -> palette.inactiveContentColor
        }
    // The fill sits inside the capsule's horizontal padding, so its own capsule radius is the
    // group's minus that inset -- concentric, which is what Apple asks of custom bar components.
    val fillColor =
        when {
            !action.enabled -> Color.Transparent
            action.prominent -> accentColor.copy(alpha = if (isInLightTheme) 0.22f else 0.28f)
            action.active -> liquidChromeSelectionIndicatorColor(isInLightTheme, accentColor)
            else -> Color.Transparent
        }

    Box(
        modifier =
            Modifier
                .then(action.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .width(AppChromeTokens.liquidActionBarMinimumTouchTarget)
                .height(AppChromeTokens.liquidActionBarInnerHeight)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = action.enabled,
                    role = Role.Button,
                    onClick = action.onClick,
                ).then(
                    if (fillColor == Color.Transparent) {
                        Modifier
                    } else {
                        Modifier.background(color = fillColor, shape = ContinuousCapsule)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.graphicsLayer {
                    val scale = scaleState.value
                    scaleX = scale
                    scaleY = scale
                    rotationZ = action.iconRotationDegrees
                    colorFilter = ColorFilter.tint(contentColor)
                },
            contentAlignment = Alignment.Center,
        ) {
            LiquidToolbarActionTooltip(action) {
                AppLiquidBadgedIcon(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    tint = Color.White,
                    badgeLabel = action.badgeLabel,
                    badgeColor = action.badgeColor,
                    badgeContentColor = action.badgeContentColor,
                )
            }
        }
    }
}

@Composable
private fun LiquidToolbarActionTooltip(
    action: LiquidToolbarAction,
    content: @Composable () -> Unit,
) {
    val tooltipText = (action.tooltipText ?: action.contentDescription).takeIf { it.isNotBlank() }
    if (tooltipText == null || !action.enabled) {
        content()
        return
    }
    TooltipBox(
        text = tooltipText,
        positioning = TooltipAnchorPosition.Above,
        content = content,
    )
}

/**
 * The rim reflection. `HighlightStyle.Ambient` is an environment reflection around the shape rather
 * than the single directional streak `Default` draws, which is what makes Apple's glass read as
 * reflective from every angle instead of lit from one.
 */
private fun liquidToolbarHighlight(
    material: LiquidActionBarMaterial,
    isBlurEnabled: Boolean,
    isInLightTheme: Boolean,
): Highlight {
    if (!isBlurEnabled) {
        return liquidActionBarBaseHighlight(
            material = material,
            isBlurEnabled = isBlurEnabled,
        )
    }
    return Highlight(
        width = if (isInLightTheme) 1.0.dp else 0.9.dp,
        blurRadius = if (isInLightTheme) 2.0.dp else 1.7.dp,
        alpha = material.highlightAlpha,
        style = HighlightStyle.Ambient(if (isInLightTheme) 0.62f else 0.78f),
    )
}

private fun liquidToolbarPressHighlightStrength(isInLightTheme: Boolean): Float =
    if (isInLightTheme) 0.70f else 0.85f

/**
 * Tighter than the bar-wide sweep the old implementation used: the bloom should read as feedback for
 * one action, not for the whole capsule.
 */
private const val LiquidToolbarPressHighlightRadiusScale = 0.55f
private const val LiquidToolbarPressScaleDurationMillis = 110

/**
 * Invisible mirror of a single group's slot geometry, reporting each slot's window bounds so a page
 * can anchor a dropdown under the action that opened it.
 *
 * It has to track [LiquidToolbarActionSlot]'s layout exactly: fixed
 * [AppChromeTokens.liquidActionBarMinimumTouchTarget]-wide slots inside the capsule's horizontal
 * padding. The old action bar sized slots by `weight(1f)` against a hardcoded bar width, so anchors
 * drifted whenever the item count and that width disagreed.
 */
@Composable
fun LiquidToolbarPopupAnchors(
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int, () -> IntRect?) -> Unit,
) {
    if (itemCount <= 0) return
    val anchorBounds =
        remember(itemCount) {
            mutableStateListOf<IntRect?>().apply { repeat(itemCount) { add(null) } }
        }
    Row(
        modifier =
            modifier
                .height(AppChromeTokens.liquidActionBarOuterHeight)
                .padding(horizontal = AppChromeTokens.liquidActionBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            Box(
                modifier =
                    Modifier
                        .width(AppChromeTokens.liquidActionBarMinimumTouchTarget)
                        .height(AppChromeTokens.liquidActionBarInnerHeight)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            val measured =
                                IntRect(
                                    left = position.x.roundToInt(),
                                    top = position.y.roundToInt(),
                                    right = (position.x + coordinates.size.width).roundToInt(),
                                    bottom = (position.y + coordinates.size.height).roundToInt(),
                                )
                            if (anchorBounds[index] != measured) {
                                anchorBounds[index] = measured
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                content(index) { anchorBounds.getOrNull(index) }
            }
        }
    }
}

/** Apple's press deformation stretches across and settles down; keep both subtle. */
private const val LiquidToolbarPressStretchX = 0.016f
private const val LiquidToolbarPressSquashY = 0.022f

/** Eased, capped give: full response near zero, asymptotic at the cap. */
private fun liquidToolbarDamp(
    rawPx: Float,
    maxPx: Float,
): Float {
    if (maxPx <= 0f || rawPx == 0f) return 0f
    val fraction = (rawPx / (maxPx * LiquidToolbarDragResistance)).fastCoerceIn(-1f, 1f)
    return maxPx * sign(fraction) * EaseOut.transform(abs(fraction))
}

/** How far the glass may slide under the finger before it stops giving. */
private val LiquidToolbarDragSlide = 4.dp

/** Travel needed to reach the cap, as a multiple of it -- higher feels heavier. */
private const val LiquidToolbarDragResistance = 6f
private const val LiquidToolbarDragStretchX = 0.020f
private const val LiquidToolbarDragSquashY = 0.014f
