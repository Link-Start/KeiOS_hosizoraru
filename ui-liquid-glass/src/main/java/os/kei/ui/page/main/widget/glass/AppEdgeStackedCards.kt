@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS-style card piling for card-dense lazy pages.
 *
 * Cards leaving through the top of the viewport pin at the page's content start line and recede:
 * they rise a little per depth level, scale down, and **darken** until they retire. A page opts in
 * with one provider plus [AppEdgeStackKeepAlive] around its lazy column; the card wrappers pick the
 * effect up through [rememberAppEdgeStackSlot]. Pages without the provider render byte-identically
 * to a page that has never heard of stacking — the slot resolves to [AppEdgeStackSlot.Inert], which
 * contributes no modifier, no layer and no measurement.
 *
 * ## Depth darkens, it does not dissolve
 *
 * This is the part that was wrong before, and it is worth being precise about because the fix is not
 * a matter of taste. Every recession treatment Apple documents **adds** something to the surface
 * going behind: macOS sheets dim the parent window, visionOS sheets dim the parent, and watchOS is
 * explicit that "the system applies a material to the background that blurs and desaturates the
 * covered content". The Materials guidance offers one concrete opacity anywhere in this area — "a
 * dark dimming layer of 35% opacity" — and it is additive too. Apple's four depth channels for
 * Liquid Glass are lensing, a content-adaptive shadow, simulated thickness and highlights; opacity
 * of the receding surface is not among them.
 *
 * The previous implementation encoded depth *only* as `alpha`, on the single channel Apple never
 * uses, and drove it all the way to zero. Over a light page a receding card therefore got lighter
 * and dissolved — the exact opposite of receding — and at low alpha it could no longer carry a
 * shadow or an edge for light to bend at, so it lost the cues that would have sold the depth. That
 * is why the pile read as nothing more than translucency.
 *
 * So: [AppEdgeStackTransform.dim] is the primary depth channel, [AppEdgeStackTransform.scale] and
 * the rise are the geometry, and [AppEdgeStackTransform.fade] is only a retirement tail — it exists
 * so a card cannot pop out of existence when the lazy layout disposes it, not to express depth.
 * Because the depth signal is geometric plus a solid dim rather than translucency, it also survives
 * a reduced-transparency setting, which Apple requires of the material.
 *
 * ## The numbers here are ours
 *
 * Apple publishes none of them. There is no stated figure anywhere for how many cards are visible in
 * a pile, nor for inset, scale, opacity or corner radius per depth level, nor any spring or duration
 * for a stacking transition. The two caps Apple does state are "display only one sheet at a time"
 * and exactly two Live Activities in the Dynamic Island, and its crowding strategy is always to
 * degrade or drop to one rather than grow the stack — so a small bounded depth is the Apple-shaped
 * choice, and [APP_EDGE_STACK_LEVELS] is our pick within it.
 */
@Stable
class AppEdgeStackState internal constructor() {
    /**
     * Distance from the lazy container's top edge at which cards stop travelling and start piling.
     *
     * Mutable rather than constructor-captured on purpose. Three hosts (the GitHub notification
     * history, the guide catalog and the memory lobby) derive this at runtime from whether their
     * pinned hub is showing. When the state was `remember`ed *keyed* on the pixel value, every flip
     * of that condition built a fresh state whose container position reset to unknown, and for a
     * whole positioning pass every card on the page rendered untransformed. Writing into a stable
     * state instead keeps the container attached across the flip. A density or font-scale change
     * used to re-key it for the same reason, and no longer does.
     */
    internal var stackLinePx by mutableFloatStateOf(0f)

    /**
     * Live coordinates of the lazy container, used to measure each card against the stack line.
     *
     * Snapshot-backed only so that the first non-null write invalidates placement once; the object
     * itself is a live view onto the container's coordinator, so reading a position off it during a
     * card's placement gives that card a *current-frame* answer even though this reference was
     * captured on an earlier pass.
     */
    internal var containerCoordinates by mutableStateOf<LayoutCoordinates?>(null)

