@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.LocalSearchAutoFocusEnabled
import os.kei.ui.page.main.widget.chrome.appWindowWidthDp
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class AppFloatingRefreshStatus {
    Idle,
    Cached,
    Refreshing,
    Success,
    Danger,
}

@Immutable
data class AppFloatingDockAction(
    val icon: ImageVector,
    val contentDescription: String,
    val iconTint: Color,
    val enabled: Boolean = true,
    val rotating: Boolean = false,
    val testTag: String = "",
    val badgeLabel: String? = null,
    val badgeColor: Color? = null,
    val badgeContentColor: Color? = null,
    val tooltipText: String? = null,
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
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    gap: Dp = 8.dp,
    focusedLift: Dp = 18.dp,
    keyboardLift: Dp? = null,
    keyboardLiftProvider: (() -> Dp)? = null,
    accent: Color = MiuixTheme.colorScheme.primary,
    compactIconTint: Color = MiuixTheme.colorScheme.onBackground,
    showActionWhenExpanded: Boolean = true,
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
    BoxWithConstraints(
        modifier = modifier.appFloatingDockLift(resolvedKeyboardLiftProvider),
    ) {
        val availableWidth =
            maxWidth.takeIf { width -> width.value.isFinite() } ?: appWindowWidthDp()
        val expandedActionWidth = if (showActionWhenExpanded) size + gap else 0.dp
        val fieldTargetWidth = (availableWidth - expandedActionWidth).coerceAtLeast(0.dp)
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
                if (targetExpanded) fieldTargetWidth + expandedActionWidth else size
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
        val fieldInteractive by
            remember(expanded, fieldWidthProvider) {
                derivedStateOf {
                    expanded &&
                        (fieldWidthProvider() > AppFloatingSearchFieldMinimumInteractiveWidth)
                }
            }

        LaunchedEffect(expanded) {
            if (!expanded) focusManager.clearFocus()
        }

        val fieldContent: @Composable () -> Unit = {
            AppFloatingSearchField(
                query = query,
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                interactive = fieldInteractive,
                autoFocus = fieldInteractive && searchAutoFocusEnabled,
                onFocusActiveChange = { active ->
                    if (active) onExpandedChange(true)
                },
                searchIcon = searchIcon,
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
                iconTint = if (expanded) accent else compactIconTint,
            )
        }

        Row(
            modifier =
                Modifier
                    .align(
                        if (dockSide == AppFloatingDockSide.Start) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        },
                    ).appFloatingDockAnimatedWidth(totalWidthProvider)
                    .height(size),
            horizontalArrangement =
                Arrangement.spacedBy(
                    gap,
                    if (dockSide == AppFloatingDockSide.Start) Alignment.Start else Alignment.End,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dockSide == AppFloatingDockSide.Start) {
                if (!expanded || showActionWhenExpanded) buttonContent()
                if (expanded && fieldInteractive) fieldContent()
            } else {
                if (expanded && fieldInteractive) fieldContent()
                if (!expanded || showActionWhenExpanded) buttonContent()
            }
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
    showRefreshAction: Boolean = true,
    refreshEnabled: Boolean = true,
    refreshStatus: AppFloatingRefreshStatus = AppFloatingRefreshStatus.Idle,
    refreshBadgeLabel: String? = null,
    refreshBadgeColor: Color? = null,
    refreshBadgeContentColor: Color? = null,
    refreshTooltipText: String? = null,
    compact: Boolean = false,
    compactIcon: ImageVector = searchIcon,
    compactContentDescription: String = searchContentDescription,
    compactBadgeColor: Color? = null,
    compactBadgeContentColor: Color? = null,
    compactTooltipText: String? = compactContentDescription,
    onCompactClick: (() -> Unit)? = null,
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchAutoFocusEnabled = LocalSearchAutoFocusEnabled.current
    val resolvedKeyboardLiftProvider =
        rememberAppFloatingKeyboardLiftProvider(
            keyboardLiftProvider = keyboardLiftProvider,
            keyboardLift = keyboardLift,
            focusedLift = focusedLift,
        )
    val visibleActionCount =
        (if (showAddAction) 1 else 0) +
            extraActions.size +
            (if (showRefreshAction) 1 else 0) +
            1
    // Every badge the collapsed button stands in for, summed. Supplying this from the call site is what
    // let the GitHub dock show its *refresh* count collapsed while showing its *unread history* count
    // expanded — the same corner reporting two unrelated things.
    val collapsedBadgeLabel =
        remember(extraActions, refreshBadgeLabel, showRefreshAction) {
            appFloatingDockCollapsedBadgeLabel(
                extraActions.map(AppFloatingDockAction::badgeLabel) +
                    listOf(refreshBadgeLabel.takeIf { showRefreshAction }),
            )
        }
    val dockHeight = appFloatingVerticalDockHeight(size, visibleActionCount)
    val dockMode =
        when {
            compact && expanded -> AppFloatingVerticalSearchDockMode.CompactSearch
            compact -> AppFloatingVerticalSearchDockMode.CompactButton
            expanded -> AppFloatingVerticalSearchDockMode.FullSearch
            else -> AppFloatingVerticalSearchDockMode.FullDock
        }
    val availableWidth = appWindowWidthDp() - horizontalInset * 2
    val compactSearchReservedWidth = size + gap
    val fullSearchFieldWidth = (availableWidth - size - gap).coerceAtLeast(0.dp)
    val compactSearchFieldWidth =
        (availableWidth - compactSearchReservedWidth - size - gap).coerceAtLeast(0.dp)
    val motion =
        rememberAppFloatingVerticalSearchDockMotion(
            mode = dockMode,
            dockSide = dockSide,
            expandedHeight = dockHeight,
            compactHeight = size,
            availableWidth = availableWidth,
            fullSearchFieldWidth = fullSearchFieldWidth,
            compactSearchFieldWidth = compactSearchFieldWidth,
            compactSearchReservedWidth = compactSearchReservedWidth,
            size = size,
            gap = gap,
        )
    val fieldInteractive by
        remember(expanded, motion.fieldWidth) {
            derivedStateOf {
                expanded && motion.fieldWidth() > AppFloatingSearchFieldMinimumInteractiveWidth
            }
        }

    LaunchedEffect(expanded) {
        if (!expanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val fieldContent: @Composable () -> Unit = {
        AppFloatingSearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            interactive = fieldInteractive,
            autoFocus = fieldInteractive && searchAutoFocusEnabled,
            onFocusActiveChange = { active ->
                if (active) onExpandedChange(true)
            },
            searchIcon = searchIcon,
            placeholder = placeholder,
            accent = accent,
            backdrop = backdrop,
            modifier =
                Modifier
                    .appFloatingDockAnimatedWidth(motion.fieldWidth)
                    .height(size)
                    .graphicsLayer { alpha = motion.fieldAlpha() },
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
                        badgeLabel = action.badgeLabel,
                        badgeColor = action.badgeColor,
                        badgeContentColor = action.badgeContentColor,
                        tooltipText = action.tooltipText,
                    )
                }
                if (showRefreshAction) {
                    AppFloatingVerticalDockAction(
                        icon = refreshIcon,
                        contentDescription = refreshContentDescription,
                        onClick = onRefreshClick,
                        size = size,
                        iconSize = iconSize,
                        iconTint = refreshTint,
                        enabled = refreshEnabled && refreshStatus != AppFloatingRefreshStatus.Refreshing,
                        rotating = refreshStatus == AppFloatingRefreshStatus.Refreshing,
                        badgeLabel = refreshBadgeLabel,
                        badgeColor = refreshBadgeColor,
                        badgeContentColor = refreshBadgeContentColor,
                        tooltipText = refreshTooltipText,
                    )
                }
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
    val compactContent: @Composable () -> Unit = {
        AppFloatingLiquidActionButton(
            backdrop = backdrop,
            icon = compactIcon,
            contentDescription = compactContentDescription,
            onClick = onCompactClick ?: { onExpandedChange(true) },
            size = size,
            iconSize = iconSize,
            iconTint = accent,
            tooltipText = compactTooltipText,
            badgeLabel = collapsedBadgeLabel,
            badgeColor = compactBadgeColor,
            badgeContentColor = compactBadgeContentColor,
        )
    }
    val compactSearchContent: @Composable () -> Unit = {
        AppFloatingLiquidActionButton(
            backdrop = backdrop,
            icon = searchIcon,
            contentDescription = searchContentDescription,
            onClick = { onExpandedChange(false) },
            size = size,
            iconSize = iconSize,
            iconTint = accent,
        )
    }

    Box(
        modifier =
            modifier
                .appFloatingDockLift(resolvedKeyboardLiftProvider)
                .appFloatingDockAnimatedWidth(motion.width)
                .appFloatingDockAnimatedHeight(motion.height),
    ) {
        if (expanded && fieldInteractive) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(x = motion.fieldX().roundToPx(), y = 0) },
            ) {
                fieldContent()
            }
        }
        Box(
            modifier =
                motion.fullDockModifier
                    .keepComposedUnplaced(motion.showFullDockContent)
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(x = motion.fullDockX().roundToPx(), y = 0) },
        ) {
            dockContent()
        }
        Box(
            modifier =
                motion.compactButtonModifier
                    .keepComposedUnplaced(motion.showCompactButtonContent)
                    .align(
                        if (dockSide == AppFloatingDockSide.Start) {
                            Alignment.BottomStart
                        } else {
                            Alignment.BottomEnd
                        },
                    ),
        ) {
            compactContent()
        }
        if (motion.showCompactSearchButtonContent) {
            Box(
                modifier =
                    motion.compactSearchButtonModifier
                        .align(Alignment.BottomEnd),
            ) {
                compactSearchContent()
            }
        }
    }
}

