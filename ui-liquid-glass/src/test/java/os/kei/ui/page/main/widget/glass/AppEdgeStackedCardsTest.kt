package os.kei.ui.page.main.widget.glass

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The pile geometry.
 *
 * Two properties here are the point of the rewrite rather than tuning, and both are stated as
 * assertions rather than left to a screenshot.
 *
 * **Depth darkens before anything turns transparent.** `dim` is the depth channel and `fade` is only
 * a retirement tail. The previous implementation had no `dim` at all and drove `alpha` from 1 to 0
 * across the whole pile, which is why receding cards read as merely translucent — and over a light
 * page they got *lighter* as they receded, the opposite of going behind something.
 *
 * **A tall card and a short row pile at comparable rates.** Depth used to run over
 * `stackLine + height` with no bound, so a 600px card needed 626px of overshoot to recede while a
 * 60px row needed 86px — a sevenfold spread, and the reason the effect looked like a different
 * feature on the OS page than on the history pages.
 */
class AppEdgeStackedCardsTest {
    private val stackLine = 300f
    private val height = 400f
    private val riseTotal = 54f
    private val step = 150f

    /** Whichever bound the pile actually runs to — the level budget or the disposal margin. */
    private val extent =
        minOf(
            APP_EDGE_STACK_LEVELS * step,
            (stackLine + height) * APP_EDGE_STACK_RETIRE_MARGIN,
        )

    private fun transformAtDepth(fraction: Float): AppEdgeStackTransform =
        transformAt(stackLine - fraction * extent)

    private fun transformAt(
        itemTop: Float,
        itemHeightPx: Float = height,
        stepPx: Float = step,
        readableHeightPx: Float = Float.POSITIVE_INFINITY,
    ): AppEdgeStackTransform =
        computeAppEdgeStackTransform(
            itemTopInContainer = itemTop,
            itemHeightPx = itemHeightPx,
            stackLinePx = stackLine,
            riseTotalPx = riseTotal,
            stepPx = stepPx,
            readableHeightPx = readableHeightPx,
        )

    @Test
    fun `cards resting at or below the stack line render untouched`() {
        assertEquals(AppEdgeStackTransform.Identity, transformAt(stackLine))
        assertEquals(AppEdgeStackTransform.Identity, transformAt(stackLine + 250f))
    }

    @Test
    fun `zero height items never transform`() {
        assertEquals(AppEdgeStackTransform.Identity, transformAt(0f, itemHeightPx = 0f))
    }

    @Test
    fun `a card crossing the line pins near it instead of scrolling away`() {
        val overshoot = 80f
        val transform = transformAt(stackLine - overshoot)

        // Pinned: the travel is handed back, less the rise the card has earned by sinking.
        assertTrue(transform.translationY > overshoot - riseTotal)
        assertTrue(transform.translationY <= overshoot)
        assertTrue(transform.scale < 1f)
    }

    @Test
    fun `a receding card slides out of focus and stays readable`() {
        // Recession is carried by blur, per Apple's own verb for it - watchOS "applies a material to the
        // background that blurs and desaturates the covered content". An earlier attempt emptied the
        // card instead, which reads beautifully and is the wrong trade: it converts screen the reader
        // could still be using into decoration, so the pile shows LESS than a plain list would.
        val shallow = transformAt(stackLine - 40f)
        val deep = transformAt(stackLine - 4000f)

        assertTrue(shallow.contentBlur > 0f)
        assertTrue(deep.contentBlur > shallow.contentBlur)
        assertEquals(1f, deep.contentBlur)
        assertTrue(
            deep.contentAlpha >= APP_EDGE_STACK_CONTENT_ALPHA_FLOOR,
            "a card in the pile is never emptied: ${deep.contentAlpha}",
        )
        assertTrue(APP_EDGE_STACK_CONTENT_ALPHA_FLOOR >= 0.5f, "still worth screen space")
    }

    @Test
    fun `the blur ramp is linear so nothing jumps out of focus`() {
        // Everything else here is eased; the blur is not. Easing it makes a card lose legibility in a
        // rush right after the line, which is the "can't tell what it is any more" failure.
        val quarter = transformAtDepth(0.25f)
        val half = transformAtDepth(0.5f)
        val threeQuarters = transformAtDepth(0.75f)

        val firstStep = half.contentBlur - quarter.contentBlur
        val secondStep = threeQuarters.contentBlur - half.contentBlur
        assertTrue(
            kotlin.math.abs(firstStep - secondStep) < 0.01f,
            "equal depth steps must cost equal blur: $firstStep vs $secondStep",
        )
    }

    @Test
    fun `the surface stays crisp while its content softens`() {
        val receded = transformAtDepth(0.5f)

        assertTrue(receded.contentBlur > 0f)
        assertTrue(receded.contentAlpha < 1f)
        assertEquals(1f, receded.fade, "the plate itself is fully present")
    }

