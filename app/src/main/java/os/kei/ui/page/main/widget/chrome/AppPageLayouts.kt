@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.appMotionDpState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

internal val LocalAppScaffoldContainerColor = staticCompositionLocalOf<Color?> { null }

/**
 * True when a managed background is painting this page, so page-level fills must step aside.
 *
 * The non-Home background is meant to apply everywhere except Home, but a page that paints its own
 * opaque plate covers it and the setting silently does nothing there. Rather than delete those plates —
 * the BA calendar and pool use theirs for a designed accent wash, and it doubles as their glass
 * backdrop producer — a page asks whether it is being backed and drops only the opaque part.
 *
 * Reads the same signal `AppManagedBackgroundHost` already publishes to make scaffolds transparent, so
 * there is one source of truth for "a background is active" rather than re-deriving it from prefs.
 *
 * **Only answers the question inside a route.** `MainPagerPageHost` makes every non-Home main page's
 * scaffold transparent whether or not a background is enabled, so on those pages this reads true
 * regardless. Anything that must know a background is genuinely painting — glass sampling it, for
 * instance — should ask [LocalAppManagedSceneBackdrop], which is only non-null when one is.
 */
@Composable
fun appManagedPageBackgroundActive(): Boolean = LocalAppScaffoldContainerColor.current == Color.Transparent

/**
 * The colour actually behind this page's content, which is what its glass should treat as its base.
 *
 * `MiuixTheme.colorScheme.surface` is not it. `AppScaffold` defaults its container to `surface`, but
 * `MainPagerPageHost` overrides every non-Home main page to transparent, leaving `MainPagerLayout`'s
 * `background(colorScheme.background)` as the visible base. miuix sets those two tokens apart —
 * `background` is White / `#242424` against `surface` = `#F7F7F7` / Black — so a page backdrop recorded
 * over `surface` had its glass sampling pure black while the page rendered `#242424`, 36 levels lighter.
 * Home keeps `surface`, because Home is the one page that really does paint it.
 */
@Composable
internal fun appPageBackdropBaseColor(): Color {
    val containerColor = LocalAppScaffoldContainerColor.current
    return when {
        containerColor == null -> MiuixTheme.colorScheme.surface
        containerColor == Color.Transparent -> MiuixTheme.colorScheme.background
        else -> containerColor
    }
}

/**
 * [sideGutter] is the large-screen centring inset from [appPageSideGutter]; it is `0.dp` on every phone, so
 * the default keeps this function's old behaviour exactly. Call sites that can see a composition should pass
 * the real one — [AppPageLazyColumn] does.
 */
fun appPageContentPadding(
    innerPadding: PaddingValues,
    bottomExtra: Dp = AppChromeTokens.pageBottomInsetExtra,
    topExtra: Dp = 0.dp,
    sideGutter: Dp = 0.dp,
): PaddingValues =
    PaddingValues(
        top = innerPadding.calculateTopPadding() + topExtra,
        bottom = innerPadding.calculateBottomPadding() + bottomExtra,
        start = AppChromeTokens.pageHorizontalPadding + sideGutter,
        end = AppChromeTokens.pageHorizontalPadding + sideGutter,
    )

fun appPageBottomPaddingWithFloatingOverlay(contentBottomPadding: Dp): Dp =
    contentBottomPadding + AppChromeTokens.pageFloatingOverlayBottomExtra

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val resolvedContainerColor =
        containerColor
            ?: LocalAppScaffoldContainerColor.current
            ?: MiuixTheme.colorScheme.surface
    MiuixScaffold(
        modifier = modifier,
        containerColor = resolvedContainerColor,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingToolbar = floatingToolbar,
        floatingToolbarPosition = floatingToolbarPosition,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun AppPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    scrollBehavior: ScrollBehavior? = null,
    topBarColor: Color = Color.Transparent,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleBackdrop: Backdrop? = null,
    reserveTopEndActionSpace: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appPageSearch",
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null,
    onTitleClick: () -> Unit = {},
    /**
     * Widest this page's content column may get.
     *
     * Published to the whole page rather than handed to the list, because the top row is laid out beside the
     * content, not inside it: both ends of that row centre against this, so a page that widens its column
     * without saying so leaves its own back button behind on the old one.
     */
    contentMaxWidth: Dp = AppPageContentMaxWidth,
    content: @Composable (PaddingValues) -> Unit,
) {
    val hasSearchBar = searchBarContent != null
    val searchBarPlaceholderHeightState =
        appMotionDpState(
            targetValue =
                if (hasSearchBar && searchBarVisible) {
                    AppChromeTokens.searchBarHostHeight
                } else {
                    0.dp
                },
            durationMillis = AppMotionTokens.searchBarSlideMs,
            label = "${searchBarAnimationLabelPrefix}ScaffoldPadding",
        )
    val currentContent = rememberUpdatedState(content)
    val scaffoldTopBar: @Composable () -> Unit =
        remember(
            scrollBehavior,
            topBarColor,
        ) {
            {
                AppTopBarSection(
                    title = "",
                    largeTitle = "",
                    scrollBehavior = scrollBehavior,
                    color = topBarColor,
                )
            }
        }
    CompositionLocalProvider(LocalAppPageContentMaxWidth provides contentMaxWidth) {
        Box(modifier = modifier) {
            AppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = scaffoldTopBar,
                bottomBar = bottomBar,
                floatingToolbar = floatingToolbar,
                floatingToolbarPosition = floatingToolbarPosition,
                snackbarHost = snackbarHost,
                content = { innerPadding ->
                    val layoutDirection = LocalLayoutDirection.current
                    val searchBarPlaceholderHeight = searchBarPlaceholderHeightState.value
                    val adjustedPadding =
                        remember(innerPadding, layoutDirection, searchBarPlaceholderHeight) {
                            PaddingValues(
                                top = innerPadding.calculateTopPadding() + searchBarPlaceholderHeight,
                                bottom = innerPadding.calculateBottomPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            )
                        }
                    currentContent.value(adjustedPadding)
                },
            )
            AppTopBarSection(
                title = title,
                largeTitle = largeTitle,
                scrollBehavior = scrollBehavior,
                color = Color.Transparent,
                navigationIcon = navigationIcon,
                titleBackdrop = titleBackdrop,
                titleEndReserve =
                    if (reserveTopEndActionSpace) {
                        AppChromeTokens.topBarTitleActionReserve
                    } else {
                        null
                    },
                onTitleClick = onTitleClick,
                searchBarVisible = searchBarVisible,
                searchBarAnimationLabelPrefix = searchBarAnimationLabelPrefix,
                searchBarContent = searchBarContent,
            )
            AppTopEndActionBarOverlay {
                Row {
                    actions()
                }
            }
        }
    }
}

