package os.kei.ui.page.main.widget.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Widest a page's content column is allowed to get before the surplus becomes gutter.
 *
 * Every row in this app is drawn as "label at the leading edge, controls at the trailing edge". At a phone's
 * ~426dp that reads as one object. Stretched to a tablet's 1280dp the two halves end up roughly 1100dp apart
 * with nothing in between, and the row stops reading as a row — the eye has to travel the width of the panel
 * to connect a switch to the thing it switches.
 *
 * 720dp is the measured answer to that, not a breakpoint borrowed from a spec. It is wide enough that the
 * tablet does not look like a scaled-up phone, and narrow enough that a label and its control stay in one
 * glance. It also lands *below* the Pad AVD's portrait width (800dp) and well below its landscape width
 * (1280dp), so a row is laid out identically in both orientations — rotating the tablet re-flows nothing,
 * which is the property that makes rotation on a large screen unremarkable now that the app can no longer
 * refuse it.
 */
val AppPageContentMaxWidth: Dp = 720.dp

/**
 * Smallest width a device must have *in its narrow dimension* before a page may lay out in two columns.
 *
 * The gate is `smallestScreenWidthDp`, not the current width, and that is the whole point: it describes the
 * **device**, not the moment. A phone is 360–440dp on its narrow side and stays there however it is held, so
 * it can never reach this — which matters because a phone turned on its side is ~817dp wide and would
 * otherwise sail past a width-only test and get a tablet layout on a 375dp-tall screen. A fold's outer screen
 * fails it for the same reason and its inner screen passes, which is the distinction that actually matters.
 *
 * 600dp because that is the line Android itself draws — the `sw600dp` resource qualifier, and the width at
 * which targetSdk 36 stops honouring a portrait lock. Borrowing it means the app agrees with the platform
 * about what a tablet is rather than inventing a second answer.
 */
val AppLargeScreenMinSmallestWidth: Dp = 600.dp

/** Gap between the two content columns, when a page is laid out in two. */
val AppPageColumnGap: Dp = 16.dp

/** Whether this is a tablet or an unfolded fold, from the device's narrow dimension. See [AppLargeScreenMinSmallestWidth]. */
fun appLargeScreenDeviceFor(smallestWidthDp: Dp): Boolean = smallestWidthDp >= AppLargeScreenMinSmallestWidth

/**
 * How many columns a page that opts into the wide layout gets.
 *
 * Two conditions, because they answer different questions. [largeScreenDevice] asks "is this hardware a
 * tablet or an unfolded fold" and never changes as the device is turned. [availableWidth] asks "is there room
 * *now*", and [AppDualPaneMinWidth] is the answer the codebase already derived for it: two columns of
 * [AppPaneMinWidth], the narrowest this app's rows have ever been laid out for. A large-screen device in a
 * narrowed split-screen window therefore drops back to one column, which is right — the window is
 * phone-shaped even though the device is not.
 */
fun appPageColumnCountFor(
    availableWidth: Dp,
    largeScreenDevice: Boolean,
): Int = if (largeScreenDevice && availableWidth >= AppDualPaneMinWidth) 2 else 1

/**
 * The content cap for [columnCount] columns.
 *
 * Two columns are allowed twice the single column's width plus the gap between them, so each one lands close
 * to [AppPageContentMaxWidth] rather than half of it. Anything wider still becomes gutter: on the Pad at
 * 1280dp this consumes the whole panel and leaves none, and only past ~1456dp would a two-column page start
 * centring itself again.
 */
fun appPageContentMaxWidthFor(columnCount: Int): Dp =
    if (columnCount >= 2) AppPageContentMaxWidth * 2f + AppPageColumnGap else AppPageContentMaxWidth

/**
 * Extra inset each side needs so a content column of at most [maxContentWidth] sits centred in
 * [availableWidth].
 *
 * Returns **zero below the cap**, which is the important half of the contract: every phone this app installs
 * on is 360–440dp wide, so this is identically `0.dp` there and no phone layout moves by a pixel. The gutter
 * only ever appears on a panel that has width to spare.
 *
 * Pure, so the arithmetic can be pinned by a test rather than read off a screenshot.
 */
fun appPageSideGutterFor(
    availableWidth: Dp,
    maxContentWidth: Dp = AppPageContentMaxWidth,
    minimumGutter: Dp = 0.dp,
): Dp = ((availableWidth - maxContentWidth) / 2f).coerceAtLeast(minimumGutter)

/**
 * The gutter for the space the caller is actually laid out in.
 *
 * [appContentWidth] resolves to the enclosing pane when there is one and the app window otherwise — never the
 * display. Both fallbacks matter: a split-screen host narrows the window, and a dual-pane layout narrows the
 * pane inside it. Centring a pane's content against the full window would push it clean out of its own pane.
 *
 * Everything that anchors to a page edge should add this: the content padding of the lists, and equally the
 * chrome that floats over them. An overlay pinned to the true window edge while the content sits 280dp inside
 * it is worse than no gutter at all — the actions stop belonging to the page they act on.
 */
