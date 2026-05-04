package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmFloatingBottomChrome(
    accent: Color,
    scrollState: BaGuideBgmBottomChromeScrollState,
    currentTrackTitle: String,
    artworkImageUrl: String = "",
    isPlaying: Boolean,
    playbackProgress: Float,
    onPlaybackProgressChange: (Float) -> Unit,
    onPlaybackProgressChangeFinished: (Float) -> Unit,
    onPlaybackSliderInteractionChanged: (Boolean) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    searchVisible: Boolean,
    searchInputActive: Boolean,
    searchQuery: String,
    searchPlaceholder: String? = null,
    onSearchQueryChange: (String) -> Unit,
    onSearchInputActiveChange: (Boolean) -> Unit,
    selectedDockKey: String,
    onSelectedDockKeyChange: (String) -> Unit,
    onCompactDockClick: () -> Unit,
    onSearchClick: () -> Unit,
    backdrop: Backdrop,
    dockTabs: List<BaGuideBgmDockTab>? = null,
    modifier: Modifier = Modifier
) {
    val defaultTabs = rememberBaGuideBgmDockTabs()
    val tabs = dockTabs ?: defaultTabs
    val animationsEnabled = LocalTransitionAnimationsEnabled.current
    val miniPlayerInteractionSource = remember { MutableInteractionSource() }
    val dockSurfaceInteractionSource = remember { MutableInteractionSource() }
    val searchFocusRequester = remember { FocusRequester() }
    val searchMode = when {
        searchInputActive -> BaGuideBgmBottomChromeMode.SearchInput
        searchVisible -> BaGuideBgmBottomChromeMode.SearchExpanded
        scrollState.isCompact -> BaGuideBgmBottomChromeMode.Compact
        else -> BaGuideBgmBottomChromeMode.Expanded
    }
    val transition = updateTransition(
        targetState = searchMode,
        label = "debug_bgm_bottom_chrome"
    )
    val animationSpec = tween<Dp>(
        durationMillis = resolvedMotionDuration(BaGuideBgmBottomChromeSizeMotionMs, animationsEnabled),
        easing = FastOutSlowInEasing
    )
    val floatAnimationSpec = tween<Float>(
        durationMillis = resolvedMotionDuration(BaGuideBgmBottomChromeFadeMotionMs, animationsEnabled),
        easing = FastOutSlowInEasing
    )
    val containerHeight by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_chrome_height"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.SearchInput,
            BaGuideBgmBottomChromeMode.Compact -> BaGuideBgmCompactChromeHeight
            BaGuideBgmBottomChromeMode.Expanded,
            BaGuideBgmBottomChromeMode.SearchExpanded -> BaGuideBgmExpandedChromeHeight
        }
    }
    val tabGroupHeight by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_tab_height"
    ) {
        BaGuideBgmExpandedDockHeight
    }
    val tabGroupY by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_tab_y"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Expanded,
            BaGuideBgmBottomChromeMode.SearchExpanded -> BaGuideBgmExpandedDockY
            BaGuideBgmBottomChromeMode.Compact,
            BaGuideBgmBottomChromeMode.SearchInput -> BaGuideBgmCompactControlInset
        }
    }
    val miniPlayerHeight by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_mini_height"
    ) { mode ->
        if (mode == BaGuideBgmBottomChromeMode.SearchInput) {
            0.dp
        } else {
            BaGuideBgmExpandedMiniHeight
        }
    }
    val miniPlayerY by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_mini_y"
    ) {
        BaGuideBgmCompactMiniY
    }
    val searchSize by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_search_size"
    ) {
        BaGuideBgmExpandedDockHeight
    }
    val searchY by transition.animateDp(
        transitionSpec = { animationSpec },
        label = "debug_bgm_search_y"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Expanded,
            BaGuideBgmBottomChromeMode.SearchExpanded -> BaGuideBgmExpandedDockY
            BaGuideBgmBottomChromeMode.Compact,
            BaGuideBgmBottomChromeMode.SearchInput -> BaGuideBgmCompactControlInset
        }
    }
    val dockExpandedAlpha by transition.animateFloat(
        transitionSpec = { floatAnimationSpec },
        label = "debug_bgm_expanded_alpha"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Expanded -> 1f
            else -> 0f
        }
    }
    val dockCompactAlpha by transition.animateFloat(
        transitionSpec = { floatAnimationSpec },
        label = "debug_bgm_compact_alpha"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Expanded -> 0f
            else -> 1f
        }
    }
    val miniExpandedAlpha by transition.animateFloat(
        transitionSpec = { floatAnimationSpec },
        label = "debug_bgm_mini_expanded_alpha"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Compact -> 0f
            else -> 1f
        }
    }
    val miniCompactAlpha by transition.animateFloat(
        transitionSpec = { floatAnimationSpec },
        label = "debug_bgm_mini_compact_alpha"
    ) { mode ->
        when (mode) {
            BaGuideBgmBottomChromeMode.Compact -> 1f
            else -> 0f
        }
    }
    val miniPlayerAlpha by transition.animateFloat(
        transitionSpec = { floatAnimationSpec },
        label = "debug_bgm_mini_alpha"
    ) { mode ->
        if (mode == BaGuideBgmBottomChromeMode.SearchInput) 0f else 1f
    }
    val dockExpandedProgress = dockExpandedAlpha.coerceIn(0f, 1f)
    val dockCompactProgress = dockCompactAlpha.coerceIn(0f, 1f)
    val miniExpandedProgress = miniExpandedAlpha.coerceIn(0f, 1f)
    val miniCompactProgress = miniCompactAlpha.coerceIn(0f, 1f)
    val searchFieldVisible = searchMode.isSearchMode

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
    ) {
        val tabGroupExpandedWidth = (maxWidth - BaGuideBgmExpandedSearchSpacing - BaGuideBgmExpandedDockHeight)
            .coerceAtLeast(260.dp)
        val compactMiniWidth = boundedDp(
            value = maxWidth - (BaGuideBgmCompactControlSize * 2f) - (BaGuideBgmCompactMiniGap * 2f),
            min = 190.dp,
            max = BaGuideBgmCompactMiniMaxWidth
        )
        val searchFieldWidth = (maxWidth - BaGuideBgmCompactControlSize - BaGuideBgmSearchFieldSpacing)
            .coerceAtLeast(196.dp)
        val miniPlayerWidth by transition.animateDp(
            transitionSpec = { animationSpec },
            label = "debug_bgm_mini_width"
        ) { mode ->
            when (mode) {
                BaGuideBgmBottomChromeMode.Compact -> compactMiniWidth
                BaGuideBgmBottomChromeMode.SearchInput -> 0.dp
                BaGuideBgmBottomChromeMode.Expanded,
                BaGuideBgmBottomChromeMode.SearchExpanded -> maxWidth
            }
        }
        val miniPlayerX by transition.animateDp(
            transitionSpec = { animationSpec },
            label = "debug_bgm_mini_x"
        ) { mode ->
            if (mode == BaGuideBgmBottomChromeMode.Compact) {
                (maxWidth - compactMiniWidth) / 2f
            } else {
                0.dp
            }
        }
        val tabGroupWidth by transition.animateDp(
            transitionSpec = { animationSpec },
            label = "debug_bgm_tab_width"
        ) { mode ->
            when (mode) {
                BaGuideBgmBottomChromeMode.Expanded -> tabGroupExpandedWidth
                BaGuideBgmBottomChromeMode.Compact,
                BaGuideBgmBottomChromeMode.SearchExpanded,
                BaGuideBgmBottomChromeMode.SearchInput -> BaGuideBgmCompactControlSize
            }
        }
        val searchWidth by transition.animateDp(
            transitionSpec = { animationSpec },
            label = "debug_bgm_search_width"
        ) { mode ->
            if (mode.isSearchMode) searchFieldWidth else BaGuideBgmExpandedDockHeight
        }
        val searchX by transition.animateDp(
            transitionSpec = { animationSpec },
            label = "debug_bgm_search_x"
        ) { mode ->
            if (mode.isSearchMode) {
                BaGuideBgmCompactControlSize + BaGuideBgmSearchFieldSpacing
            } else {
                maxWidth - BaGuideBgmExpandedDockHeight
            }
        }

        if (miniPlayerHeight > 1.dp && miniPlayerWidth > 1.dp) {
            AppLiquidFloatingSurface(
                modifier = Modifier
                    .offset(x = miniPlayerX, y = miniPlayerY)
                    .width(miniPlayerWidth)
                    .height(miniPlayerHeight)
                    .graphicsLayer { alpha = miniPlayerAlpha },
                shape = ContinuousCapsule,
                backdrop = backdrop,
                interactionSource = miniPlayerInteractionSource,
                consumeTouches = true,
                pressDurationMillis = BaGuideBgmBottomPressMotionMs,
                pressLabel = "debug_bgm_bottom_surface_press"
            ) {
                BaGuideBgmChromeMiniPlayer(
                    accent = accent,
                    currentTrackTitle = currentTrackTitle,
                    artworkImageUrl = artworkImageUrl,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    onPlaybackProgressChange = onPlaybackProgressChange,
                    onPlaybackProgressChangeFinished = onPlaybackProgressChangeFinished,
                    onPlaybackSliderInteractionChanged = onPlaybackSliderInteractionChanged,
                    expandedProgress = miniExpandedProgress,
                    compactProgress = miniCompactProgress,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    controlInteractionSource = miniPlayerInteractionSource,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AppLiquidFloatingSurface(
            modifier = Modifier
                .offset(x = 0.dp, y = tabGroupY)
                .width(tabGroupWidth)
                .height(tabGroupHeight),
            shape = ContinuousCapsule,
            backdrop = backdrop,
            interactionSource = dockSurfaceInteractionSource,
            clipContent = false,
            pressDurationMillis = BaGuideBgmBottomPressMotionMs,
            pressLabel = "debug_bgm_bottom_surface_press"
        ) {
            BaGuideBgmDockGroupContent(
                tabs = tabs,
                selectedDockKey = selectedDockKey,
                accent = accent,
                expandedProgress = dockExpandedProgress,
                compactProgress = dockCompactProgress,
                backdrop = backdrop,
                compactInteractionSource = dockSurfaceInteractionSource,
                onSelectedDockKeyChange = onSelectedDockKeyChange,
                onCompactDockClick = onCompactDockClick
            )
        }

        AppLiquidFloatingSurface(
            modifier = Modifier
                .offset(x = searchX, y = searchY)
                .width(searchWidth)
                .height(searchSize),
            shape = if (searchFieldVisible) ContinuousCapsule else CircleShape,
            backdrop = backdrop,
            onClick = if (searchFieldVisible) null else onSearchClick,
            pressDurationMillis = BaGuideBgmBottomPressMotionMs,
            pressLabel = "debug_bgm_bottom_surface_press"
        ) {
            if (searchFieldVisible) {
                BaGuideBgmBottomSearchField(
                    query = searchQuery,
                    placeholder = searchPlaceholder,
                    onQueryChange = onSearchQueryChange,
                    focusRequester = searchFocusRequester,
                    onFocusActiveChange = onSearchInputActiveChange,
                    accent = accent,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BaGuideBgmDockTabIcon(
                    icon = appLucideSearchIcon(),
                    label = stringResource(R.string.debug_component_lab_nav_search),
                    selected = searchVisible,
                    accent = accent,
                    iconSize = 27.dp
                )
            }
        }
    }
}

@Composable
private fun BaGuideBgmBottomSearchField(
    query: String,
    placeholder: String?,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusActiveChange: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val resolvedPlaceholder = placeholder ?: stringResource(R.string.debug_component_lab_search_placeholder)
    val contentColor = MiuixTheme.colorScheme.onBackground
    val placeholderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f)
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = TextStyle(
        color = contentColor,
        fontSize = AppTypographyTokens.CardHeader.fontSize,
        lineHeight = AppTypographyTokens.CardHeader.lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    Row(
        modifier = modifier
            .clip(ContinuousCapsule)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onFocusActiveChange(true)
                focusRequester.requestFocus()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BaGuideBgmDockTabIcon(
            icon = appLucideSearchIcon(),
            label = stringResource(R.string.debug_component_lab_nav_search),
            selected = true,
            accent = accent,
            iconSize = 25.dp
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
                }
            ),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 24.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    onFocusActiveChange(state.isFocused)
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isBlank()) {
                        BasicText(
                            text = resolvedPlaceholder,
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

private fun boundedDp(
    value: Dp,
    min: Dp,
    max: Dp
): Dp = value.value.coerceIn(min.value, max.value).dp

private enum class BaGuideBgmBottomChromeMode {
    Expanded,
    Compact,
    SearchExpanded,
    SearchInput;

    val isSearchMode: Boolean
        get() = this == SearchExpanded || this == SearchInput
}

private val BaGuideBgmChromeControlHeight = AppChromeTokens.floatingBottomBarOuterHeight
private val BaGuideBgmChromeStackGap = AppChromeTokens.pageSectionGap
private val BaGuideBgmExpandedChromeHeight = BaGuideBgmChromeControlHeight * 2f + BaGuideBgmChromeStackGap
private val BaGuideBgmCompactChromeHeight = BaGuideBgmChromeControlHeight
private val BaGuideBgmExpandedMiniHeight = BaGuideBgmChromeControlHeight
private val BaGuideBgmCompactMiniY = 0.dp
private val BaGuideBgmExpandedDockHeight = BaGuideBgmChromeControlHeight
private val BaGuideBgmExpandedDockY = BaGuideBgmChromeControlHeight + BaGuideBgmChromeStackGap
private val BaGuideBgmExpandedSearchSpacing = AppChromeTokens.pageSectionGap
private val BaGuideBgmSearchFieldSpacing = 8.dp
private val BaGuideBgmCompactMiniGap = BaGuideBgmSearchFieldSpacing
private val BaGuideBgmCompactMiniMaxWidth = 288.dp
private val BaGuideBgmCompactControlSize = BaGuideBgmChromeControlHeight
private val BaGuideBgmCompactControlInset = 0.dp
private const val BaGuideBgmBottomChromeSizeMotionMs = 360
private const val BaGuideBgmBottomChromeFadeMotionMs = 240
private const val BaGuideBgmBottomPressMotionMs = 120
