package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class AppFloatingRefreshStatus {
    Idle,
    Cached,
    Refreshing,
    Success,
    Danger
}

data class AppFloatingDockAction(
    val icon: ImageVector,
    val contentDescription: String,
    val iconTint: Color,
    val enabled: Boolean = true,
    val rotating: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun AppFloatingSearchDock(
    backdrop: Backdrop?,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    contentDescription: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    dockSide: AppFloatingDockSide = AppFloatingDockSide.End,
    horizontalInset: Dp = 14.dp,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    gap: Dp = 8.dp,
    focusedLift: Dp = 18.dp,
    keyboardLift: Dp? = null,
    accent: Color = MiuixTheme.colorScheme.primary
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val resolvedKeyboardLift = keyboardLift ?: rememberAppFloatingKeyboardLift(focusedLift)
    val availableWidth = configuration.screenWidthDp.dp - horizontalInset * 2
    val fieldTargetWidth = (availableWidth - size - gap).coerceAtLeast(0.dp)
    val transition = updateTransition(targetState = expanded, label = "app_floating_search")
    val fieldWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
        label = "app_floating_search_field_width"
    ) { targetExpanded ->
        if (targetExpanded) fieldTargetWidth else 0.dp
    }
    val totalWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
        label = "app_floating_search_total_width"
    ) { targetExpanded ->
        size + if (targetExpanded) gap + fieldTargetWidth else 0.dp
    }
    val fieldAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockFadeMotionMs) },
        label = "app_floating_search_field_alpha"
    ) { targetExpanded ->
        if (targetExpanded) 1f else 0f
    }

    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus()
        }
    }

    val fieldContent: @Composable () -> Unit = {
        if (fieldWidth > 1.dp) {
            AppLiquidFloatingSurface(
                modifier = Modifier
                    .width(fieldWidth)
                    .height(size)
                    .alpha(fieldAlpha),
                shape = ContinuousCapsule,
                backdrop = backdrop,
                pressDurationMillis = 120,
                pressLabel = "app_floating_search_field_press"
            ) {
                AppFloatingSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    focusRequester = focusRequester,
                    autoFocus = expanded,
                    onFocusActiveChange = { active ->
                        if (active) onExpandedChange(true)
                    },
                    placeholder = placeholder,
                    accent = accent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    val buttonContent: @Composable () -> Unit = {
        AppFloatingLiquidActionButton(
            backdrop = backdrop,
            icon = searchIcon,
            contentDescription = contentDescription,
            onClick = { onExpandedChange(!expanded) },
            size = size,
            iconSize = iconSize,
            iconTint = if (expanded) accent else MiuixTheme.colorScheme.onBackground
        )
    }

    Row(
        modifier = modifier
            .offset(y = -resolvedKeyboardLift)
            .width(totalWidth)
            .height(size),
        horizontalArrangement = Arrangement.spacedBy(
            gap,
            if (dockSide == AppFloatingDockSide.Start) Alignment.Start else Alignment.End
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dockSide == AppFloatingDockSide.Start) {
            buttonContent()
            fieldContent()
        } else {
            fieldContent()
            buttonContent()
        }
    }
}

@Composable
fun AppFloatingVerticalSearchActionDock(
    backdrop: Backdrop?,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    searchContentDescription: String,
    placeholder: String,
    addIcon: ImageVector,
    addContentDescription: String,
    onAddClick: () -> Unit,
    refreshIcon: ImageVector,
    refreshContentDescription: String,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
    dockSide: AppFloatingDockSide = AppFloatingDockSide.End,
    showAddAction: Boolean = true,
    refreshEnabled: Boolean = true,
    refreshStatus: AppFloatingRefreshStatus = AppFloatingRefreshStatus.Idle,
    horizontalInset: Dp = 14.dp,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    gap: Dp = 8.dp,
    focusedLift: Dp = 18.dp,
    keyboardLift: Dp? = null,
    accent: Color = MiuixTheme.colorScheme.primary
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val resolvedKeyboardLift = keyboardLift ?: rememberAppFloatingKeyboardLift(focusedLift)
    val visibleActionCount = if (showAddAction) 3 else 2
    val dockHeight = size * visibleActionCount
    val availableWidth = configuration.screenWidthDp.dp - horizontalInset * 2
    val fieldTargetWidth = (availableWidth - size - gap).coerceAtLeast(0.dp)
    val transition = updateTransition(targetState = expanded, label = "app_vertical_floating_search")
    val fieldWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
        label = "app_vertical_floating_search_field_width"
    ) { targetExpanded ->
        if (targetExpanded) fieldTargetWidth else 0.dp
    }
    val totalWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
        label = "app_vertical_floating_search_total_width"
    ) { targetExpanded ->
        size + if (targetExpanded) gap + fieldTargetWidth else 0.dp
    }
    val fieldAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = AppFloatingSearchDockFadeMotionMs) },
        label = "app_vertical_floating_search_field_alpha"
    ) { targetExpanded ->
        if (targetExpanded) 1f else 0f
    }

    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus()
        }
    }

    val fieldContent: @Composable () -> Unit = {
        AppLiquidFloatingSurface(
            modifier = Modifier
                .width(fieldWidth)
                .height(size)
                .alpha(fieldAlpha),
            shape = ContinuousCapsule,
            backdrop = backdrop,
            pressDurationMillis = 120,
            pressLabel = "app_vertical_floating_search_field_press"
        ) {
            AppFloatingSearchField(
                query = query,
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                autoFocus = expanded,
                onFocusActiveChange = { active ->
                    if (active) onExpandedChange(true)
                },
                placeholder = placeholder,
                accent = accent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    val refreshTint = appFloatingRefreshTint(
        status = refreshStatus,
        enabled = refreshEnabled,
        neutral = MiuixTheme.colorScheme.onBackground,
        muted = MiuixTheme.colorScheme.onBackgroundVariant,
        success = Color(0xFF22C55E),
        danger = MiuixTheme.colorScheme.error,
        active = accent
    )
    val dockContent: @Composable () -> Unit = {
        AppLiquidFloatingSurface(
            modifier = Modifier
                .width(size)
                .height(dockHeight),
            shape = ContinuousCapsule,
            backdrop = backdrop,
            pressDurationMillis = 120,
            pressLabel = "app_vertical_floating_action_dock_press"
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (showAddAction) {
                    AppFloatingVerticalDockAction(
                        icon = addIcon,
                        contentDescription = addContentDescription,
                        onClick = onAddClick,
                        size = size,
                        iconSize = iconSize,
                        iconTint = accent
                    )
                }
                AppFloatingVerticalDockAction(
                    icon = refreshIcon,
                    contentDescription = refreshContentDescription,
                    onClick = onRefreshClick,
                    size = size,
                    iconSize = iconSize,
                    iconTint = refreshTint,
                    enabled = refreshEnabled && refreshStatus != AppFloatingRefreshStatus.Refreshing,
                    rotating = refreshStatus == AppFloatingRefreshStatus.Refreshing
                )
                AppFloatingVerticalDockAction(
                    icon = searchIcon,
                    contentDescription = searchContentDescription,
                    onClick = { onExpandedChange(!expanded) },
                    size = size,
                    iconSize = iconSize,
                    iconTint = accent
                )
            }
        }
    }

    Row(
        modifier = modifier
            .offset(y = -resolvedKeyboardLift)
            .width(totalWidth)
            .height(dockHeight),
        horizontalArrangement = Arrangement.spacedBy(
            gap,
            if (dockSide == AppFloatingDockSide.Start) Alignment.Start else Alignment.End
        ),
        verticalAlignment = Alignment.Bottom
    ) {
        if (dockSide == AppFloatingDockSide.Start) {
            dockContent()
            fieldContent()
        } else {
            fieldContent()
            dockContent()
        }
    }
}

@Composable
fun AppFloatingVerticalActionDock(
    backdrop: Backdrop?,
    actions: List<AppFloatingDockAction>,
    modifier: Modifier = Modifier,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp
) {
    if (actions.isEmpty()) return
    AppLiquidFloatingSurface(
        modifier = modifier
            .width(size)
            .height(size * actions.size),
        shape = ContinuousCapsule,
        backdrop = backdrop,
        pressDurationMillis = 120,
        pressLabel = "app_vertical_floating_action_dock_press"
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            actions.forEach { action ->
                AppFloatingVerticalDockAction(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    size = size,
                    iconSize = iconSize,
                    iconTint = action.iconTint,
                    enabled = action.enabled,
                    rotating = action.rotating
                )
            }
        }
    }
}