@Composable
fun AppFloatingVerticalActionDock(
    backdrop: Backdrop?,
    actions: List<AppFloatingDockAction>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    compactIcon: ImageVector? = null,
    compactContentDescription: String? = null,
    compactBadgeColor: Color? = null,
    compactBadgeContentColor: Color? = null,
    compactTooltipText: String? = null,
    onCompactClick: (() -> Unit)? = null,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
) {
    if (actions.isEmpty()) return
    val dockHeight = appFloatingVerticalDockHeight(size, actions.size)
    val firstAction = actions.first()
    // Derived, never supplied. Collapsed, this one button stands in for every action, so its badge has
    // to be the sum of theirs — see [appFloatingDockCollapsedBadgeLabel].
    val collapsedBadgeLabel =
        remember(actions) { appFloatingDockCollapsedBadgeLabel(actions.map(AppFloatingDockAction::badgeLabel)) }
    val compactMotion =
        rememberAppFloatingVerticalDockCompactMotion(
            compact = compact,
            expandedHeight = dockHeight,
            compactHeight = size,
            label = "app_vertical_floating_action_dock",
        )
    Box(
        modifier =
            modifier
                .width(size)
                .appFloatingDockAnimatedHeight(compactMotion.height),
    ) {
        // Each form is wrapped in a Box that carries keepComposedUnplaced, rather than handing the modifier to
        // the component: AppFloatingLiquidActionButton puts its modifier inside a TooltipBox, so that wrapper
        // stayed placed with its long-click semantics while the button was hidden, and it covered the first
        // action in the accessibility tree. TalkBack and uiautomator lost that action (on BA, the calendar).
        Box(
            modifier =
                Modifier
                    .keepComposedUnplaced(compactMotion.showExpandedContent)
                    .align(Alignment.BottomCenter),
        ) {
            AppFloatingLiquidVerticalDockSurface(
                backdrop = backdrop,
                modifier =
                    compactMotion.expandedModifier
                        .width(size)
                        .height(dockHeight),
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
                            badgeLabel = action.badgeLabel,
                            badgeColor = action.badgeColor,
                            badgeContentColor = action.badgeContentColor,
                            tooltipText = action.tooltipText,
                        )
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .keepComposedUnplaced(compactMotion.showCompactContent)
                    .align(Alignment.BottomCenter),
        ) {
            AppFloatingLiquidActionButton(
                backdrop = backdrop,
                icon = compactIcon ?: firstAction.icon,
                contentDescription = compactContentDescription ?: firstAction.contentDescription,
                onClick = onCompactClick ?: firstAction.onClick,
                size = size,
                iconSize = iconSize,
                iconTint = firstAction.iconTint,
                tooltipText = compactTooltipText ?: firstAction.tooltipText ?: compactContentDescription ?: firstAction.contentDescription,
                badgeLabel = collapsedBadgeLabel,
                badgeColor = compactBadgeColor ?: firstAction.badgeColor,
                badgeContentColor = compactBadgeContentColor ?: firstAction.badgeContentColor,
                modifier = compactMotion.compactModifier,
            )
        }
    }
}