    /**
     * Invisible viewport the host keeps above the visible top, in pixels. Zero unless the host wraps
     * its list in [AppEdgeStackKeepAlive].
     *
     * The pile's reachable depth is bounded by this — see `computeAppEdgeStackTransform`. It lives on
     * the state rather than being passed down so an unconverted host needs no change at all.
     */
    internal var keepAliveHeadroomPx by mutableFloatStateOf(0f)

    /**
     * Chrome that floats over the bottom of the list, in pixels. Zero unless the host declares it.
     *
     * Subtracted from the container to get the window a card has to fit in to be pinnable — see
     * `computeAppEdgeStackTransform`'s `readableHeightPx`. A page with a floating pager bar has a
     * strip at the bottom where a control is visible but not tappable, and a card that only "fits"
     * by parking its last row under that bar is exactly the case the bound exists to exclude.
     */
    internal var bottomInsetPx by mutableFloatStateOf(0f)
}

val LocalAppEdgeStackCards = compositionLocalOf<AppEdgeStackState?> { null }

@Composable
fun rememberAppEdgeStackState(stackLine: Dp): AppEdgeStackState {
    val stackLinePx = with(LocalDensity.current) { stackLine.toPx() }
    return remember { AppEdgeStackState() }.also { state ->
        if (state.stackLinePx != stackLinePx) {
            state.stackLinePx = stackLinePx
        }
    }
}

/**
 * Tags the lazy container whose top edge anchors the stack line.
 *
 * [onPlaced] rather than `onGloballyPositioned`: it runs inside the placement pass, so the container
 * is attached before the cards below it are placed on the very first frame rather than one frame
 * later.
 *
 * Private since every host adopted [AppEdgeStackKeepAlive]. It used to be the host-facing half of a
 * two-piece contract — tag the list, provide the state — and a host that shifted its list up while
 * still tagging the *list* would silently measure the stack line from the hidden top edge instead of
 * the visible one. With the only caller being the wrapper that owns both halves, that mistake is no
 * longer expressible.
 */
private fun Modifier.appEdgeStackContainer(state: AppEdgeStackState): Modifier =
    onPlaced { coordinates ->
        if (state.containerCoordinates !== coordinates) {
            state.containerCoordinates = coordinates
        }
    }

/**
 * Hosts a stacking lazy list with [headroom] of extra viewport above the visible top.
 *
 * The pile used to be about one card deep no matter what the depth constant said, because a pinned
 * card's layout position keeps travelling upward with the list and the lazy container disposes it
 * shortly after it leaves the viewport. Giving the list a taller viewport that extends *upward*, and
 * clipping it back to the visible bounds, keeps those items composed while they are pinned.
 *
 * Three things have to line up, which is why this is a composable rather than a modifier:
 *
 * 1. The list measures [headroom] taller and is placed at `-headroom`, so its viewport reaches above
 *    the visible area. Callers must add [headroom] to the list's own top content padding, or the first
 *    card starts that far off screen — [appEdgeStackKeepAliveTopPadding] exists to make that hard to
 *    forget.
 * 2. The outer box clips, or the off-screen part of the list draws over whatever sits above it.
 * 3. The outer box — not the list — is the stack container, so the stack line stays measured from the
 *    *visible* top edge. Tagging the shifted list instead would move the line up with it and every
 *    host would have to add the same headroom to its stack line too.
 */