    @Test
    fun `the scrim is light enough not to read as a filter over the card`() {
        // It only has to seat the card behind the one in front. Left to carry the whole recession on its
        // own these ceilings were nearly twice as heavy, and over artwork that looked like colour
        // grading rather than depth. Blur does the work now.
        assertTrue(APP_EDGE_STACK_DIM_DARK <= 0.24f)
        assertTrue(APP_EDGE_STACK_DIM_LIGHT <= 0.16f)
    }

    @Test
    fun `depth is expressed as dimming long before anything fades`() {
        // The regression this rewrite exists to prevent. At the depth where a card is already visibly
        // receded, it must still be fully opaque — otherwise the pile is just transparency again.
        val shallow = transformAt(stackLine - 60f)
        val middle = transformAt(stackLine - 180f)

        assertTrue(shallow.dim > 0f, "a card past the line is already dimmed")
        assertTrue(middle.dim > shallow.dim, "dimming deepens with depth")
        assertEquals(1f, shallow.fade)
        assertEquals(1f, middle.fade)
    }

    @Test
    fun `depth grows monotonically and bottoms out at the scale floor`() {
        val shallow = transformAt(stackLine - 50f)
        val deep = transformAt(stackLine - 400f)

        assertTrue(deep.scale < shallow.scale)
        assertTrue(deep.scale >= APP_EDGE_STACK_MIN_SCALE)
        assertTrue(deep.dim > shallow.dim)
        // Deeper cards sit visually higher: more of the rise has been applied.
        val shallowVisualTop = stackLine - 50f + shallow.translationY
        val deepVisualTop = stackLine - 400f + deep.translationY
        assertTrue(deepVisualTop < shallowVisualTop)
    }

    @Test
    fun `dim and scale saturate rather than running away`() {
        val atFullDepth = transformAt(stackLine - 4000f)

        assertEquals(1f, atFullDepth.dim)
        assertEquals(APP_EDGE_STACK_MIN_SCALE, atFullDepth.scale)
        assertEquals(0f, atFullDepth.fade)
    }

    @Test
    fun `retirement completes before the lazy layout disposes the card`() {
        // Disposal happens when the item's layout bottom crosses the viewport top: itemTop == -height.
        val disposalOvershoot = stackLine + height
        assertEquals(0f, transformAt(-height).fade)
        // And strictly before it, so removal never pops.
        val retireOvershoot = disposalOvershoot * APP_EDGE_STACK_RETIRE_MARGIN
        assertTrue(retireOvershoot < disposalOvershoot)
        assertEquals(0f, transformAt(stackLine - retireOvershoot).fade)
    }

    @Test
    fun `the pile is bounded to a few levels rather than the whole card`() {
        // A short row must not have to travel its own height plus the stack line to recede: with the
        // step bounded, three levels is the whole pile.
        val shortStep = 64f
        val pile = APP_EDGE_STACK_LEVELS * shortStep
        val atPileEnd = transformAt(stackLine - pile, itemHeightPx = 900f, stepPx = shortStep)

        assertEquals(1f, atPileEnd.dim)
        assertEquals(APP_EDGE_STACK_MIN_SCALE, atPileEnd.scale)
    }

    @Test
    fun `without keep-alive headroom a short row cannot reach its levels`() {
        // The pile's arithmetic bug, stated. The bound is `(stackLine + height) * margin`, so it bites
        // whenever a card's level budget outruns its own height plus the stack line — which is every
        // short row on a page whose stack line is tight, and the reason the pile was about one card deep
        // no matter what APP_EDGE_STACK_LEVELS said.
        val shortStep = 64f
        val levelBudget = APP_EDGE_STACK_LEVELS * shortStep
        val tinyStackLine = 8f
        val rowHeight = 48f
        val disposalBound = (tinyStackLine + rowHeight) * APP_EDGE_STACK_RETIRE_MARGIN

        assertTrue(
            disposalBound < levelBudget,
            "fixture must exercise the bound: $disposalBound vs $levelBudget",
        )

        // Saturated at the bound rather than at the level budget: the row has already fully receded and
        // retired by the time it has travelled half of the pile it was supposed to have.
        val atBound =
            computeAppEdgeStackTransform(
                itemTopInContainer = tinyStackLine - disposalBound,
                itemHeightPx = rowHeight,
                stackLinePx = tinyStackLine,
                riseTotalPx = riseTotal,
                stepPx = shortStep,
            )

        assertEquals(1f, atBound.dim)
        assertEquals(0f, atBound.fade)
    }

