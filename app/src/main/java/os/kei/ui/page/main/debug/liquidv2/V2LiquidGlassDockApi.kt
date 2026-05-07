package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

internal enum class V2LiquidDockMode {
    Expanded,
    FloatingCompact,
    SplitDock
}

@Immutable
internal data class V2LiquidDockMaterialSpec(
    val outerParameters: V2LiquidParameterSet = V2LiquidParameterSet.dockProminent,
    val blobParameters: V2LiquidParameterSet = V2LiquidParameterSet.dockProminent,
    val rimLightAlpha: Float = 0.40f,
    val edgeChromaticAlpha: Float = 0.18f,
    val causticAlpha: Float = 0.12f,
    val readabilityFillAlpha: Float = 0.10f,
    val contentVibrancy: Float = 1f,
    val captureContentAlpha: Float = 0.86f,
    val blobReadabilityAlpha: Float = 0.10f
)

@Immutable
internal data class V2LiquidDockBlobSpec(
    val minWidthFraction: Float = 1.00f,
    val stretchOnPress: Float = 0.10f,
    val stretchOnDrag: Float = 0.20f,
    val liftDp: Dp = 1.5.dp,
    val cornerMorph: Float = 0.72f,
    val velocityMorph: Float = 0.18f
)

@Immutable
internal data class V2LiquidDockLayoutSpec(
    val mode: V2LiquidDockMode = V2LiquidDockMode.Expanded,
    val height: Dp = V2LiquidGlassTokens.dockHeight,
    val itemMinWidth: Dp = V2LiquidGlassTokens.dockItemMinWidth,
    val outerPadding: Dp = 6.dp,
    val indicatorInset: Dp = V2LiquidGlassTokens.dockIndicatorInset,
    val labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    val iconSize: Dp = 22.dp,
    val badgeOffset: DpOffset = DpOffset(12.dp, (-3).dp)
)

@Immutable
internal data class V2LiquidDockSpec(
    val height: Dp = V2LiquidGlassTokens.dockHeight,
    val collapsedHeight: Dp = V2LiquidGlassTokens.dockCollapsedHeight,
    val itemMinWidth: Dp = V2LiquidGlassTokens.dockItemMinWidth,
    val outerPadding: Dp = 6.dp,
    val indicatorInset: Dp = V2LiquidGlassTokens.dockIndicatorInset,
    val selectedBlobStyle: V2LiquidMaterialStyle = V2LiquidMaterialStyle.ControlThumb,
    val labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    val dragEnabled: Boolean = true,
    val contentTint: Color = Color.Unspecified,
    val activeTint: Color = Color.Unspecified,
    val inactiveTint: Color = Color.Unspecified,
    val badgeStyle: Color = Color.Unspecified,
    val materialSpec: V2LiquidDockMaterialSpec = V2LiquidDockMaterialSpec(),
    val blobSpec: V2LiquidDockBlobSpec = V2LiquidDockBlobSpec(),
    val layoutSpec: V2LiquidDockLayoutSpec = V2LiquidDockLayoutSpec()
)

@Immutable
internal data class V2LiquidDockScrollBehavior(
    val mode: V2LiquidDockMode = V2LiquidDockMode.Expanded
)

@Stable
internal class V2LiquidDockGestureState internal constructor(
    val selectedPosition: Float,
    val pressedIndex: Int,
    val pressProgress: Float,
    val dragProgress: Float
)
