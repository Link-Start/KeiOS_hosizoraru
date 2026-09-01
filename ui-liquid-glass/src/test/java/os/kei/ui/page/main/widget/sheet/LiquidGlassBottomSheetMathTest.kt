@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassBottomSheetMathTest {
    @Test
    fun blockedDismissGateCoalescesCallbacksFromOneUserAction() {
        val gate = BlockedDismissRequestGate(deduplicationWindowNanos = 200L)
        var dispatchCount = 0

        assertTrue(gate.dispatch(nowNanos = 1_000L) { dispatchCount++ })
        assertFalse(gate.dispatch(nowNanos = 1_050L) { dispatchCount++ })
        assertFalse(gate.dispatch(nowNanos = 1_199L) { dispatchCount++ })
        assertTrue(gate.dispatch(nowNanos = 1_200L) { dispatchCount++ })
        assertEquals(2, dispatchCount)
    }

    @Test
    fun managedContentUsesOpeningHeightUntilUserResizes() {
        assertEquals(
            720,
            liquidSheetManagedContentMaxHeightPx(
                openingContentHeightPx = 720,
                resizedContentHeightPx = 0,
            ),
        )
        assertEquals(
            420,
            liquidSheetManagedContentMaxHeightPx(
                openingContentHeightPx = 720,
                resizedContentHeightPx = 420,
            ),
        )
        assertEquals(
            960,
            liquidSheetManagedContentMaxHeightPx(
                openingContentHeightPx = 720,
                resizedContentHeightPx = 960,
            ),
        )
    }

    @Test
    fun maxVisibleHeightLeavesSafeTopInset() {
        assertEquals(
            1_040f,
            liquidSheetMaxVisibleHeightPx(windowHeightPx = 1_120f, topInsetPx = 80f),
        )
        assertEquals(
            1_024f,
            liquidSheetMaxVisibleHeightPx(windowHeightPx = 1_120f, topInsetPx = 96f),
        )
    }

    /**
     * The regression guard for the dismiss flinch.
     *
     * Everything that fades with the sheet is scaled by the presentation value, so it has to bottom
     * out at exactly zero. The sheet this replaced floored its background dim at 0.28, which meant a
     * dismissal ended with a 28%-dimmed screen being cut to nothing the instant the window went away.
     */
    @Test
    fun presentationReachesZeroWhenTheSheetIsFullyHidden() {
        assertEquals(0f, liquidSheetPresentation(1f), 0f)
        assertEquals(1f, liquidSheetPresentation(0f), 0f)
        assertEquals(0.5f, liquidSheetPresentation(0.5f), 0.0001f)
        // An exit spring may pass the target slightly; that must stay clamped, not go negative.
        assertEquals(0f, liquidSheetPresentation(1.08f), 0f)
        assertEquals(1f, liquidSheetPresentation(-0.05f), 0f)
    }

    @Test
    fun presentationFallsMonotonicallyAsTheSheetLeaves() {
        val samples = (0..10).map { step -> liquidSheetPresentation(step / 10f) }
        samples.zipWithNext().forEach { (earlier, later) ->
            assertTrue(later <= earlier, "Presentation must never rise while the sheet leaves")
        }
    }

    @Test
    fun offsetClampsAnEnterOvershootSoNoGapOpensBelowTheSheet() {
        assertEquals(0f, liquidSheetOffsetPx(hidden = -0.06f, heightPx = 1_000f), 0f)
        assertEquals(0f, liquidSheetOffsetPx(hidden = 0f, heightPx = 1_000f), 0f)
        assertEquals(400f, liquidSheetOffsetPx(hidden = 0.4f, heightPx = 1_000f), 0.0001f)
        assertEquals(1_000f, liquidSheetOffsetPx(hidden = 1f, heightPx = 1_000f), 0.0001f)
    }

    @Test
    fun scrimBlurPlateStopsAtTheSheetsTopEdgePlusItsCorners() {
        assertEquals(
            504,
            liquidSheetScrimBlurHeightPx(
                windowHeightPx = 1_120f,
                sheetHeightPx = 700f,
                offsetPx = 0f,
                cornerRadiusPx = 84f,
            ),
        )
        // As the sheet slides away the plate follows it down and is capped by the window.
        assertEquals(
            1_120,
            liquidSheetScrimBlurHeightPx(
                windowHeightPx = 1_120f,
                sheetHeightPx = 700f,
                offsetPx = 700f,
                cornerRadiusPx = 84f,
            ),
        )
    }

    /**
     * A phone's sheet spans the window; a tablet's does not, and that is what the plate has to follow.
     *
     * The widths are the real ones: 426dp is the phone AVD against a 480dp compact cap, 1280dp is the Pad
     * in landscape against the 592dp panel measured there.
     */
    @Test
    fun onlyASheetThatReachesBothEdgesSpansTheWindow() {
        assertTrue(
            liquidSheetSpansWindowWidth(
                windowWidth = 426.dp,
                sheetMaxWidth = 480.dp,
                outsideMarginWidth = 0.dp,
            ),
            "A phone's cap is wider than its window, so the sheet fills it",
        )
        assertFalse(
            liquidSheetSpansWindowWidth(
                windowWidth = 1_280.dp,
                sheetMaxWidth = 592.dp,
                outsideMarginWidth = 0.dp,
            ),
            "The Pad's cap wins and leaves bare window down both sides",
        )
        // A margin leaves a strip however wide the sheet is allowed to be. A seam is a seam.
        assertFalse(
            liquidSheetSpansWindowWidth(
                windowWidth = 426.dp,
                sheetMaxWidth = 480.dp,
                outsideMarginWidth = 8.dp,
            ),
        )
    }

    /**
     * A sheet that leaves window bare beside it takes the whole height instead of stopping at its own top.
     *
     * Stopping there is only correct while the sheet covers everything below the plate; on the Pad it drew
     * a hard horizontal line with dimmed-but-unblurred page either side of the panel.
     */
    @Test
    fun aSheetThatDoesNotSpanTheWindowBlursAllOfIt() {
        assertEquals(
            Int.MAX_VALUE,
            liquidSheetScrimBlurHeightPx(
                windowHeightPx = 1_600f,
                sheetHeightPx = 1_200f,
                offsetPx = 0f,
                cornerRadiusPx = 84f,
                sheetSpansWindowWidth = false,
            ),
        )
        // And the sheet that does span it is unchanged, which is every phone.
        assertEquals(
            504,
            liquidSheetScrimBlurHeightPx(
                windowHeightPx = 1_120f,
                sheetHeightPx = 700f,
                offsetPx = 0f,
                cornerRadiusPx = 84f,
                sheetSpansWindowWidth = true,
            ),
        )
    }

    @Test
    fun dismissTriggersOnDistanceOrVelocity() {
        assertFalse(
            liquidSheetDismissDragExceeded(
                draggedPx = 0f,
                thresholdPx = 200f,
                velocity = 4_000f,
                velocityThresholdPx = 2_400f,
            ),
            "A fling with no drag at all must not dismiss",
        )
        assertFalse(
            liquidSheetDismissDragExceeded(
                draggedPx = 40f,
                thresholdPx = 200f,
                velocity = 100f,
                velocityThresholdPx = 2_400f,
            ),
        )
        assertTrue(
            liquidSheetDismissDragExceeded(
                draggedPx = 260f,
                thresholdPx = 200f,
                velocity = 0f,
                velocityThresholdPx = 2_400f,
            ),
            "Past the distance threshold, release dismisses",
        )
        assertTrue(
            liquidSheetDismissDragExceeded(
                draggedPx = 40f,
                thresholdPx = 200f,
                velocity = 3_000f,
                velocityThresholdPx = 2_400f,
            ),
            "A short but fast flick dismisses",
        )
    }

    /**
     * A drag arrives as many small deltas. Dismiss progress has to add up across them — the first
     * version of this assigned from a single event, so a long drag past the floor held only the last
     * delta's worth and the sheet sprang back however far it was pulled.
     */
    @Test
    fun dismissProgressAccumulatesAcrossDragEvents() {
        var height = 900f
        var hidden = 0f
        repeat(10) {
            val result = liquidSheetResolveDrag(
                deltaPx = 30f,
                heightPx = height,
                hidden = hidden,
                minVisibleHeightPx = 900f,
                maxVisibleHeightPx = 2_400f,
                resistance = 1f,
            )
            height = result.heightPx
            hidden = result.hidden
        }
        assertEquals(900f, height, 0.001f)
        assertEquals(300f, liquidSheetOffsetPx(hidden, height), 0.5f)
        assertTrue(
            liquidSheetDismissDragExceeded(
                draggedPx = liquidSheetOffsetPx(hidden, height),
                thresholdPx = 216f,
                velocity = 0f,
                velocityThresholdPx = 2_400f,
            ),
            "Ten 30px pulls past the floor must add up past the dismiss threshold",
        )
    }

    @Test
    fun dragShrinksTheSheetBeforeItStartsDismissing() {
        val result = liquidSheetResolveDrag(
            deltaPx = 400f,
            heightPx = 2_000f,
            hidden = 0f,
            minVisibleHeightPx = 900f,
            maxVisibleHeightPx = 2_400f,
            resistance = 1f,
        )
        assertEquals(1_600f, result.heightPx, 0.001f)
        assertEquals(0f, result.hidden, 0.001f)
        assertEquals(400f, result.consumedPx, 0.001f)
    }

    @Test
    fun dragUpUndoesDismissProgressBeforeGrowingTheSheet() {
        val pulled = liquidSheetResolveDrag(
            deltaPx = 200f,
            heightPx = 900f,
            hidden = 0f,
            minVisibleHeightPx = 900f,
            maxVisibleHeightPx = 2_400f,
            resistance = 1f,
        )
        assertEquals(200f, liquidSheetOffsetPx(pulled.hidden, pulled.heightPx), 0.5f)

        val pushedBack = liquidSheetResolveDrag(
            deltaPx = -200f,
            heightPx = pulled.heightPx,
            hidden = pulled.hidden,
            minVisibleHeightPx = 900f,
            maxVisibleHeightPx = 2_400f,
            resistance = 1f,
        )
        assertEquals(0f, pushedBack.hidden, 0.001f)
        assertEquals(900f, pushedBack.heightPx, 0.001f)
    }

    @Test
    fun blockedSheetsResistTheDismissDrag() {
        val allowed = liquidSheetResolveDrag(
            deltaPx = 300f,
            heightPx = 900f,
            hidden = 0f,
            minVisibleHeightPx = 900f,
            maxVisibleHeightPx = 2_400f,
            resistance = 1f,
        )
        val blocked = liquidSheetResolveDrag(
            deltaPx = 300f,
            heightPx = 900f,
            hidden = 0f,
            minVisibleHeightPx = 900f,
            maxVisibleHeightPx = 2_400f,
            resistance = 0.35f,
        )
        assertTrue(
            blocked.hidden < allowed.hidden,
            "A sheet that refuses dismissal must follow the finger less far",
        )
    }

    @Test
    fun visibleHeightFractionTracksResizableSheetHeight() {
        assertEquals(
            0f,
            liquidSheetVisibleHeightFraction(visibleHeightPx = 0f, maxVisibleHeightPx = 1_000f),
            0.0001f,
        )
        assertEquals(
            0.5f,
            liquidSheetVisibleHeightFraction(visibleHeightPx = 500f, maxVisibleHeightPx = 1_000f),
            0.0001f,
        )
        assertEquals(
            1f,
            liquidSheetVisibleHeightFraction(visibleHeightPx = 1_200f, maxVisibleHeightPx = 1_000f),
            0.0001f,
        )
    }

    @Test
    fun visualDetentFractionUsesSmallStableSteps() {
        assertEquals(0f, liquidSheetQuantizedVisualDetentFraction(-0.5f), 0.0001f)
        assertEquals(0.5f, liquidSheetQuantizedVisualDetentFraction(0.5f), 0.0001f)
        assertTrue(
            liquidSheetQuantizedVisualDetentFraction(0.751f) in 0.74f..0.77f,
            "Expected visual detent quantization to preserve smooth height readability",
        )
        assertEquals(1f, liquidSheetQuantizedVisualDetentFraction(1.2f), 0.0001f)
    }

    @Test
    fun glassSurfaceTintGainsReadabilityBeforeFullHeight() {
        val shortLightAlpha =
            liquidSheetGlassSurfaceColor(
                isDark = false,
                solidness = liquidSheetSolidness(1f / 3f),
            ).alpha
        val tallLightAlpha =
            liquidSheetGlassSurfaceColor(
                isDark = false,
                solidness = liquidSheetSolidness(0.75f),
            ).alpha
        val fullLightAlpha =
            liquidSheetGlassSurfaceColor(isDark = false, solidness = liquidSheetSolidness(1f)).alpha
        val tallDarkAlpha =
            liquidSheetGlassSurfaceColor(
                isDark = true,
                solidness = liquidSheetSolidness(0.75f),
            ).alpha

        assertTrue(
            tallLightAlpha > shortLightAlpha + 0.06f,
            "Expected 3/4 detent to add readable tint before full height",
        )
        assertTrue(
            fullLightAlpha > tallLightAlpha,
            "Expected full detent to keep gaining readable tint",
        )
        assertTrue(
            tallDarkAlpha > 0.40f,
            "Expected dark sheet tint to remain readable at 3/4 detent",
        )
        assertTrue(
            liquidSheetSolidness(0.75f) in 0.30f..0.45f,
            "Expected readability curve to engage before full height",
        )
    }

    /**
     * The glass fill now sits on top of a backdrop that actually blurs, so it can be lighter than the
     * opaque fallback — but not as light as it used to be. The pre-rewrite floors (0.28 light / 0.34
     * dark) were tuned inside a Dialog window where the blur silently drew nothing, so they were
     * really being asked to carry legibility on their own and could not.
     */
    @Test
    fun glassFillStaysLighterThanTheOpaqueFallbackButAboveTheLegibilityFloor() {
        val shortGlass =
            liquidSheetGlassSurfaceColor(
                isDark = false,
                solidness = liquidSheetSolidness(1f / 3f),
            ).alpha
        val shortOpaque =
            liquidSheetSurfaceColor(
                isDark = false,
                solidness = liquidSheetSolidness(1f / 3f),
            ).alpha

        assertTrue(
            shortGlass < shortOpaque,
            "Glass leans on the blur, so its fill must stay lighter than the no-backdrop fallback",
        )
        assertTrue(
            shortGlass >= 0.38f,
            "A partial-detent sheet still needs enough fill to read over busy content",
        )
        assertTrue(
            liquidSheetGlassSurfaceColor(
                isDark = false,
                solidness = liquidSheetSolidness(1f / 3f),
                surfaceTone = LiquidSheetSurfaceTone.Readable,
            ).alpha > shortGlass,
            "The Readable tone must be more opaque than Default at the same height",
        )
    }

    @Test
    fun adaptiveInitialDetentPromotesOnlyThreeQuarterOverflow() {
        assertEquals(
            LiquidSheetInitialDetent.Full,
            liquidSheetAdaptedInitialDetent(
                initialDetent = LiquidSheetInitialDetent.ThreeQuarter,
                contentOverflowsOpeningDetent = true,
            ),
        )
        assertEquals(
            LiquidSheetInitialDetent.Half,
            liquidSheetAdaptedInitialDetent(
                initialDetent = LiquidSheetInitialDetent.Half,
                contentOverflowsOpeningDetent = true,
            ),
        )
    }
}
