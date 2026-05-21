@file:Suppress("PropertyName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import kotlin.math.abs
import kotlin.math.sign

internal fun baGuideBgmDockPanelOffset(
    rawOffsetPx: Float,
    totalWidthPx: Float,
    density: Density,
): Float {
    if (totalWidthPx <= 0f) return 0f
    val fraction = (rawOffsetPx / totalWidthPx).fastCoerceIn(-1f, 1f)
    return with(density) {
        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
    }
}

internal const val BaGuideBgmDockSelectionWidthFraction = 1f
internal const val BaGuideBgmDockPressedScale = 68f / 54f
internal const val BaGuideBgmDockClickScale = 1.032f
internal const val BaGuideBgmDockVelocityScaleXFactor = 0.48f
internal const val BaGuideBgmDockVelocityScaleYFactor = 0.16f
internal const val BaGuideBgmDockVelocityScaleClamp = 0.12f
internal const val BaGuideBgmDockSelectedContentPressedScale = 1.12f