@Composable
fun AppEdgeStackKeepAlive(
    state: AppEdgeStackState,
    modifier: Modifier = Modifier,
    headroom: Dp = AppEdgeStackKeepAliveHeadroom,
    /**
     * Chrome the page floats over the bottom of this list — a pager bar, a dock — plus whatever
     * inset lifts it off the window edge.
     *
     * Only the pinnable-height bound reads it, so a host that leaves it at zero behaves exactly as
     * before. A host that *has* floating bottom chrome should pass the same number its list reserves
     * as bottom content padding: a card whose last row can only be reached by parking it under the
     * bar is not reachable, and the bound is there to keep such a card out of the pile.
     */
    bottomInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val headroomPx = with(LocalDensity.current) { headroom.toPx() }
    if (state.keepAliveHeadroomPx != headroomPx) {
        state.keepAliveHeadroomPx = headroomPx
    }
    val bottomInsetPx = with(LocalDensity.current) { bottomInset.toPx() }
    if (state.bottomInsetPx != bottomInsetPx) {
        state.bottomInsetPx = bottomInsetPx
    }
    Box(
        modifier =
            modifier
                .clipToBounds()
                .appEdgeStackContainer(state),
    ) {
        Box(modifier = Modifier.appEdgeStackHeadroom(headroom)) {
            content()
        }
    }
}

/** The top content padding a list inside [AppEdgeStackKeepAlive] needs, given its own inset. */
fun appEdgeStackKeepAliveTopPadding(
    listTopInset: Dp = AppEdgeStackListTopInset,
    headroom: Dp = AppEdgeStackKeepAliveHeadroom,
): Dp = listTopInset + headroom

/**
 * Measures the child [headroom] taller than the incoming bounds and places it that far up.
 *
 * The node keeps reporting the original height, so nothing above or below it moves; only the child's
 * viewport grows, upward. Height is taken from `maxHeight`, so this needs a bounded parent — inside an
 * unbounded column it would have nothing to extend from, which is why the wrapper above owns it rather
 * than exposing it for arbitrary use.
 */
private fun Modifier.appEdgeStackHeadroom(headroom: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = headroom.roundToPx().coerceAtLeast(0)
        val visibleHeight = constraints.maxHeight
        if (!constraints.hasBoundedHeight || extra == 0) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val tall = visibleHeight + extra
        val placeable =
            measurable.measure(
                constraints.copy(minHeight = tall, maxHeight = tall),
            )
        layout(placeable.width, visibleHeight) {
            placeable.place(0, -extra)
        }
    }

/**
 * One card's live place in the pile.
 *
 * Written during the card's placement and read during the glass's draw. That direction matters:
 * Compose runs composition, then layout, then draw, so a value written in layout and read in draw
 * lands in the *same* frame. The previous implementation went the other way round — it wrote from
 * `onGloballyPositioned`, which fires after layout has finished, and read the result in the next
 * frame's placement — so during a fling every card in the pile trailed the list by a frame. That was
 * the whole of the "not smooth" complaint, and it is structural rather than a matter of tuning.
 */
@Stable
class AppEdgeStackCard internal constructor() {
    internal var stacked by mutableStateOf(false)
    internal var translationY by mutableFloatStateOf(0f)
    internal var scale by mutableFloatStateOf(1f)
    internal var dim by mutableFloatStateOf(0f)
    internal var contentAlpha by mutableFloatStateOf(1f)
    internal var contentBlur by mutableFloatStateOf(0f)
    internal var fade by mutableFloatStateOf(1f)

    internal fun rest() {
        if (!stacked) return
        stacked = false
        translationY = 0f
        scale = 1f
        dim = 0f
        contentAlpha = 1f
        contentBlur = 0f
        fade = 1f
    }

    internal fun apply(transform: AppEdgeStackTransform) {
        if (!stacked) stacked = true
        if (translationY != transform.translationY) translationY = transform.translationY
        if (scale != transform.scale) scale = transform.scale
        if (dim != transform.dim) dim = transform.dim
        if (contentAlpha != transform.contentAlpha) contentAlpha = transform.contentAlpha
        if (contentBlur != transform.contentBlur) contentBlur = transform.contentBlur
        if (fade != transform.fade) fade = transform.fade
    }
}

