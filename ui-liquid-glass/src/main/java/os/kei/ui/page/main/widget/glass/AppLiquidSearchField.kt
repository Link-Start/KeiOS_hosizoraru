@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleClip
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppLiquidInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    lineHeight: TextUnit? = null,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    cornerRadius: Dp = 999.dp,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onFocusActiveChange: ((Boolean) -> Unit)? = null,
    leadingContentGap: Dp = AppInteractiveTokens.controlContentGap,
    placeholderMaxLines: Int = if (singleLine) 1 else 2,
    readOnly: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val isDark = isAppInDarkTheme()
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val liquidFlat = liquidFlatField(activeBackdrop)
    var focused by remember { mutableStateOf(false) }
    val usesSearchMaterial = variant == GlassVariant.SearchField
    val focusProgressState =
        appMotionFloatState(
            targetValue = if (focused) 1f else 0f,
            durationMillis = 140,
            label = "app_liquid_search_field_focus",
        )
    val focusProgressProvider = remember(focusProgressState) { { focusProgressState.value } }
    val sheetInputAccent = resolveGlassAccentColor(textColor, isDark)
    val placeholderColor =
        if (variant == GlassVariant.SheetInput) {
            sheetInputAccent.copy(alpha = if (isDark) 0.72f else 0.62f)
        } else if (usesSearchMaterial) {
            appLiquidSearchPlaceholderColor(
                contentColor = MiuixTheme.colorScheme.onBackground,
                isDark = isDark,
            )
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant
        }
    val effectiveLineHeight =
        lineHeight ?: if (singleLine && variant == GlassVariant.SheetInput) {
            fontSize
        } else {
            AppTypographyTokens.Body.lineHeight
        }
    val inputTextStyle =
        TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = effectiveLineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            textAlign = textAlign,
        )
    val glass =
        glassStyle(
            isDark = isDark,
            variant = variant,
            blurRadius = blurRadius,
        ).let { baseStyle ->
            if (variant == GlassVariant.SheetInput) {
                baseStyle.tintWithAccent(
                    accentColor = sheetInputAccent,
                    isDark = isDark,
                )
            } else {
                baseStyle
            }
        }
    val fallbackSurface = MiuixTheme.colorScheme.surfaceContainer
    val searchColors = appLiquidSearchMaterialColors(isDark)
    val effectiveKeyboardOptions =
        keyboardOptions ?: if (singleLine) {
            KeyboardOptions(imeAction = ImeAction.Done)
        } else {
            KeyboardOptions.Default
        }
    val inputContentStyle =
        remember(
            inputTextStyle,
            placeholderColor,
            textColor,
            leadingContentGap,
            placeholderMaxLines,
        ) {
            AppTextInputContentStyle(
                textStyle = inputTextStyle,
                placeholderColor = placeholderColor,
                cursorColor = textColor,
                leadingContentGap = leadingContentGap,
                placeholderMaxLines = placeholderMaxLines,
                wrapFieldContentHeight = true,
            )
        }

    val fieldShape =
        remember(cornerRadius) {
            if (cornerRadius == 999.dp) ContinuousCapsule else RoundedRectangle(cornerRadius)
        }
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = minHeight)
                .appSquircleClip(cornerRadius)
                .then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = liquidFlat ?: activeBackdrop,
                            shape = { fieldShape },
                            layerBlock = {
                                val focusProgress = focusProgressProvider()
                                val focusScale =
                                    1f + 2.dp.toPx() / size.height.coerceAtLeast(1f) * focusProgress
                                scaleX = focusScale
                                scaleY = focusScale
                            },
                            effects = {
                                if (liquidFlat != null) return@drawBackdrop
                                val focusProgress = focusProgressProvider()
                                vibrancy()
                                blur((if (usesSearchMaterial) glass.blur + 1.dp * focusProgress else glass.blur).toPx())
                                safeLiquidLens(
                                    glass.lensStart.toPx() +
                                        if (usesSearchMaterial) 2.dp.toPx() * focusProgress else 4.dp.toPx() * focusProgress,
                                    glass.lensEnd.toPx() +
                                        if (usesSearchMaterial) 4.dp.toPx() * focusProgress else 6.dp.toPx() * focusProgress,
                                    chromaticAberration = false,
                                    depthEffect = false,
                                )
                            },
                            highlight = {
                                val focusProgress = focusProgressProvider()
                                Highlight.Default.copy(
                                    alpha =
                                        appLiquidSearchHighlightAlpha(
                                            baseAlpha = glass.highlightAlpha,
                                            materialProgress = focusProgress,
                                            isDark = isDark,
                                        ),
                                )
                            },
                            shadow = {
                                val focusProgress = focusProgressProvider()
                                liquidGlassShadow(
                                    color = Color.Black.copy(alpha = glass.shadowAlpha + 0.04f * focusProgress),
                                )
                            },
                            innerShadow = {
                                val focusProgress = focusProgressProvider()
                                InnerShadow(
                                    radius = 6.dp * focusProgress,
                                    alpha = 0.22f * focusProgress,
                                )
                            },
                            onDrawSurface = {
                                val focusProgress = focusProgressProvider()
                                if (variant == GlassVariant.Bar || usesSearchMaterial) {
                                    drawRect(fallbackSurface.copy(alpha = glass.fallbackAlpha))
                                } else {
                                    drawRect(glass.baseColor)
                                    if (glass.overlayColor != Color.Transparent) drawRect(glass.overlayColor)
                                    if (focusProgress > 0f) {
                                        drawRect(sheetInputAccent.copy(alpha = 0.04f * focusProgress))
                                    }
                                }
                            },
                        )
                    } else {
                        Modifier
                            .appSquircleBackground(
                                glass.baseColor.takeIf { it != Color.Transparent }
                                    ?: fallbackSurface.copy(alpha = glass.fallbackAlpha),
                                cornerRadius,
                            ).then(
                                if (glass.overlayColor != Color.Transparent) {
                                    Modifier.appSquircleBackground(glass.overlayColor, cornerRadius)
                                } else {
                                    Modifier
                                },
                            )
                    },
                ).then(
                    if (usesSearchMaterial) {
                        Modifier.appLiquidSearchMaterialOverlay(
                            cornerRadius = cornerRadius,
                            colors = searchColors,
                            focusProgress = focusProgressProvider,
                            pressProgress = { 0f },
                        )
                    } else {
                        Modifier
                    },
                ).graphicsLayer {
                    val focusProgress = focusProgressProvider()
                    shadowElevation = 2.dp.toPx() * focusProgress
                    // A platform elevation shadow is derived from the layer's OUTLINE, and an unset
                    // shape is `RectangleShape` — so this was casting a hard rectangle behind a
                    // squircle field every time the field took focus. `clip = false` because the
                    // outline is wanted for the shadow only; the surface does its own clipping.
                    shape = fieldShape
                    clip = false
                    ambientShadowColor = Color.Black.copy(alpha = 0.06f * focusProgress)
                    spotShadowColor = Color.Black.copy(alpha = 0.08f * focusProgress)
                }.then(
                    if (!glass.showBorder) {
                        Modifier
                    } else {
                        Modifier.drawAppSquircleBorder(
                            width = glass.borderWidth,
                            cornerRadius = cornerRadius,
                        ) {
                            val focusProgress = focusProgressProvider()
                            glass.borderColor.copy(
                                alpha = (glass.borderColor.alpha + 0.14f * focusProgress).coerceAtMost(1f),
                            )
                        }
                    },
                ).padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        AppTextInputContent(
            value = value,
            onValueChange = onValueChange,
            label = label,
            style = inputContentStyle,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = effectiveKeyboardOptions,
            keyboardActions = keyboardActions,
            focusRequester = focusRequester,
            onFocusActiveChange = { active ->
                focused = active
                onFocusActiveChange?.invoke(active)
            },
            leadingContent = leadingContent,
        )
    }
}

