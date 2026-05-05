package os.kei.ui.page.main.widget.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppBottomSearchDock(
    backdrop: Backdrop?,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    contentDescription: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    expandedWidth: Dp? = null,
    compactDockReservedWidth: Dp = 116.dp,
    horizontalInset: Dp = 14.dp,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    accent: Color = MiuixTheme.colorScheme.primary,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val searchAutoFocusEnabled = LocalSearchAutoFocusEnabled.current
    val maxExpandedWidth = (
            configuration.screenWidthDp.dp -
                    horizontalInset * 2 -
                    compactDockReservedWidth -
                    AppChromeTokens.pageSectionGap
            ).coerceAtLeast(size)
    val targetWidth = if (expanded) expandedWidth ?: maxExpandedWidth else size
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(
            durationMillis = resolvedMotionDuration(
                AppBottomSearchDockWidthMotionMs,
                animationsEnabled,
            ),
        ),
        label = "app_bottom_search_dock_width",
    )
    val width = if (expandedWidth == null) animatedWidth else targetWidth
    val contentTransition = updateTransition(
        targetState = expanded,
        label = "app_bottom_search_dock_content",
    )
    val fieldAlpha by contentTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = resolvedMotionDuration(
                    AppBottomSearchDockContentFadeMs,
                    animationsEnabled,
                ),
            )
        },
        label = "app_bottom_search_field_alpha",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val iconAlpha by contentTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = resolvedMotionDuration(
                    AppBottomSearchDockContentFadeMs,
                    animationsEnabled,
                ),
            )
        },
        label = "app_bottom_search_icon_alpha",
    ) { visible ->
        if (visible) 0f else 1f
    }

    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    AppLiquidFloatingSurface(
        modifier = modifier
            .width(width)
            .height(size),
        shape = if (expanded) ContinuousCapsule else CircleShape,
        backdrop = backdrop,
        onClick = if (expanded) null else {
            { onExpandedChange(true) }
        },
        pressDurationMillis = 120,
        pressLabel = "app_bottom_search_dock_press",
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (fieldAlpha > AppBottomSearchDockVisibleAlpha) {
                AppBottomSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    focusRequester = focusRequester,
                    autoFocus = expanded && searchAutoFocusEnabled,
                    onFocusActiveChange = { active ->
                        if (active) onExpandedChange(true)
                    },
                    searchIcon = searchIcon,
                    placeholder = placeholder,
                    accent = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fieldAlpha },
                )
            }
            if (iconAlpha > AppBottomSearchDockVisibleAlpha) {
                Icon(
                    imageVector = searchIcon,
                    contentDescription = contentDescription,
                    tint = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(17.dp)
                        .size(iconSize)
                        .graphicsLayer {
                            alpha = iconAlpha
                            val scale = 0.90f + 0.10f * iconAlpha
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}

private const val AppBottomSearchDockWidthMotionMs = 220
private const val AppBottomSearchDockContentFadeMs = 120
private const val AppBottomSearchDockVisibleAlpha = 0.01f

@Composable
private fun AppBottomSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    autoFocus: Boolean,
    onFocusActiveChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }
    val textStyle = TextStyle(
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = AppTypographyTokens.CardHeader.fontSize,
        lineHeight = AppTypographyTokens.CardHeader.lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                onFocusActiveChange(true)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = searchIcon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(27.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onFocusActiveChange(false)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            ),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    onFocusActiveChange(state.isFocused)
                },
            decorationBox = { innerTextField ->
                if (query.isBlank()) {
                    Text(
                        text = placeholder,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = AppTypographyTokens.CardHeader.fontSize,
                        lineHeight = AppTypographyTokens.CardHeader.lineHeight,
                    )
                }
                innerTextField()
            },
        )
    }
}
