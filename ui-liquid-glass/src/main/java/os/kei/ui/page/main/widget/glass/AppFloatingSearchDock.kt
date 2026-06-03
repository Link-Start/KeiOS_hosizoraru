@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LocalSearchAutoFocusEnabled
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class AppFloatingRefreshStatus {
    Idle,
    Cached,
    Refreshing,
    Success,
    Danger,
}

data class AppFloatingDockAction(
    val icon: ImageVector,
    val contentDescription: String,
    val iconTint: Color,
    val enabled: Boolean = true,
    val rotating: Boolean = false,
    val testTag: String = "",
    val onClick: () -> Unit,
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
    keyboardLiftProvider: (() -> Dp)? = null,
    accent: Color = MiuixTheme.colorScheme.primary,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searchAutoFocusEnabled = LocalSearchAutoFocusEnabled.current
    val resolvedKeyboardLiftProvider =
        rememberAppFloatingKeyboardLiftProvider(
            keyboardLiftProvider = keyboardLiftProvider,
            keyboardLift = keyboardLift,
            focusedLift = focusedLift,
        )
    val availableWidth = appWindowWidthDp() - horizontalInset * 2
    val fieldTargetWidth = (availableWidth - size - gap).coerceAtLeast(0.dp)
    val transition = updateTransition(targetState = expanded, label = "app_floating_search")
    val fieldWidthState =
        transition.animateDp(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
            label = "app_floating_search_field_width",
        ) { targetExpanded ->
            if (targetExpanded) fieldTargetWidth else 0.dp
        }
    val totalWidthState =
        transition.animateDp(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
            label = "app_floating_search_total_width",
        ) { targetExpanded ->
            size + if (targetExpanded) gap + fieldTargetWidth else 0.dp
        }
    val fieldAlphaState =
        transition.animateFloat(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockFadeMotionMs) },
            label = "app_floating_search_field_alpha",
        ) { targetExpanded ->
            if (targetExpanded) 1f else 0f
        }
    val fieldWidthProvider = remember(fieldWidthState) { { fieldWidthState.value } }
    val totalWidthProvider = remember(totalWidthState) { { totalWidthState.value } }
    val fieldAlphaProvider = remember(fieldAlphaState) { { fieldAlphaState.value } }

    LaunchedEffect(expanded) {
        if (!expanded) focusManager.clearFocus()
    }

    val fieldContent: @Composable () -> Unit = {
        AppFloatingSearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            autoFocus = expanded && searchAutoFocusEnabled,
            onFocusActiveChange = { active ->
                if (active) onExpandedChange(true)
            },
            placeholder = placeholder,
            accent = accent,
            backdrop = backdrop,
            modifier =
                Modifier
                    .appFloatingDockAnimatedWidth(fieldWidthProvider)
                    .height(size)
                    .graphicsLayer { alpha = fieldAlphaProvider() },
        )
    }
    val buttonContent: @Composable () -> Unit = {
        AppFloatingLiquidActionButton(
            backdrop = backdrop,
            icon = searchIcon,
            contentDescription = contentDescription,
            onClick = { onExpandedChange(!expanded) },
            size = size,
            iconSize = iconSize,
            iconTint = if (expanded) accent else MiuixTheme.colorScheme.onBackground,
        )
    }

    Row(
        modifier =
            modifier
                .appFloatingDockLift(resolvedKeyboardLiftProvider)
                .appFloatingDockAnimatedWidth(totalWidthProvider)
                .height(size),
        horizontalArrangement =
            Arrangement.spacedBy(
                gap,
                if (dockSide == AppFloatingDockSide.Start) Alignment.Start else Alignment.End,
            ),
        verticalAlignment = Alignment.CenterVertically,
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
    extraActions: List<AppFloatingDockAction> = emptyList(),
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
    keyboardLiftProvider: (() -> Dp)? = null,
    accent: Color = MiuixTheme.colorScheme.primary,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searchAutoFocusEnabled = LocalSearchAutoFocusEnabled.current
    val resolvedKeyboardLiftProvider =
        rememberAppFloatingKeyboardLiftProvider(
            keyboardLiftProvider = keyboardLiftProvider,
            keyboardLift = keyboardLift,
            focusedLift = focusedLift,
        )
    val visibleActionCount = (if (showAddAction) 1 else 0) + extraActions.size + 2
    val dockHeight = appFloatingVerticalDockHeight(size, visibleActionCount)
    val availableWidth = appWindowWidthDp() - horizontalInset * 2
    val fieldTargetWidth = (availableWidth - size - gap).coerceAtLeast(0.dp)
    val transition = updateTransition(targetState = expanded, label = "app_vertical_floating_search")
    val fieldWidthState =
        transition.animateDp(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
            label = "app_vertical_floating_search_field_width",
        ) { targetExpanded ->
            if (targetExpanded) fieldTargetWidth else 0.dp
        }
    val totalWidthState =
        transition.animateDp(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockWidthMotionMs) },
            label = "app_vertical_floating_search_total_width",
        ) { targetExpanded ->
            size + if (targetExpanded) gap + fieldTargetWidth else 0.dp
        }
    val fieldAlphaState =
        transition.animateFloat(
            transitionSpec = { tween(durationMillis = AppFloatingSearchDockFadeMotionMs) },
            label = "app_vertical_floating_search_field_alpha",
        ) { targetExpanded ->
            if (targetExpanded) 1f else 0f
        }
    val fieldWidthProvider = remember(fieldWidthState) { { fieldWidthState.value } }
    val totalWidthProvider = remember(totalWidthState) { { totalWidthState.value } }
    val fieldAlphaProvider = remember(fieldAlphaState) { { fieldAlphaState.value } }

    LaunchedEffect(expanded) {
        if (!expanded) focusManager.clearFocus()
    }

    val fieldContent: @Composable () -> Unit = {
        AppFloatingSearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            autoFocus = expanded && searchAutoFocusEnabled,
            onFocusActiveChange = { active ->
                if (active) onExpandedChange(true)
            },
            placeholder = placeholder,
            accent = accent,
            backdrop = backdrop,
            modifier =
                Modifier
                    .appFloatingDockAnimatedWidth(fieldWidthProvider)
                    .height(size)
                    .graphicsLayer { alpha = fieldAlphaProvider() },
        )
    }
    val refreshTint =
        appFloatingRefreshTint(
            status = refreshStatus,
            enabled = refreshEnabled,
            neutral = MiuixTheme.colorScheme.onBackground,
            muted = MiuixTheme.colorScheme.onBackgroundVariant,
            success = Color(0xFF22C55E),
            danger = MiuixTheme.colorScheme.error,
            active = accent,
        )
    val dockContent: @Composable () -> Unit = {
        AppFloatingLiquidVerticalDockSurface(
            backdrop = backdrop,
            modifier =
                Modifier
                    .width(size)
                    .height(dockHeight),
        ) {
            Column(
                modifier = Modifier.matchParentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (showAddAction) {
                    AppFloatingVerticalDockAction(
                        icon = addIcon,
                        contentDescription = addContentDescription,
                        onClick = onAddClick,
                        size = size,
                        iconSize = iconSize,
                        iconTint = accent,
                    )
                }
                extraActions.forEach { action ->
                    AppFloatingVerticalDockAction(
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        onClick = action.onClick,
                        size = size,
                        iconSize = iconSize,
                        iconTint = action.iconTint,
                        enabled = action.enabled,
                        rotating = action.rotating,
                        testTag = action.testTag,
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
                    rotating = refreshStatus == AppFloatingRefreshStatus.Refreshing,
                )
                AppFloatingVerticalDockAction(
                    icon = searchIcon,
                    contentDescription = searchContentDescription,
                    onClick = { onExpandedChange(!expanded) },
                    size = size,
                    iconSize = iconSize,
                    iconTint = if (expanded) accent else MiuixTheme.colorScheme.onBackground,
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .appFloatingDockLift(resolvedKeyboardLiftProvider)
                .appFloatingDockAnimatedWidth(totalWidthProvider)
                .height(dockHeight),
    ) {
        if (dockSide == AppFloatingDockSide.Start) {
            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                dockContent()
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = size + gap),
            ) {
                fieldContent()
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = -(size + gap)),
            ) {
                fieldContent()
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                dockContent()
            }
        }
    }
}