    @Test
    fun `keep-alive headroom lets the pile reach its full level budget`() {
        val shortStep = 64f
        val levelBudget = APP_EDGE_STACK_LEVELS * shortStep
        val tinyStackLine = 8f
        val rowHeight = 48f
        // What AppEdgeStackKeepAlive publishes: comfortably more than the level budget.
        val headroom = 530f

        fun at(overshoot: Float) =
            computeAppEdgeStackTransform(
                itemTopInContainer = tinyStackLine - overshoot,
                itemHeightPx = rowHeight,
                stackLinePx = tinyStackLine,
                riseTotalPx = riseTotal,
                stepPx = shortStep,
                keepAliveHeadroomPx = headroom,
            )

        // Now the level budget is the binding constraint, so depth saturates exactly there.
        assertEquals(1f, at(levelBudget).dim)
        // And a row only part way in is genuinely part way, rather than already retired.
        val half = at(levelBudget / 2f)
        assertTrue(half.dim > 0f && half.dim < 1f, "half depth should be mid-pile, was ${half.dim}")
        assertEquals(1f, half.fade, "a mid-pile plate must still be fully present")
    }

    @Test
    fun `retirement still completes before disposal once headroom is granted`() {
        // The whole reason the bound exists. Moving it must not let a card outlive the kept region, or a
        // plate pops out of existence when the lazy layout finally drops it.
        val headroom = 530f
        val disposal = stackLine + height + headroom

        assertEquals(0f, transformAtHeadroom(disposal, headroom).fade)
        assertEquals(
            0f,
            transformAtHeadroom(disposal * APP_EDGE_STACK_RETIRE_MARGIN, headroom).fade,
        )
    }

    @Test
    fun `a negative headroom cannot shrink the bound`() {
        val withNothing = transformAtHeadroom(overshoot = 120f, headroom = 0f)
        val withNonsense = transformAtHeadroom(overshoot = 120f, headroom = -5_000f)

        assertEquals(withNothing.dim, withNonsense.dim)
        assertEquals(withNothing.fade, withNonsense.fade)
    }

    private fun transformAtHeadroom(
        overshoot: Float,
        headroom: Float,
    ): AppEdgeStackTransform =
        computeAppEdgeStackTransform(
            itemTopInContainer = stackLine - overshoot,
            itemHeightPx = height,
            stackLinePx = stackLine,
            riseTotalPx = riseTotal,
            stepPx = step,
            keepAliveHeadroomPx = headroom,
        )

    @Test
    fun `tall and short cards recede at comparable rates`() {
        // The clamped step is what bounds this. Unclamped height gave a sevenfold spread; measured at
        // the same overshoot, the two extremes of the step band must stay within a small factor.
        val overshoot = 120f
        val shortRow = transformAt(stackLine - overshoot, itemHeightPx = 64f, stepPx = 64f)
        val tallCard = transformAt(stackLine - overshoot, itemHeightPx = 900f, stepPx = 168f)

        assertTrue(shortRow.dim > tallCard.dim, "a short row is deeper into the pile at equal travel")
        assertTrue(
            shortRow.dim < tallCard.dim * 3f,
            "but not by the runaway factor the unbounded rate produced: " +
                "${shortRow.dim} vs ${tallCard.dim}",
        )
    }

    @Test
    fun `a card taller than the window below the stack line never joins the pile`() {
        // Issues #19 and #29. Pinning hands the card's travel back, so a pinned card's drawn top is a
        // function of depth alone -- scrolling further does not move it down by a pixel. Whatever did not
        // fit below the stack line when it pinned is therefore unreachable for the rest of the card's
        // life, which on the release page was the file rows carrying the download and share buttons.
        val window = 600f

        assertEquals(
            AppEdgeStackTransform.Identity,
            transformAt(stackLine - 120f, itemHeightPx = window + 1f, readableHeightPx = window),
        )
        // And not just at the line: no scroll position rescues it.
        assertEquals(
            AppEdgeStackTransform.Identity,
            transformAt(stackLine - 4_000f, itemHeightPx = window + 1f, readableHeightPx = window),
        )
    }

    @Test
    fun `a card that fits the window still piles, so the effect is withheld only where it would hide something`() {
        val window = 600f
        val transform = transformAt(stackLine - 120f, itemHeightPx = window, readableHeightPx = window)

        assertTrue(transform.dim > 0f, "a card that fits is still a pile member")
        assertTrue(transform.scale < 1f)
        assertEquals(
            transformAt(stackLine - 120f, itemHeightPx = window),
            transform,
            "and it is transformed identically to how it was before the bound existed",
        )
    }