@Composable
private fun rememberAppFloatingVerticalDockCompactMotion(
    compact: Boolean,
    expandedHeight: Dp,
    compactHeight: Dp,
    label: String,
): AppFloatingVerticalDockCompactMotion {
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val transition = updateTransition(targetState = compact, label = "${label}_compact")
    val heightState =
        transition.animateDp(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            AppFloatingVerticalDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "${label}_height",
        ) { compactTarget ->
            if (compactTarget) compactHeight else expandedHeight
        }
    val progressState =
        transition.animateFloat(
            transitionSpec = {
                tween(
                    durationMillis =
                        resolvedMotionDuration(
                            AppFloatingVerticalDockCompactMotionMs,
                            animationsEnabled,
                        ),
                    easing = FastOutSlowInEasing,
                )
            },
            label = "${label}_progress",
        ) { compactTarget ->
            if (compactTarget) 0f else 1f
        }
    val heightProvider = remember(heightState) { { heightState.value } }
    val progressProvider = remember(progressState) { { progressState.value } }
    return AppFloatingVerticalDockCompactMotion(
        height = heightProvider,
        showExpandedContent = !transition.currentState || !transition.targetState,
        showCompactContent = transition.currentState || transition.targetState,
        expandedModifier =
            Modifier.graphicsLayer {
                val progress = progressProvider()
                alpha = progress
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.90f + 0.10f * progress
            },
        compactModifier =
            Modifier.graphicsLayer {
                val progress = 1f - progressProvider()
                alpha = progress
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.88f + 0.12f * progress
            },
    )
}