@Composable
fun AppPageLazyColumn(
    innerPadding: PaddingValues,
    state: LazyListState,
    modifier: Modifier = Modifier,
    bottomExtra: Dp = AppChromeTokens.pageBottomInsetExtra,
    topExtra: Dp = AppChromeTokens.topBarToHeaderGap,
    sectionSpacing: Dp = AppChromeTokens.pageSectionGapLarge,
    userScrollEnabled: Boolean = true,
    /**
     * Widest this page's content column may get before the surplus becomes gutter.
     *
     * Opt-in, and the default is the single-column cap every page has had. A page that lays its cards out in
     * two columns passes [appPageContentMaxWidthFor], which roughly doubles it — otherwise two columns would
     * be carved out of one column's width and each half would be narrower than a phone.
     */
    maxContentWidth: Dp = LocalAppPageContentMaxWidth.current,
    // Follows LocalOverscrollFactory: MiuixOverscrollFactory app-wide (spring placement
    // translation, no RenderEffect), lifting the 767b191c3 global disable.
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        overscrollEffect = overscrollEffect,
        userScrollEnabled = userScrollEnabled,
        contentPadding =
            appPageContentPadding(
                innerPadding = innerPadding,
                bottomExtra = bottomExtra,
                topExtra = topExtra,
                // Padding rather than a width constraint on the list itself, so the scroll surface, the
                // overscroll stretch and the edge-stacked card pile still span the whole panel. Only the
                // content is centred; the gesture area is not narrowed.
                sideGutter = appPageSideGutter(maxContentWidth),
            ),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        content = content,
    )
}

/**
 * [AppPageLazyColumn]'s layout contract, in columns that pack independently.
 *
 * The same content padding, the same gutter, the same section rhythm — only the flow differs. A staggered
 * grid rather than rows of equal-height cells because these cards are accordions: pairing them by row means
 * a collapsed card sits beside an expanded one and the shorter side leaves a dead half-column, which is the
 * exact complaint this exists to answer. Each column here takes the next card as soon as it has room, so a
 * tall card on the left simply lets the right column run ahead.
 *
 * Still lazy, which is the reason not to hand-build two [androidx.compose.foundation.layout.Column]s inside a
 * single list item: that would give the same packing while composing every card at once, and the Interface
 * category alone is nine cards of sliders and pickers.
 */
@Composable
fun AppPageStaggeredGrid(
    innerPadding: PaddingValues,
    state: LazyStaggeredGridState,
    columnCount: Int,
    modifier: Modifier = Modifier,
    bottomExtra: Dp = AppChromeTokens.pageBottomInsetExtra,
    topExtra: Dp = AppChromeTokens.topBarToHeaderGap,
    sectionSpacing: Dp = AppChromeTokens.pageSectionGapLarge,
    userScrollEnabled: Boolean = true,
    maxContentWidth: Dp = LocalAppPageContentMaxWidth.current,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnCount),
        modifier = modifier,
        state = state,
        userScrollEnabled = userScrollEnabled,
        contentPadding =
            appPageContentPadding(
                innerPadding = innerPadding,
                bottomExtra = bottomExtra,
                topExtra = topExtra,
                sideGutter = appPageSideGutter(maxContentWidth),
            ),
        verticalItemSpacing = sectionSpacing,
        horizontalArrangement = Arrangement.spacedBy(AppPageColumnGap),
        content = content,
    )
}
