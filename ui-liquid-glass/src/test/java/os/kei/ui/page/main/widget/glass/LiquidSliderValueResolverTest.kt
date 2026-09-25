package os.kei.ui.page.main.widget.glass

import org.junit.Test
import kotlin.test.assertEquals

class LiquidSliderValueResolverTest {
    private val keyPoints =
        listOf(
            LiquidSliderKeyPoint(0.25f),
            LiquidSliderKeyPoint(0.50f),
            LiquidSliderKeyPoint(0.75f),
        )

    @Test
    fun progressChangesResolveAgainstKeyPointsAndTheRange() {
        data class Case(
            val label: String,
            val current: Float,
            val target: Float,
            val expected: Float?,
            val snapThreshold: Float? = 0.05f,
            val snapToKeyPoints: Boolean = true,
            val keyPoints: List<LiquidSliderKeyPoint> = this@LiquidSliderValueResolverTest.keyPoints,
        )
        listOf(
            // Magnet threshold: accessibility progress snaps exactly as touch does.
            Case("snaps to a key point inside the threshold", current = 0.25f, target = 0.53f, expected = 0.50f),
            Case("keeps the target outside the threshold", current = 0.25f, target = 0.57f, expected = 0.57f),
            Case("keeps moving off a key point outside the threshold", current = 0.50f, target = 0.51f, expected = 0.51f),
            // Fully discrete (no threshold): each step moves to the neighbouring key point.
            Case("discrete step moves to the next key point", current = 0.25f, target = 0.26f, expected = 0.50f, snapThreshold = null),
            Case("discrete step moves to the previous key point", current = 0.50f, target = 0.49f, expected = 0.25f, snapThreshold = null),
            Case("discrete step stops at the first key point", current = 0.25f, target = 0.24f, expected = null, snapThreshold = null),
            Case("discrete step stops at the last key point", current = 0.75f, target = 0.76f, expected = null, snapThreshold = null),
            Case("discrete step between key points keeps direction down", current = 0.40f, target = 0.39f, expected = 0.25f, snapThreshold = null),
            Case("discrete step between key points keeps direction up", current = 0.40f, target = 0.41f, expected = 0.50f, snapThreshold = null),
            Case(
                "targets are clamped to the range",
                current = 0.50f,
                target = 4f,
                expected = 1f,
                snapThreshold = null,
                snapToKeyPoints = false,
                keyPoints = emptyList(),
            ),
            // An invalid target keeps the current finite value.
            Case("NaN target is ignored", current = 0.40f, target = Float.NaN, expected = null),
            Case("infinite target is ignored", current = 0.40f, target = Float.POSITIVE_INFINITY, expected = null),
        ).forEach { case ->
            assertEquals(
                case.expected,
                resolveSliderProgressChange(
                    currentValue = case.current,
                    target = case.target,
                    valueRange = 0f..1f,
                    keyPoints = case.keyPoints,
                    snapToKeyPoints = case.snapToKeyPoints,
                    snapThreshold = case.snapThreshold,
                ),
                case.label,
            )
        }
    }

    @Test
    fun reversedRangesAreNormalizedBeforeResolvingTargets() {
        assertEquals(
            0.25f,
            resolveSliderTarget(
                target = 0.25f,
                valueRange = 1f..0f,
                keyPoints = emptyList(),
                snapToKeyPoints = false,
                snapThreshold = null,
            ),
        )
    }

    @Test
    fun invalidKeyPointsDoNotParticipateInSnapping() {
        assertEquals(
            0.52f,
            resolveSliderTarget(
                target = 0.52f,
                valueRange = 0f..1f,
                keyPoints =
                    listOf(
                        LiquidSliderKeyPoint(Float.NaN),
                        LiquidSliderKeyPoint(Float.POSITIVE_INFINITY),
                    ),
                snapToKeyPoints = true,
                snapThreshold = null,
            ),
        )
    }
}