@Composable
fun AppFloatingVerticalActionDock(
    backdrop: Backdrop?,
    actions: List<AppFloatingDockAction>,
    modifier: Modifier = Modifier,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    gap: Dp = 8.dp,
) {
    if (actions.isEmpty()) return
    Column(
        modifier =
            modifier
                .width(size)
                .height(appFloatingVerticalDockHeight(size, actions.size)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppFloatingLiquidVerticalDockSurface(
            backdrop = backdrop,
            modifier =
                Modifier
                    .width(size)
                    .height(appFloatingVerticalDockHeight(size, actions.size)),
        ) {
            Column(
                modifier = Modifier.matchParentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
                        rotating = action.rotating,
                        testTag = action.testTag,
                    )
                }
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
    rotating: Boolean = false,
    testTag: String = "",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedTintState =
        animateColorAsState(
            targetValue = iconTint,
            animationSpec = tween(durationMillis = 180),
            label = "app_floating_vertical_dock_action_tint",
        )
    val animatedTintProvider = remember(animatedTintState) { { animatedTintState.value } }
    val pressedScaleState =
        appMotionFloatState(
            targetValue = if (isPressed) AppInteractiveTokens.pressedScale else 1f,
            durationMillis = 110,
            label = "app_floating_vertical_dock_action_scale",
        )
    val pressedScaleProvider = remember(pressedScaleState) { { pressedScaleState.value } }
    val rotationProvider = rememberFloatingDockActionRotationProvider(rotating)

    Box(
        modifier =
            Modifier
                .size(size)
                .then(
                    if (testTag.isBlank()) {
                        Modifier
                    } else {
                        Modifier.testTag(testTag)
                    },
                )
                .graphicsLayer {
                    alpha = if (enabled || rotating) 1f else AppInteractiveTokens.disabledContentAlpha
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        val pressedScale = pressedScaleProvider()
                        scaleX = pressedScale
                        scaleY = pressedScale
                        rotationZ = if (rotating) rotationProvider() else 0f
                        colorFilter = ColorFilter.tint(animatedTintProvider())
                    },
            tint = Color.White,
        )
    }
}

@Composable
private fun rememberFloatingDockActionRotationProvider(rotating: Boolean): () -> Float {
    if (!rotating) return remember { { 0f } }
    val infiniteTransition = rememberInfiniteTransition(label = "app_floating_vertical_dock_action_rotation")
    val rotationState =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 820, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "app_floating_vertical_dock_action_rotation",
        )
    return remember(rotationState) { { rotationState.value } }
}