/**
 * What a page card needs to join the pile: a probe [modifier] to wear, and the [card] the glass
 * reads at draw time.
 *
 * Replaces three hand-rolled copies of the same wiring (AppSurfaceCard, GuideLiquidCard and the BA
 * surfaces each resolved the local, built a transform modifier and suppressed nested surfaces on
 * their own). [Inert] is the shared no-host answer, so an unstacked page allocates nothing.
 */
@Stable
class AppEdgeStackSlot internal constructor(
    internal val card: AppEdgeStackCard?,
    val modifier: Modifier,
) {
    val stacking: Boolean get() = card != null

    companion object {
        val Inert: AppEdgeStackSlot = AppEdgeStackSlot(card = null, modifier = Modifier)
    }
}

/**
 * Resolves this card's slot against the host page.
 *
 * [enabled] is the opt-out AppSurfaceCard uses while an expandable card is animating its height —
 * see `shouldApplyEdgeStackToExpandableCard`.
 */
@Composable
fun rememberAppEdgeStackSlot(enabled: Boolean = true): AppEdgeStackSlot {
    val state = LocalAppEdgeStackCards.current
    if (state == null || !enabled) return AppEdgeStackSlot.Inert
    val card = remember(state) { AppEdgeStackCard() }
    val stepRange = with(LocalDensity.current) {
        AppEdgeStackStepFloor.toPx() to AppEdgeStackStepCeiling.toPx()
    }
    val riseTotalPx = with(LocalDensity.current) { AppEdgeStackTuckRise.toPx() }
    return remember(state, card, stepRange, riseTotalPx) {
        AppEdgeStackSlot(
            card = card,
            modifier = Modifier.appEdgeStackProbe(
                state = state,
                card = card,
                stepFloorPx = stepRange.first,
                stepCeilingPx = stepRange.second,
                riseTotalPx = riseTotalPx,
            ),
        )
    }
}

/**
 * Measures the card against the stack line and publishes its depth.
 *
 * Reads its position from [androidx.compose.ui.layout.Placeable.PlacementScope.coordinates], which
 * Compose documents as re-running the placement block "when the parent layout changes a position" —
 * exactly the invalidation a scrolling list produces, and it arrives in the current frame. Placement
 * itself is plain: the card is never offset here, because the visual transform belongs inside the
 * glass layer (see [applyAppEdgeStackTransform]) where the backdrop can be inverse-corrected for it.
 */
private fun Modifier.appEdgeStackProbe(
    state: AppEdgeStackState,
    card: AppEdgeStackCard,
    stepFloorPx: Float,
    stepCeilingPx: Float,
    riseTotalPx: Float,
): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val container = state.containerCoordinates
            val self = coordinates
            val attached =
                container != null && self != null && container.isAttached && self.isAttached
            val itemTop =
                if (attached) {
                    container.localPositionOf(self).y
                } else {
                    Float.NaN
                }
            // How much of the card the reader can actually get to: the container, less the line the
            // pile pins at, less whatever floats over the bottom. Read from the same attached
            // coordinates as the position, and unbounded while there are none — which is exactly the
            // behaviour every host had before the bound existed.
            val containerHeight = if (attached) container.size.height.toFloat() else 0f
            val readableHeightPx =
                if (containerHeight > 0f) {
                    (containerHeight - state.stackLinePx - state.bottomInsetPx).coerceAtLeast(1f)
                } else {
                    Float.POSITIVE_INFINITY
                }
            val transform =
                if (itemTop.isNaN()) {
                    AppEdgeStackTransform.Identity
                } else {
                    computeAppEdgeStackTransform(
                        itemTopInContainer = itemTop,
                        itemHeightPx = placeable.height.toFloat(),
                        stackLinePx = state.stackLinePx,
                        riseTotalPx = riseTotalPx,
                        stepPx = placeable.height.toFloat().coerceIn(stepFloorPx, stepCeilingPx),
                        keepAliveHeadroomPx = state.keepAliveHeadroomPx,
                        readableHeightPx = readableHeightPx,
                    )
                }
            if (transform === AppEdgeStackTransform.Identity) {
                card.rest()
            } else {
                card.apply(transform)
            }
            placeable.placeRelative(0, 0)
        }
    }