private data class AppFloatingVerticalDockCompactMotion(
    val height: () -> Dp,
    val showExpandedContent: Boolean,
    val showCompactContent: Boolean,
    val expandedModifier: Modifier,
    val compactModifier: Modifier,
)

private fun Modifier.appFloatingDockAnimatedHeight(height: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val heightPx = height().roundToPx().coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = heightPx,
                    maxHeight = heightPx,
                ),
            )
        layout(placeable.width, heightPx) {
            placeable.place(0, 0)
        }
    }

private const val AppFloatingVerticalDockCompactMotionMs = 240

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
    badgeLabel: String? = null,
    badgeColor: Color? = null,
    badgeContentColor: Color? = null,
    tooltipText: String? = null,
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

    val actionContent: @Composable () -> Unit = {
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
                    ).then(disabledContentAlphaModifier(enabled = enabled || rotating))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            AppFloatingVerticalDockActionIcon(
                icon = icon,
                contentDescription = contentDescription,
                buttonSize = size,
                iconSize = iconSize,
                iconTint = animatedTintProvider(),
                badgeLabel = badgeLabel,
                badgeColor = badgeColor,
                badgeContentColor = badgeContentColor,
                modifier =
                    Modifier
                        .graphicsLayer {
                            val pressedScale = pressedScaleProvider()
                            scaleX = pressedScale
                            scaleY = pressedScale
                            rotationZ = if (rotating) rotationProvider() else 0f
                        },
            )
        }
    }
    val resolvedTooltipText = (tooltipText ?: contentDescription).takeIf { it.isNotBlank() }
    if (resolvedTooltipText != null && enabled) {
        TooltipBox(
            text = resolvedTooltipText,
            positioning = TooltipAnchorPosition.Above,
            content = actionContent,
        )
    } else {
        actionContent()
    }
}

@Composable
private fun AppFloatingVerticalDockActionIcon(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: Dp,
    iconSize: Dp,
    iconTint: Color,
    badgeLabel: String?,
    badgeColor: Color?,
    badgeContentColor: Color?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier.size(buttonSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier.size(iconSize),
            tint = iconTint,
        )
        AppLiquidIconBadge(
            label = badgeLabel,
            color = badgeColor,
            contentColor = badgeContentColor,
            // The dock's own surface, so the badge refracts the dock rather than the page behind it.
            backdrop = LocalAppFloatingDockBackdrop.current,
            // Solved against the capsule, not its bounding box: `TopEnd` plus a hand-tuned offset put
            // the badge's corner outside the rim, where the dock's capsule clip shaved it.
            modifier = Modifier.appRoundHostBadgePlacement(),
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
private val AppFloatingSearchFieldMinimumInteractiveWidth = 1.dp

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
    interactive: Boolean,
    autoFocus: Boolean,
    onFocusActiveChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    placeholder: String,
    accent: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(interactive, autoFocus) {
        if (!interactive) {
            focusManager.clearFocus()
            keyboardController?.hide()
        } else if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    AppLiquidInputField(
        value = query,
        onValueChange = { value ->
            if (interactive) onQueryChange(value)
        },
        label = placeholder,
        backdrop = backdrop,
        modifier =
            modifier.then(
                if (interactive) {
                    Modifier
                } else {
                    Modifier
                        .focusProperties { canFocus = false }
                        .clearAndSetSemantics {}
                },
            ),
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
                    if (interactive) onFocusActiveChange(false)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            ),
        focusRequester = focusRequester.takeIf { interactive },
        onFocusActiveChange = { active -> onFocusActiveChange(active && interactive) },
        leadingContent = {
            Icon(
                imageVector = searchIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(27.dp),
            )
        },
    )
}