@Composable
private fun AppFloatingVerticalDockAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    iconTint: Color,
    enabled: Boolean = true,
    rotating: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedTint by animateColorAsState(
        targetValue = iconTint,
        animationSpec = tween(durationMillis = 180),
        label = "app_floating_vertical_dock_action_tint"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "app_floating_vertical_dock_action_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "app_floating_vertical_dock_action_rotation"
    )
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (enabled || rotating) {
                    Modifier
                } else {
                    Modifier.alpha(AppInteractiveTokens.disabledContentAlpha)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = if (rotating) rotation else 0f
                },
            tint = animatedTint
        )
    }
}

@Composable
private fun appFloatingRefreshTint(
    status: AppFloatingRefreshStatus,
    enabled: Boolean,
    neutral: Color,
    muted: Color,
    success: Color,
    danger: Color,
    active: Color
): Color {
    return when (status) {
        AppFloatingRefreshStatus.Refreshing -> active
        AppFloatingRefreshStatus.Success -> success
        AppFloatingRefreshStatus.Danger -> danger
        AppFloatingRefreshStatus.Cached -> Color(0xFFF59E0B)
        AppFloatingRefreshStatus.Idle -> if (enabled) neutral else muted
    }
}

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
        animationSpec = androidx.compose.animation.core.tween(durationMillis = AppFloatingKeyboardLiftMotionMs),
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

private const val AppFloatingSearchDockWidthMotionMs = 220
private const val AppFloatingSearchDockFadeMotionMs = 120
private const val AppFloatingKeyboardLiftMotionMs = 160

@Composable
private fun AppFloatingSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    autoFocus: Boolean,
    onFocusActiveChange: (Boolean) -> Unit,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val contentColor = MiuixTheme.colorScheme.onBackground
    val placeholderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f)
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = TextStyle(
        color = contentColor,
        fontSize = AppTypographyTokens.CardHeader.fontSize,
        lineHeight = AppTypographyTokens.CardHeader.lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onFocusActiveChange(true)
                focusRequester.requestFocus()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                }
            ),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state -> onFocusActiveChange(state.isFocused) },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isBlank()) {
                        BasicText(
                            text = placeholder,
                            style = textStyle.copy(color = placeholderColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