    @Test
    fun `every pixel of a pinnable card is reachable, which is the property the bound buys`() {
        // The bound restated as the thing a reader cares about, rather than as an inequality. A card is
        // allowed into the pile only if its last pixel is inside the container at the moment it pins --
        // drawn y of card-local offset d is (stackLine - rise * eased) + d * scale, and at the pin moment
        // eased is 0 and scale is 1.
        val container = 1_000f
        val window = container - stackLine
        val itemTop = stackLine - 1f

        fun deepestDrawnY(transform: AppEdgeStackTransform, cardHeight: Float): Float =
            itemTop + transform.translationY + cardHeight * transform.scale

        for (cardHeight in listOf(64f, 200f, window - 1f, window)) {
            val atPin = transformAt(itemTop, itemHeightPx = cardHeight, readableHeightPx = window)
            assertTrue(
                deepestDrawnY(atPin, cardHeight) <= container,
                "a ${cardHeight}px card pinned at the line draws down to " +
                    "${deepestDrawnY(atPin, cardHeight)}, past the container at $container",
            )
        }

        // And the bound is load-bearing rather than decorative: the first card over the window would
        // have drawn past the container's bottom edge had it been allowed to pin, and that overhang is
        // what no amount of scrolling brings back.
        val overTall = window + 200f
        val unbounded = transformAt(itemTop, itemHeightPx = overTall)
        assertTrue(
            deepestDrawnY(unbounded, overTall) > container,
            "fixture must exercise the bound: ${deepestDrawnY(unbounded, overTall)} vs $container",
        )
        assertEquals(
            AppEdgeStackTransform.Identity,
            transformAt(itemTop, itemHeightPx = overTall, readableHeightPx = window),
        )
    }

    @Test
    fun `the bound is unset by default, so a host that declares no window behaves exactly as before`() {
        // Every host predates the bound and most never need it. The probe also passes infinity while the
        // container is unattached, which is the first frame of every page.
        val tall = transformAt(stackLine - 120f, itemHeightPx = 4_000f)

        assertTrue(tall.dim > 0f)
        assertEquals(tall, transformAt(stackLine - 120f, itemHeightPx = 4_000f, readableHeightPx = Float.POSITIVE_INFINITY))
    }

    @Test
    fun `a pinned card is still opaque when its layout rect leaves the clip`() {
        // Why `cullWhenFullyClipped` has to ask the pile instead of reading `boundsInWindow`.
        //
        // That modifier skips the draw of an element whose *clipped layout rect* is empty, and it sits
        // above the layer the pile's transform rides in — so for a pinned card it reads a rect that has
        // scrolled away while the plate is held at the stack line. The rect clears at
        // `stackLine + height`; the pile only retires at `extent`. This pins the disagreement: at the
        // moment the rect clears, the plate is at FULL opacity, so culling on it cut visible glass.
        //
        // Density-3 pixels, so the numbers line up with the real constants — the step band is
        // 192..504px, the rise 78px, and the keep-alive headroom 1590px.
        val line = 300f
        val headroom = 1_590f

        fun atClipExit(cardHeight: Float): AppEdgeStackTransform =
            computeAppEdgeStackTransform(
                // Overshoot == stackLine + height is exactly where the layout rect clears the top edge.
                itemTopInContainer = line - (line + cardHeight),
                itemHeightPx = cardHeight,
                stackLinePx = line,
                riseTotalPx = 78f,
                stepPx = cardHeight.coerceIn(192f, 504f),
                keepAliveHeadroomPx = headroom,
            )

        // 48dp, 70dp, 110dp, 200dp — the whole range of rows and collapsed cards the pile is used on.
        listOf(144f, 210f, 330f, 600f).forEach { cardHeight ->
            assertEquals(
                1f,
                atClipExit(cardHeight).fade,
                "a ${cardHeight}px card is fully opaque when its layout rect clears, so culling on " +
                    "that rect removes a plate the reader is looking at",
            )
        }

        // Not universal, and the contrast is the point: a card tall enough that `stackLine + height`
        // outruns the level budget retires before its rect clears, and for those two the old signal
        // agreed with the pile. That band is narrow and it is not where the pile is used.
        assertEquals(0f, atClipExit(1_300f).fade)
    }

    @Test
    fun `the scrim is heavier in dark mode than in light`() {
        val card = AppEdgeStackCard().apply { apply(transformAt(stackLine - 4000f)) }

        assertEquals(APP_EDGE_STACK_DIM_DARK, appEdgeStackDimAlpha(card, isDark = true))
        assertEquals(APP_EDGE_STACK_DIM_LIGHT, appEdgeStackDimAlpha(card, isDark = false))
        assertTrue(APP_EDGE_STACK_DIM_LIGHT < APP_EDGE_STACK_DIM_DARK)
    }

    @Test
    fun `a resting card draws no scrim at all`() {
        val card = AppEdgeStackCard()

        assertEquals(0f, appEdgeStackDimAlpha(card, isDark = true))
        assertEquals(0f, appEdgeStackDimAlpha(card, isDark = false))
    }
}