/**
 * Folds a card's place in the pile into a glass surface's `layerBlock`.
 *
 * Being *inside* `drawBackdrop`'s layer rather than wrapped around it is what keeps the card real
 * glass while it recedes. `LayerBackdrop.drawBackdrop` inverse-transforms the sampled backdrop by
 * the layer block it is handed, so the refraction stays registered with the page behind; a transform
 * applied outside the modifier instead falls into the library's documented "outer transformations
 * lead to wrong position calculation" path, and the lens visibly slides as the card moves.
 *
 * The pivot is expressed as a translation deliberately. `InverseLayerScope.inverseTransformAtTopLeft`
 * reads only `rotationZ`, `scaleX` and `scaleY` and inverts about the element's top-left — it never
 * looks at `transformOrigin`. The old code scaled about `TransformOrigin(0.5f, 0f)`, which the
 * inverse cannot undo, so it would have left a horizontal residual that slid the refraction for the
 * length of the pile. Scaling about the top-left with a compensating `translationX` is the same
 * transform and one the inverse handles exactly.
 */
internal fun GraphicsLayerScope.applyAppEdgeStackTransform(card: AppEdgeStackCard) {
    if (!card.stacked) return
    val resolvedScale = card.scale
    scaleX = resolvedScale
    scaleY = resolvedScale
    translationX = size.width * APP_EDGE_STACK_PIVOT_X * (1f - resolvedScale)
    translationY = card.translationY
    transformOrigin = AppEdgeStackTopLeftOrigin
    // Only when it is actually retiring: a plain `alpha` promotes the layer to an offscreen buffer,
    // which on a surface that also draws a blurred backdrop is the expensive kind, and it bounds the
    // backdrop shadow's spread to the element rect.
    if (card.fade < 1f) presentationFade(card.fade)
}

/**
 * Applies the pile transform in a layer of its own.
 *
 * For surfaces with no glass layer to fold it into — a card rendering its opaque fallback because the
 * page has no usable backdrop. Prefer letting `LiquidSurface` carry it, so the sampled backdrop gets
 * inverse-corrected; this is the degraded path and it carries geometry only, no scrim.
 */
fun Modifier.appEdgeStackLayer(slot: AppEdgeStackSlot): Modifier {
    val card = slot.card ?: return this
    return graphicsLayer { applyAppEdgeStackTransform(card) }
}

/**
 * Alpha of the recession scrim a stacked card draws over its own surface, per theme.
 *
 * Light mode carries less than dark, and not for symmetry: a dark scrim on a pale card is far more
 * conspicuous per unit alpha than the same scrim on a dark one, so matching the numbers would make
 * the light pile look bruised while the dark pile looked flat.
 */
internal fun appEdgeStackDimAlpha(
    card: AppEdgeStackCard,
    isDark: Boolean,
): Float {
    if (!card.stacked) return 0f
    val ceiling = if (isDark) APP_EDGE_STACK_DIM_DARK else APP_EDGE_STACK_DIM_LIGHT
    return (card.dim * ceiling).coerceIn(0f, 1f)
}

/**
 * The scrim colour. A near-black with a blue cast rather than pure black, so a receding card reads as
 * falling into shade rather than as having a grey filter dropped on it.
 */
internal val AppEdgeStackDimColor = Color(0xFF0B0F18)

/**
 * Opacity for a stacked card's content, so a receding card becomes a blank plate.
 *
 * This is the channel that makes the pile legible on a large card. Apple's stacked artifacts all show
 * the surfaces behind the front one *empty* — the plates in the Pickers stack carry nothing, a grouped
 * notification behind the top one is a bare sliver, and a Wallet pass behind the front pass shows only
 * its edge. Keeping a card's text and artwork and merely darkening them produces a muddy photograph
 * instead of a receded surface, which is worse the more content the card has.
 */
