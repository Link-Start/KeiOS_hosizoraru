package os.kei.ui.page.main.about.page

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutSearchDock(
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
    val configuration = LocalConfiguration.current
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val maxExpandedWidth = (
            configuration.screenWidthDp.dp -
                    horizontalInset * 2 -
                    compactDockReservedWidth -
                    AppChromeTokens.pageSectionGap
            ).coerceAtLeast(size)
    val width by animateDpAsState(
        targetValue = if (expanded) expandedWidth ?: maxExpandedWidth else size,
        animationSpec = tween(
            durationMillis = resolvedMotionDuration(
                AboutSearchDockWidthMotionMs,
                animationsEnabled
            ),
        ),
        label = "about_search_dock_width",
    )

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
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
        pressLabel = "about_search_dock_press",
    ) {
        if (expanded) {
            AboutSearchField(
                query = query,
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                onFocusActiveChange = { active ->
                    if (active) onExpandedChange(true)
                },
                searchIcon = searchIcon,
                placeholder = placeholder,
                accent = accent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = searchIcon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(17.dp)
                    .size(iconSize),
            )
        }
    }
}

private const val AboutSearchDockWidthMotionMs = 260

@Composable
private fun AboutSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusActiveChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
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
