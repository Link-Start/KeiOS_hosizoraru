package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object V2LiquidGlassTokens {
    val blurSoft: Dp = 4.dp
    val blurBalanced: Dp = 8.dp
    val blurStrong: Dp = 10.dp
    val lensSoft: Dp = 16.dp
    val lensBalanced: Dp = 24.dp
    val lensStrong: Dp = 32.dp
    val radiusCompact: Dp = 18.dp
    val radiusCard: Dp = 28.dp
    val radiusPanel: Dp = 36.dp
    val spacingXs: Dp = 6.dp
    val spacingSm: Dp = 10.dp
    val spacingMd: Dp = 14.dp
    val spacingLg: Dp = 18.dp
    val spacingXl: Dp = 24.dp
    val controlHeight: Dp = 46.dp
    val dockHeight: Dp = 62.dp
    val switchWidth: Dp = 62.dp
    val switchHeight: Dp = 36.dp
    val sliderHeight: Dp = 46.dp
    val sliderCompactHeight: Dp = 38.dp
    val dockIndicatorInset: Dp = 6.dp
    val dockItemMinWidth: Dp = 54.dp
    const val pressMotionMs: Int = 130
    const val stateMotionMs: Int = 180
    const val overlayMotionMs: Int = 190
}

@Immutable
internal data class V2LiquidGlassPalette(
    val accent: Color,
    val content: Color,
    val secondary: Color,
    val panelTint: Color,
    val clearTint: Color,
    val danger: Color,
    val success: Color,
    val warning: Color
)

@Composable
internal fun rememberV2LiquidGlassPalette(
    accent: Color = MiuixTheme.colorScheme.primary
): V2LiquidGlassPalette {
    val isDark = isSystemInDarkTheme()
    return V2LiquidGlassPalette(
        accent = accent,
        content = MiuixTheme.colorScheme.onBackground,
        secondary = if (isDark) {
            Color.White.copy(alpha = 0.80f)
        } else {
            Color(0xFF253044).copy(alpha = 0.82f)
        },
        panelTint = if (isDark) {
            Color(0xFF11161B).copy(alpha = 0.24f)
        } else {
            Color.White.copy(alpha = 0.30f)
        },
        clearTint = if (isDark) {
            Color.White.copy(alpha = 0.075f)
        } else {
            Color.White.copy(alpha = 0.18f)
        },
        danger = Color(0xFFFF4D6D),
        success = Color(0xFF21C982),
        warning = Color(0xFFFFB43D)
    )
}

internal enum class V2GlassControlSize {
    Compact,
    Regular,
    Large
}

internal enum class V2GlassRole {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger
}

internal enum class V2GlassSelectionStyle {
    Indicator,
    Fill,
    Outline
}

internal enum class V2GlassContentDensity {
    Compact,
    Comfortable,
    Spacious
}

internal enum class V2GlassBackdropPolicy {
    Parent,
    ExportChild,
    CombinedChild
}

internal enum class V2LiquidMaterialStyle {
    Clear,
    Regular,
    Prominent,
    Tinted,
    Dock,
    Widget,
    ControlThumb
}

internal enum class V2GlassTabLabelPolicy {
    Always,
    Selected,
    Never
}

@Immutable
internal data class V2GlassMotionSpec(
    val pressDurationMs: Int = V2LiquidGlassTokens.pressMotionMs,
    val stateDurationMs: Int = V2LiquidGlassTokens.stateMotionMs,
    val pressScaleX: Float = 1.018f,
    val pressScaleY: Float = 0.992f,
    val pressLift: Dp = 1.5.dp
)

@Immutable
internal data class V2GlassBorderSpec(
    val width: Dp = 1.dp,
    val color: Color = Color.Unspecified,
    val alpha: Float = 1f,
    val pressedAlphaMultiplier: Float = 0.55f
)

@Immutable
internal data class V2GlassShadowSpec(
    val radius: Dp = 18.dp,
    val alpha: Float = 0.10f,
    val disabledAlpha: Float = 0.02f,
    val innerRadius: Dp = 7.dp
)