internal fun appEdgeStackContentAlpha(card: AppEdgeStackCard): Float =
    if (card.stacked) card.contentAlpha.coerceIn(0f, 1f) else 1f

/**
 * Slides a receding card's content out of focus, in its own layer.
 *
 * Blur is the load-bearing channel and opacity is the trim. Both are layer properties, so the whole
 * recession is a render-thread update with no recomposition and no relayout — the same reason the
 * geometry rides in a layer block. A blur render effect does force the layer offscreen, but only for
 * the one or two cards actually in the pile.
 */
internal fun GraphicsLayerScope.applyAppEdgeStackContentRecession(card: AppEdgeStackCard) {
    if (!card.stacked) {
        renderEffect = null
        return
    }
    alpha = appEdgeStackContentAlpha(card)
    val blurPx = card.contentBlur.coerceIn(0f, 1f) * AppEdgeStackContentBlurMax.toPx()
    // Below about half a pixel a BlurEffect is invisible and not worth an offscreen pass, and a zero
    // radius is not a legal blur at all.
    renderEffect =
        if (blurPx >= 0.5f) {
            BlurEffect(radiusX = blurPx, radiusY = blurPx, edgeTreatment = TileMode.Clamp)
        } else {
            null
        }
}

data class AppEdgeStackTransform(
    /** Pins the card at the stack line, less the rise it has earned by sinking. */
    val translationY: Float,
    val scale: Float,
    /** 0 at the stack line, 1 fully receded. Scaled per theme by [appEdgeStackDimAlpha]. */
    val dim: Float,
    /**
     * Opacity of the card's *content* — not its surface. Bottoms out at
     * [APP_EDGE_STACK_CONTENT_ALPHA_FLOOR] rather than at zero.
     *
     * Deliberately never reaches zero while the card is a member of the pile. Emptying a receding card
     * makes the pile legible but throws its information away, which costs more screen than the pile
     * saves — the point of stacking is that *more* of the page stays useful, not less. So a receded
     * card stays vaguely readable and only leaves during retirement.
     */
    val contentAlpha: Float,
    /**
     * Blur applied to the card's content, 0..1 of [AppEdgeStackContentBlurMax], **linear in depth**.
     *
     * This is Apple's actual recession verb and it is the one the first attempt at this missed. The
     * watchOS sheet guidance is explicit that the system "applies a material to the background that
     * blurs and desaturates the covered content" — it blurs, progressively, rather than switching the
     * content off. Linear rather than eased on purpose: the ramp has to read as a continuous slide out
     * of focus, so a card never jumps from sharp to unrecognisable.
     */
    val contentBlur: Float,
    /** 1 until the retirement tail; reaches 0 before the lazy layout disposes the card. */
    val fade: Float,
) {
    companion object {
        val Identity =
            AppEdgeStackTransform(
                translationY = 0f,
                scale = 1f,
                dim = 0f,
                contentAlpha = 1f,
                contentBlur = 0f,
                fade = 1f,
            )
    }
}

/**
 * The pile geometry, kept pure so it can be unit-tested — it is the part that is easy to get subtly
 * wrong and impossible to see in a screenshot.
 *
 * [stepPx] is how much scroll travel one depth level costs. It tracks the card's own height, clamped
 * to [AppEdgeStackStepFloor]..[AppEdgeStackStepCeiling]. Unclamped height was the previous
 * behaviour's real bug: depth ran over `stackLine + height`, so a 600dp card needed 626dp of
 * overshoot to recede while a 60dp row needed 86dp — a sevenfold spread, which is why the pile
 * looked like a different effect on the OS page than on the history pages. Clamping caps that spread
 * at under 3x while still letting a tall card own more of the pile than a single row does.
 */