private const val AppFloatingSearchDockWidthMotionMs = 220
private const val AppFloatingSearchDockFadeMotionMs = 120

@Composable
private fun rememberAppFloatingKeyboardLiftProvider(
    keyboardLiftProvider: (() -> Dp)?,
    keyboardLift: Dp?,
    focusedLift: Dp,
): () -> Dp {
    if (keyboardLiftProvider != null) {
        return keyboardLiftProvider
    }
    if (keyboardLift != null) {
        return remember(keyboardLift) { { keyboardLift } }
    }
    val keyboardLiftState = rememberAppFloatingKeyboardLiftState(focusedLift)
    return remember(keyboardLiftState) { { keyboardLiftState.value } }
}

private fun Modifier.appFloatingDockLift(lift: () -> Dp): Modifier = offset { IntOffset(x = 0, y = -lift().roundToPx()) }

private fun Modifier.appFloatingDockAnimatedWidth(width: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val widthPx = width().roundToPx().coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = widthPx,
                    maxWidth = widthPx,
                ),
            )
        layout(widthPx, placeable.height) {
            placeable.place(0, 0)
        }
    }

@Composable
private fun AppFloatingSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    autoFocus: Boolean,
    onFocusActiveChange: (Boolean) -> Unit,
    placeholder: String,
    accent: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    AppLiquidInputField(
        value = query,
        onValueChange = onQueryChange,
        label = placeholder,
        backdrop = backdrop,
        modifier = modifier,
        singleLine = true,
        fontSize = AppTypographyTokens.CardHeader.fontSize,
        textColor = MiuixTheme.colorScheme.onBackground,
        variant = GlassVariant.SearchField,
        minHeight = AppChromeTokens.floatingBottomBarOuterHeight,
        horizontalPadding = 18.dp,
        verticalPadding = 0.dp,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onFocusActiveChange(false)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            ),
        focusRequester = focusRequester,
        onFocusActiveChange = onFocusActiveChange,
    )
}