@Immutable
internal data class V2LiquidParameterSet(
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true
) {
    fun bounded(): V2LiquidParameterSet {
        return copy(
            blur = blur.coerceIn(0.dp, 28.dp),
            refractionHeight = refractionHeight.coerceIn(0.dp, 48.dp),
            refractionAmount = refractionAmount.coerceIn(0.dp, 56.dp)
        )
    }

    companion object {
        val sampleClear = V2LiquidParameterSet(
            blur = 4.dp,
            refractionHeight = 16.dp,
            refractionAmount = 32.dp,
            chromaticAberration = true,
            depthEffect = true
        )
        val controlRegular = V2LiquidParameterSet(
            blur = 6.dp,
            refractionHeight = 18.dp,
            refractionAmount = 30.dp,
            chromaticAberration = true,
            depthEffect = true
        )
        val dockProminent = V2LiquidParameterSet(
            blur = 5.dp,
            refractionHeight = 18.dp,
            refractionAmount = 34.dp,
            chromaticAberration = true,
            depthEffect = true
        )
        val thumbLens = V2LiquidParameterSet(
            blur = 3.dp,
            refractionHeight = 14.dp,
            refractionAmount = 22.dp,
            chromaticAberration = true,
            depthEffect = true
        )
    }
}

internal typealias V2GlassSurfaceDrawBlock = DrawScope.() -> Unit
internal typealias V2GlassLayerBlock = GraphicsLayerScope.(pressProgress: Float) -> Unit

@Immutable
internal data class V2GlassSurfaceSpec(
    val shape: Shape = RoundedCornerShape(V2LiquidGlassTokens.radiusCard),
    val materialStyle: V2LiquidMaterialStyle = V2LiquidMaterialStyle.Regular,
    val parameters: V2LiquidParameterSet? = null,
    val role: V2GlassRole = V2GlassRole.Neutral,
    val size: V2GlassControlSize = V2GlassControlSize.Regular,
    val density: V2GlassContentDensity = V2GlassContentDensity.Comfortable,
    val tint: Color = Color.Unspecified,
    val surfaceColor: Color = Color.Unspecified,
    val blur: Dp = V2LiquidGlassTokens.blurBalanced,
    val lensHeight: Dp = V2LiquidGlassTokens.lensBalanced,
    val lensAmount: Dp = V2LiquidGlassTokens.lensBalanced,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    val selected: Boolean = false,
    val loading: Boolean = false,
    val pressed: Boolean = false,
    val interactive: Boolean = false,
    val disabled: Boolean = false,
    val contentAlpha: Float = 1f,
    val minWidth: Dp = 0.dp,
    val minHeight: Dp = 0.dp,
    val maxWidth: Dp = Dp.Unspecified,
    val maxHeight: Dp = Dp.Unspecified,
    val highlightAlpha: Float = 0.88f,
    val disabledHighlightAlpha: Float = 0.32f,
    val clearDimmingAlpha: Float = 0f,
    val rimLightAlpha: Float = 0.28f,
    val edgeChromaticAlpha: Float = 0.12f,
    val causticAlpha: Float = 0.08f,
    val contentVibrancy: Float = 1f,
    val backgroundReadability: Float = 0f,
    val shapeMorph: Float = 0f,
    val border: V2GlassBorderSpec = V2GlassBorderSpec(),
    val shadow: V2GlassShadowSpec = V2GlassShadowSpec(),
    val motion: V2GlassMotionSpec = V2GlassMotionSpec(),
    val semanticsRole: Role? = null,
    val backdropPolicy: V2GlassBackdropPolicy = V2GlassBackdropPolicy.Parent,
    val onDrawSurface: V2GlassSurfaceDrawBlock? = null,
    val gestureTransform: V2GlassLayerBlock? = null,
    val layerBlock: V2GlassLayerBlock? = null
) {
    companion object {
        fun capsule(
            tint: Color = Color.Unspecified,
            surfaceColor: Color = Color.Unspecified,
            interactive: Boolean = true,
            role: V2GlassRole = V2GlassRole.Neutral,
            size: V2GlassControlSize = V2GlassControlSize.Regular,
            density: V2GlassContentDensity = V2GlassContentDensity.Comfortable
        ): V2GlassSurfaceSpec {
            return V2GlassSurfaceSpec(
                shape = ContinuousCapsule,
                role = role,
                size = size,
                density = density,
                tint = tint,
                surfaceColor = surfaceColor,
                interactive = interactive
            )
        }
    }
}

@Stable
internal fun V2LiquidGlassPalette.roleTint(
    role: V2GlassRole,
    alpha: Float
): Color {
    return when (role) {
        V2GlassRole.Neutral -> Color.Unspecified
        V2GlassRole.Accent -> accent.copy(alpha = alpha)
        V2GlassRole.Success -> success.copy(alpha = alpha)
        V2GlassRole.Warning -> warning.copy(alpha = alpha)
        V2GlassRole.Danger -> danger.copy(alpha = alpha)
    }
}