@Composable
fun AppLiquidSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    onImeActionDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    AppLiquidInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        backdrop = backdrop,
        modifier = modifier,
        singleLine = singleLine,
        textAlign = textAlign,
        fontSize = fontSize,
        textColor = textColor,
        visualTransformation = visualTransformation,
        blurRadius = blurRadius,
        variant = variant,
        minHeight = minHeight,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        keyboardOptions = keyboardOptions,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onImeActionDone?.invoke()
                    focusManager.clearFocus()
                },
            ),
        focusRequester = focusRequester,
    )
}

@Composable
fun AppLiquidSearchSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousCapsule,
    focused: Boolean = false,
    pressed: Boolean = false,
    compactMaterial: Boolean = false,
    pressDurationMillis: Int = 120,
    pressLabel: String = "app_liquid_search_surface_press",
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isAppInDarkTheme()
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val liquidFlat = liquidFlatField(activeBackdrop)
    val density = LocalDensity.current
    val focusProgressState =
        appMotionFloatState(
            targetValue = if (focused) 1f else 0f,
            durationMillis = 140,
            label = "app_liquid_search_surface_focus",
        )
    val pressProgressState =
        appMotionFloatState(
            targetValue = if (pressed) 1f else 0f,
            durationMillis = pressDurationMillis,
            label = pressLabel,
        )
    val focusProgressProvider = remember(focusProgressState) { { focusProgressState.value } }
    val pressProgressProvider = remember(pressProgressState) { { pressProgressState.value } }
    val materialProgressProvider =
        remember(focusProgressProvider, pressProgressProvider) {
            { maxOf(focusProgressProvider(), pressProgressProvider()) }
        }
    val glass =
        glassStyle(
            isDark = isDark,
            variant = GlassVariant.SearchField,
            blurRadius = null,
        )
    val fallbackSurface = MiuixTheme.colorScheme.surfaceContainer
    val fallbackAlpha = if (isDark && compactMaterial) glass.fallbackAlpha * 0.62f else glass.fallbackAlpha
    val searchColors = appLiquidSearchMaterialColors(isDark, compactMaterial = compactMaterial)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val pressProgress = pressProgressProvider()
                    translationY = -with(density) { 1.dp.toPx() } * pressProgress
                    scaleX = lerp(1f, 1.010f, pressProgress)
                    scaleY = lerp(1f, 0.992f, pressProgress)
                }.then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = liquidFlat ?: activeBackdrop,
                            shape = { shape },
                            effects = {
                                if (liquidFlat != null) return@drawBackdrop
                                val materialProgress = materialProgressProvider()
                                vibrancy()
                                blur((glass.blur + 1.dp * materialProgress).toPx())
                                safeLiquidLens(
                                    glass.lensStart.toPx() + 2.dp.toPx() * materialProgress,
                                    glass.lensEnd.toPx() + 5.dp.toPx() * materialProgress,
                                    chromaticAberration = false,
                                    depthEffect = false,
                                )
                            },
                            highlight = {
                                val materialProgress = materialProgressProvider()
                                Highlight.Default.copy(
                                    alpha =
                                        appLiquidSearchHighlightAlpha(
                                            baseAlpha = glass.highlightAlpha,
                                            materialProgress = materialProgress,
                                            isDark = isDark,
                                            darkMaxAlpha = if (compactMaterial) 0.22f else 0.34f,
                                        ),
                                )
                            },
                            shadow = {
                                val focusProgress = focusProgressProvider()
                                val pressProgress = pressProgressProvider()
                                liquidGlassShadow(
                                    color =
                                        Color.Black.copy(
                                            alpha = (glass.shadowAlpha + 0.04f * focusProgress) * (1f - 0.25f * pressProgress),
                                        ),
                                )
                            },
                            innerShadow = {
                                val materialProgress = materialProgressProvider()
                                InnerShadow(
                                    radius = 6.dp * materialProgress,
                                    alpha = 0.18f * materialProgress,
                                )
                            },
                            onDrawSurface = {
                                drawRect(fallbackSurface.copy(alpha = fallbackAlpha))
                            },
                        )
                    } else {
                        Modifier.appSquircleBackground(fallbackSurface.copy(alpha = fallbackAlpha), 999.dp)
                    },
                ).then(
                    Modifier.appLiquidSearchMaterialOverlay(
                        cornerRadius = 999.dp,
                        colors = searchColors,
                        focusProgress = focusProgressProvider,
                        pressProgress = pressProgressProvider,
                    ),
                ).drawAppSquircleBorder(
                    width = glass.borderWidth,
                    cornerRadius = 999.dp,
                ) {
                    val materialProgress = materialProgressProvider()
                    glass.borderColor.copy(
                        alpha = (glass.borderColor.alpha + 0.10f * materialProgress).coerceAtMost(1f),
                    )
                },
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun AppStandaloneLiquidInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    lineHeight: TextUnit? = null,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    cornerRadius: Dp = 999.dp,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onFocusActiveChange: ((Boolean) -> Unit)? = null,
    leadingContentGap: Dp = AppInteractiveTokens.controlContentGap,
    placeholderMaxLines: Int = if (singleLine) 1 else 2,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    AppStandaloneBackdropHost(
        modifier = modifier,
    ) { activeBackdrop ->
        AppLiquidInputField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            backdrop = activeBackdrop,
            modifier = fieldModifier,
            enabled = enabled,
            singleLine = singleLine,
            textAlign = textAlign,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textColor = textColor,
            visualTransformation = visualTransformation,
            blurRadius = blurRadius,
            variant = variant,
            minHeight = minHeight,
            cornerRadius = cornerRadius,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            focusRequester = focusRequester,
            onFocusActiveChange = onFocusActiveChange,
            leadingContentGap = leadingContentGap,
            placeholderMaxLines = placeholderMaxLines,
            leadingContent = leadingContent,
        )
    }
}

@Composable
fun AppStandaloneLiquidSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    onImeActionDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    AppStandaloneLiquidInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        fieldModifier = fieldModifier,
        singleLine = singleLine,
        textAlign = textAlign,
        fontSize = fontSize,
        textColor = textColor,
        visualTransformation = visualTransformation,
        blurRadius = blurRadius,
        variant = variant,
        minHeight = minHeight,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        keyboardOptions = keyboardOptions,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onImeActionDone?.invoke()
                    focusManager.clearFocus()
                },
            ),
        focusRequester = focusRequester,
    )
}
