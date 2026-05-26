@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import os.kei.ui.page.main.widget.shape.appSquircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppLiquidInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onFocusActiveChange: ((Boolean) -> Unit)? = null,
    leadingContentGap: Dp = AppInteractiveTokens.controlContentGap,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val activeBackdrop = backdrop.takeIf { LocalLiquidControlsEnabled.current }
    var focused by remember { mutableStateOf(false) }
    val usesSearchMaterial = variant == GlassVariant.SearchField
    val focusProgress by appMotionFloatState(
        targetValue = if (focused) 1f else 0f,
        durationMillis = 140,
        label = "app_liquid_search_field_focus",
    )
    val sheetInputAccent = resolveGlassAccentColor(textColor, isDark)
    val placeholderColor =
        if (variant == GlassVariant.SheetInput) {
            sheetInputAccent.copy(alpha = if (isDark) 0.72f else 0.62f)
        } else if (usesSearchMaterial) {
            appLiquidSearchPlaceholderColor(
                contentColor = MiuixTheme.colorScheme.onBackground,
                variantColor = MiuixTheme.colorScheme.onBackgroundVariant,
                isDark = isDark,
            )
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant
        }
    val effectiveLineHeight =
        if (singleLine && variant == GlassVariant.SheetInput) {
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
    val borderModifier =
        if (!glass.showBorder) {
            Modifier
        } else {
            Modifier.appSquircleBorder(
                width = glass.borderWidth,
                color =
                    glass.borderColor.copy(
                        alpha = (glass.borderColor.alpha + 0.14f * focusProgress).coerceAtMost(1f),
                    ),
                cornerRadius = 999.dp,
            )
        }

    val contentAlignment =
        when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    val effectiveKeyboardOptions =
        keyboardOptions ?: if (singleLine) {
            KeyboardOptions(imeAction = ImeAction.Done)
        } else {
            KeyboardOptions.Default
        }

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = minHeight)
                .appSquircleClip(999.dp)
                .then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = activeBackdrop,
                            shape = { ContinuousCapsule },
                            layerBlock = {
                                val focusScale =
                                    1f + 2.dp.toPx() / size.height.coerceAtLeast(1f) * focusProgress
                                scaleX = focusScale
                                scaleY = focusScale
                            },
                            effects = {
                                vibrancy()
                                blur((if (usesSearchMaterial) glass.blur + 1.dp * focusProgress else glass.blur).toPx())
                                lens(
                                    glass.lensStart.toPx() +
                                        if (usesSearchMaterial) 2.dp.toPx() * focusProgress else 4.dp.toPx() * focusProgress,
                                    glass.lensEnd.toPx() +
                                        if (usesSearchMaterial) 4.dp.toPx() * focusProgress else 6.dp.toPx() * focusProgress,
                                    chromaticAberration = usesSearchMaterial || focusProgress > 0.01f,
                                    depthEffect = usesSearchMaterial || focusProgress > 0.01f,
                                )
                            },
                            highlight = {
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
                                Shadow.Default.copy(
                                    color = Color.Black.copy(alpha = glass.shadowAlpha + 0.04f * focusProgress),
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 6.dp * focusProgress,
                                    alpha = 0.22f * focusProgress,
                                )
                            },
                            onDrawSurface = {
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
                                999.dp,
                            ).then(
                                if (glass.overlayColor != Color.Transparent) {
                                    Modifier.appSquircleBackground(glass.overlayColor, 999.dp)
                                } else {
                                    Modifier
                                },
                            )
                    },
                ).then(
                    if (usesSearchMaterial) {
                        appLiquidSearchMaterialOverlayModifier(
                            shape = ContinuousCapsule,
                            colors = searchColors,
                            focusProgress = focusProgress,
                            pressProgress = 0f,
                        )
                    } else {
                        Modifier
                    },
                ).graphicsLayer {
                    shadowElevation = 2.dp.toPx() * focusProgress
                    ambientShadowColor = Color.Black.copy(alpha = 0.06f * focusProgress)
                    spotShadowColor = Color.Black.copy(alpha = 0.08f * focusProgress)
                }.then(borderModifier)
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        @Composable
        fun TextInput(modifier: Modifier) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = inputTextStyle,
                cursorBrush = SolidColor(textColor),
                visualTransformation = visualTransformation,
                keyboardOptions = effectiveKeyboardOptions,
                keyboardActions = keyboardActions,
                modifier =
                    modifier
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .onFocusChanged { state ->
                            val active = state.isFocused || state.hasFocus
                            focused = active
                            onFocusActiveChange?.invoke(active)
                        }.then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.CenterVertically),
                        contentAlignment = contentAlignment,
                    ) {
                        if (value.isBlank()) {
                            BasicText(
                                text = label,
                                style = inputTextStyle.copy(color = placeholderColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (leadingContent == null) {
            TextInput(modifier = Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(leadingContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingContent()
                TextInput(modifier = Modifier.weight(1f))
            }
        }
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
    val isDark = isSystemInDarkTheme()
    val activeBackdrop = backdrop.takeIf { LocalLiquidControlsEnabled.current }
    val density = LocalDensity.current
    val focusProgress by appMotionFloatState(
        targetValue = if (focused) 1f else 0f,
        durationMillis = 140,
        label = "app_liquid_search_surface_focus",
    )
    val pressProgress by appMotionFloatState(
        targetValue = if (pressed) 1f else 0f,
        durationMillis = pressDurationMillis,
        label = pressLabel,
    )
    val materialProgress = maxOf(focusProgress, pressProgress)
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
                    translationY = -with(density) { 1.dp.toPx() } * pressProgress
                    scaleX = lerp(1f, 1.010f, pressProgress)
                    scaleY = lerp(1f, 0.992f, pressProgress)
                }.appSquircleClip(999.dp)
                .then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = activeBackdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur((glass.blur + 1.dp * materialProgress).toPx())
                                lens(
                                    glass.lensStart.toPx() + 2.dp.toPx() * materialProgress,
                                    glass.lensEnd.toPx() + 5.dp.toPx() * materialProgress,
                                    chromaticAberration = true,
                                    depthEffect = true,
                                )
                            },
                            highlight = {
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
                                Shadow.Default.copy(
                                    color =
                                        Color.Black.copy(
                                            alpha = (glass.shadowAlpha + 0.04f * focusProgress) * (1f - 0.25f * pressProgress),
                                        ),
                                )
                            },
                            innerShadow = {
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
                    appLiquidSearchMaterialOverlayModifier(
                        shape = shape,
                        colors = searchColors,
                        focusProgress = focusProgress,
                        pressProgress = pressProgress,
                    ),
                ).appSquircleBorder(
                    width = glass.borderWidth,
                    color =
                        glass.borderColor.copy(
                            alpha = (glass.borderColor.alpha + 0.10f * materialProgress).coerceAtMost(1f),
                        ),
                    cornerRadius = 999.dp,
                ),
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
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textColor: Color = MiuixTheme.colorScheme.onBackground,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = AppInteractiveTokens.appLiquidSearchFieldMinHeight,
    horizontalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldHorizontalPadding,
    verticalPadding: Dp = AppInteractiveTokens.appLiquidSearchFieldVerticalPadding,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onFocusActiveChange: ((Boolean) -> Unit)? = null,
    leadingContentGap: Dp = AppInteractiveTokens.controlContentGap,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val localBackdrop = rememberLayerBackdrop()
    AppStandaloneBackdropHost(
        backdrop = localBackdrop,
        modifier = modifier,
    ) {
        AppLiquidInputField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            backdrop = localBackdrop,
            modifier = fieldModifier,
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
            keyboardActions = keyboardActions,
            focusRequester = focusRequester,
            onFocusActiveChange = onFocusActiveChange,
            leadingContentGap = leadingContentGap,
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