fun computeAppEdgeStackTransform(
    itemTopInContainer: Float,
    itemHeightPx: Float,
    stackLinePx: Float,
    riseTotalPx: Float,
    stepPx: Float,
    minScale: Float = APP_EDGE_STACK_MIN_SCALE,
    keepAliveHeadroomPx: Float = 0f,
    readableHeightPx: Float = Float.POSITIVE_INFINITY,
): AppEdgeStackTransform {
    val overshoot = stackLinePx - itemTopInContainer
    if (overshoot <= 0f || itemHeightPx <= 0f) return AppEdgeStackTransform.Identity
    // A card only joins the pile if the pile can show all of it. This is issues #19 and #29, and it is
    // arithmetic rather than taste.
    //
    // Pinning hands the card's travel straight back — `translationY = overshoot - rise * eased` — so a
    // pinned card's *drawn* top is `stackLine - rise * eased`, a function of depth alone and not of
    // scroll. Scrolling further therefore does not move the card down by one pixel; it only deepens it
    // and brings the next list item, which is still scrolling at 1:1 and draws (and hit-tests) above,
    // climbing over it from the bottom. So the deepest part of the card the reader can ever get to is
    // whatever fitted below the stack line at the moment it pinned — everything past that is out of
    // reach for the rest of the card's life, and no amount of scrolling recovers it.
    //
    // For a collapsed row that bound is far below the row's own height and nothing is lost, which is
    // why the pile is right on card-dense pages. For an open release card carrying a file list it fell
    // straight through the download and share buttons: reported as #19 against the tracked-app card,
    // fixed there by keeping expanded cards out of the pile, and reported again as #29 against the
    // release page, which had opted back in with `edgeStackWhileExpanded`.
    //
    // Bounding by the card's own height fixes the class rather than the two call sites, and it can only
    // ever withhold the effect in precisely the case where the effect hides content: at
    // `itemHeightPx <= readableHeightPx` every page behaves exactly as it did.
    if (itemHeightPx > readableHeightPx) return AppEdgeStackTransform.Identity
    // A pinned card is still disposed on its LAYOUT position, not the position it is drawn at, so the
    // pile cannot outlast however far above the viewport the container keeps items alive. Clamping the
    // extent to a margin inside that is what makes `fade == 0` before disposal provable rather than
    // hopeful.
    //
    // [keepAliveHeadroomPx] is what moves that bound. At zero the reachable depth is the card's own
    // height — which is why the pile was about one card deep whatever [APP_EDGE_STACK_LEVELS] claimed,
    // and why raising the level count alone did nothing. Every host now publishes headroom through
    // `AppEdgeStackKeepAlive`, so zero is reached only before the wrapper's first composition and in
    // the tests, where it is the control case the depth claim is measured against.
    val disposalOvershoot =
        (stackLinePx + itemHeightPx + keepAliveHeadroomPx.coerceAtLeast(0f)) *
            APP_EDGE_STACK_RETIRE_MARGIN
    val extent =
        minOf(APP_EDGE_STACK_LEVELS * stepPx.coerceAtLeast(1f), disposalOvershoot)
            .coerceAtLeast(1f)
    val progress = (overshoot / extent).coerceIn(0f, 1f)
    // Decelerate, so levels tighten as they deepen and the pile reads as a stack rather than a ramp.
    val eased = progress * (2f - progress)
    val fadeSpan = 1f - APP_EDGE_STACK_FADE_START
    return AppEdgeStackTransform(
        translationY = overshoot - riseTotalPx * eased,
        scale = 1f - (1f - minScale) * eased,
        dim = eased,
        // Softens toward the floor rather than to nothing, so a receded card is still worth having on
        // screen. Eased alongside the geometry so opacity and inset move together.
        contentAlpha = 1f - (1f - APP_EDGE_STACK_CONTENT_ALPHA_FLOOR) * eased,
        // Linear, unlike everything else here: the blur is the channel the eye reads as "sliding out of
        // focus", and easing it makes a card lose legibility in a rush near the line.
        contentBlur = progress,
        fade =
            if (progress <= APP_EDGE_STACK_FADE_START || fadeSpan <= 0f) {
                1f
            } else {
                (1f - (progress - APP_EDGE_STACK_FADE_START) / fadeSpan).coerceIn(0f, 1f)
            },
    )
}