/**
 * The content cap the page being composed is actually using.
 *
 * Published by the page, because the top row is a *sibling* of the list rather than its parent: a page that
 * widens its column has to tell its own chrome, or the back button centres on a column that is no longer
 * there. That was visible the moment Settings went to two columns — content from 14dp, back button from
 * 294dp, the two laid out against different pages.
 *
 * Defaults to the single-column cap, so a page that never opts in is unchanged.
 */
val LocalAppPageContentMaxWidth = compositionLocalOf { AppPageContentMaxWidth }

/**
 * Edge inset a page keeps once its column is wide enough to reach the window edge.
 *
 * The single-column cap always leaves a gutter on a large window, so this never applied before. A two-column
 * page consumes the whole panel, and then all that stands between a card and the bezel is
 * `pageHorizontalPadding` — which is a *phone* margin, and reads on a 1280dp panel exactly the way the top
 * row's 14dp did before [AppTopBarRegularEdgePadding] stepped it up. Same reasoning, same step: twice the
 * phone margin rather than a new number.
 */
val AppWideContentEdgeInset: Dp = AppChromeTokens.pageHorizontalPadding

@Composable
fun appPageSideGutter(maxContentWidth: Dp = LocalAppPageContentMaxWidth.current): Dp =
    appPageSideGutterFor(
        availableWidth = appContentWidth(),
        maxContentWidth = maxContentWidth,
        // A wider-than-single-column cap is what a page passes when it lays out in columns, and it is the
        // only case where the gutter can reach zero on a large screen.
        minimumGutter = if (maxContentWidth > AppPageContentMaxWidth) AppWideContentEdgeInset else 0.dp,
    )

/**
 * Columns for the space the caller is laid out in, on the device it is running on.
 *
 * Opt-in: a page gets this only by asking, because two columns are right for a page of independent cards and
 * wrong for one long ordered list. [appContentWidth] rather than the display, so a pane or a split-screen
 * window narrows it; `smallestScreenWidthDp` for the device half, which no orientation changes.
 */
@Composable
fun appPageColumnCount(): Int =
    appPageColumnCountFor(
        availableWidth = appContentWidth(),
        largeScreenDevice = appLargeScreenDeviceFor(LocalConfiguration.current.smallestScreenWidthDp.dp),
    )

/**
 * Horizontal padding for something that spans the page and is laid out *outside* a list's content padding.
 *
 * The status hub each main page pins above its list is the case this exists for: it is a sibling of the list,
 * not an item in it, so it never sees [appPageContentPadding] and would stay full-bleed while every row below
 * it narrowed. Anything that reaches the page's own left and right edges should use this instead of reading
 * `AppChromeTokens.pageHorizontalPadding` directly — that token is still correct *inside* a card, where there
 * is no page edge to centre against.
 */
@Composable
fun appPageEdgePadding(): Dp = AppChromeTokens.pageHorizontalPadding + appPageSideGutter()

/** Gap between a page-edge floating dock and the edge of the content column it belongs to. */
val AppFloatingDockEdgeSpacing: Dp = 14.dp

/**
 * Leading-edge padding for a floating action dock, when the dock is aligned to that edge.
 *
 * [isDockSide] is whether *this* edge is the one the dock is aligned to; the opposite edge gets zero, because
 * only the aligned side's padding moves an edge-aligned child. Four pages placed this dock with the same
 * hand-written pair of expressions and the same `14.dp`; they now share one, which is also what makes moving
 * them all a single change rather than four.
 *
 * The **chrome** gutter, not the content one, and that is the whole design of it. A dock that followed its
 * page's content column ended up against the bezel of a 1280dp panel — geometrically consistent with the
 * cards, and the least reachable spot on the whole device, since a hand holding a tablet reaches *inward*.
 * On the single-column cap it lands exactly where the About and Settings pages already put their search
 * button, because [AppFloatingDockEdgeSpacing] and `pageHorizontalPadding` are the same 14dp: one reachable
 * column of controls whatever the page behind it does with its width.
 *
 * One function per edge, because the two edges genuinely differ once a sidebar is up — the same asymmetry
 * [appPageSideGutterStart] and [appPageEdgePaddingStart] already carry. A single side-agnostic helper took the
 * trailing gutter for both, so the dock landed *underneath* the rail whenever the grip-aware side flipped to
 * leading, which on a tablet is just holding it in the other hand.
 */
@Composable
fun appFloatingDockStartPadding(isDockSide: Boolean): Dp =
    if (isDockSide) AppFloatingDockEdgeSpacing + appBottomChromeSideGutterStart() else 0.dp

/** Trailing half of [appFloatingDockStartPadding]. Nothing floats against the trailing edge, so just the gutter. */
@Composable
fun appFloatingDockEndPadding(isDockSide: Boolean): Dp =
    if (isDockSide) AppFloatingDockEdgeSpacing + appBottomChromeSideGutterEnd() else 0.dp
