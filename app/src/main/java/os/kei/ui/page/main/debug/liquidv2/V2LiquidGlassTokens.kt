package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    val dockHeight: Dp = 66.dp
    const val pressMotionMs: Int = 130
    const val stateMotionMs: Int = 180
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
        secondary = MiuixTheme.colorScheme.onBackgroundVariant,
        panelTint = if (isDark) {
            Color(0xFF101820).copy(alpha = 0.32f)
        } else {
            Color.White.copy(alpha = 0.44f)
        },
        clearTint = if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.26f)
        },
        danger = Color(0xFFFF4D6D),
        success = Color(0xFF21C982),
        warning = Color(0xFFFFB43D)
    )
}

@Immutable
internal data class V2GlassSurfaceSpec(
    val shape: Shape = RoundedCornerShape(V2LiquidGlassTokens.radiusCard),
    val tint: Color = Color.Unspecified,
    val surfaceColor: Color = Color.Unspecified,
    val blur: Dp = V2LiquidGlassTokens.blurBalanced,
    val lensHeight: Dp = V2LiquidGlassTokens.lensBalanced,
    val lensAmount: Dp = V2LiquidGlassTokens.lensBalanced,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    val interactive: Boolean = false,
    val disabled: Boolean = false
) {
    companion object {
        fun capsule(
            tint: Color = Color.Unspecified,
            surfaceColor: Color = Color.Unspecified,
            interactive: Boolean = true
        ): V2GlassSurfaceSpec {
            return V2GlassSurfaceSpec(
                shape = ContinuousCapsule,
                tint = tint,
                surfaceColor = surfaceColor,
                interactive = interactive
            )
        }
    }
}