private val AppEdgeStackTopLeftOrigin = TransformOrigin(0f, 0f)

/** Cards narrow toward their horizontal centre as they recede, so the pivot is centre-x, top. */
private const val APP_EDGE_STACK_PIVOT_X = 0.5f

/**
 * Total rise across the pile.
 *
 * Generous on purpose. The point of the inset and the rise together is that a reader can *count* the
 * plates behind the front card — with a small offset a large card recedes into something that reads as
 * one dimmed card rather than as a stack, which is the whole legibility problem.
 */
private val AppEdgeStackTuckRise = 26.dp

/** Scroll travel per depth level is the card's height, held inside this band. */
private val AppEdgeStackStepFloor = 64.dp
private val AppEdgeStackStepCeiling = 168.dp

/**
 * Top content inset for lists whose page pins an overview block above them: leaves the tuck rise
 * fully visible inside the lazy viewport plus a breathing gap, and doubles as the stack line those
 * lists pass to [rememberAppEdgeStackState].
 */
val AppEdgeStackListTopInset = 26.dp

/**
 * Invisible viewport the lazy list gets *above* its visible top, so a pinned card is not disposed.
 *
 * This is what capped the pile at roughly one card. A card in the pile is held near the top edge by a
 * transform inside its glass layer, but its *layout* position keeps travelling with the list — so once
 * that position clears the lazy viewport by the container's own retention margin, the item is disposed
 * and the pinned plate simply vanishes, however many levels [APP_EDGE_STACK_LEVELS] claims.
 *
 * Sized as the pile's depth in card-heights plus the rise: three levels of [AppEdgeStackStepCeiling]
 * covers the tallest card a page uses, and the extra rise keeps the deepest plate's own offset inside
 * the kept region rather than exactly on its boundary. Bigger would keep more items composed for no
 * visible gain, which is the cost this trades against — every card in the headroom is a real composed,
 * measured card.
 */
val AppEdgeStackKeepAliveHeadroom: Dp =
    AppEdgeStackStepCeiling * APP_EDGE_STACK_LEVELS.toInt() + AppEdgeStackTuckRise

const val APP_EDGE_STACK_MIN_SCALE = 0.86f

/** Visible pile depth. Ours, not Apple's — see the class KDoc. */
internal const val APP_EDGE_STACK_LEVELS = 3f

/** Retire this far inside the disposal point, so removal never pops. */
internal const val APP_EDGE_STACK_RETIRE_MARGIN = 0.9f

/** Where the retirement tail starts, as a fraction of the pile. */
internal const val APP_EDGE_STACK_FADE_START = 0.82f

/**
 * How faint a fully receded card's content is allowed to get while it is still in the pile.
 *
 * Well above zero on purpose. A blank plate reads beautifully and is the wrong trade: it converts
 * screen the reader could still be using into decoration, so the pile ends up showing *less* than a
 * plain list. Blur carries the recession; opacity only takes the edge off.
 */
internal const val APP_EDGE_STACK_CONTENT_ALPHA_FLOOR = 0.55f

/**
 * Blur at full depth. Enough that a receded card is plainly out of focus and cannot compete with the
 * card in front, but not so much that its shapes stop being identifiable.
 */
internal val AppEdgeStackContentBlurMax = 9.dp

/**
 * Scrim ceilings, much lighter than they would need to be if the plate still carried its content.
 * With the card emptied, the scrim only has to seat the plate behind the one in front — it is not
 * fighting a photograph for attention.
 */
internal const val APP_EDGE_STACK_DIM_DARK = 0.20f
internal const val APP_EDGE_STACK_DIM_LIGHT = 0.12f
