package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Reference text size used to measure the title's intrinsic width. The pill grows or shrinks
 * relative to this baseline so the rendered text always fits without clipping.
 */
private val AppTopBarTitleBaseSize: TextUnit = 18.sp
private val AppTopBarTitleBaseLineHeight: TextUnit = 22.sp
private val AppTopBarTitleMinScale: Float = 0.62f
private val AppTopBarTitleMaxScale: Float = 1f

@Composable
fun AppTopBarTitleCard(
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    startReserve: Dp = AppChromeTokens.topBarTitleEdgePadding,
    endReserve: Dp = AppChromeTokens.topBarTitleEdgePadding,
    onClick: () -> Unit = {},
) {
    if (title.isBlank()) return
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val textMeasurer = rememberTextMeasurer()
    val baseTextStyle =
        remember(fontScale) {
            TextStyle(
                fontSize = AppTopBarTitleBaseSize,
                lineHeight = AppTopBarTitleBaseLineHeight,
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }
    // Measure the actual rendered glyph width at the baseline size. This uses the same font
    // weight, font, and platform metrics the title surface will render with, so no per-character
    // estimator can drift away from reality. fontScale is included in the measurer's density.
    val measuredTextWidthPx =
        remember(title, baseTextStyle, density, textMeasurer) {
            textMeasurer.measure(
                text = AnnotatedString(title),
                style = baseTextStyle,
                density = density,
            ).size.width
        }
    val measuredTextWidth = with(density) { measuredTextWidthPx.toDp() }

    BoxWithConstraints(
        modifier = modifier.padding(start = startReserve, end = endReserve),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth.coerceAtLeast(AppChromeTokens.topBarTitleMinWidth)
        val cardMaxWidth = availableWidth.coerceAtMost(AppChromeTokens.topBarTitleMaxWidth)
        val layout =
            remember(cardMaxWidth, measuredTextWidth) {
                deriveAppTopBarTitleLayout(
                    cardMaxWidth = cardMaxWidth,
                    measuredTextWidthAtBaseSize = measuredTextWidth,
                )
            }
        AppTopBarTitleCardSurface(
            title = title,
            backdrop = backdrop,
            width = layout.cardWidth,
            textSize = layout.textSize,
            lineHeight = layout.lineHeight,
            horizontalPadding = layout.horizontalPadding,
            onClick = onClick,
        )
    }
}

@Immutable
private data class AppTopBarTitleLayout(
    val cardWidth: Dp,
    val textSize: TextUnit,
    val lineHeight: TextUnit,
    val horizontalPadding: Dp,
)

/**
 * Compute the pill width and (if necessary) the shrunk text size from the measured text width.
 *
 * Strategy: try to render the title at full [AppTopBarTitleBaseSize] with comfortable padding.
 * If that doesn't fit in [cardMaxWidth], back off horizontal padding first (it's cheaper than
 * shrinking the glyphs). Only when even the minimum padding still won't fit do we scale the
 * text down — and that scale comes from the *measured* width, not a per-character guess, so
 * it always lands on a value where the rendered glyphs actually fit.
 */
private fun deriveAppTopBarTitleLayout(
    cardMaxWidth: Dp,
    measuredTextWidthAtBaseSize: Dp,
): AppTopBarTitleLayout {
    val comfortablePadding = 18.dp
    val mediumPadding = 14.dp
    val tightPadding = 10.dp
    val measuredTextValue = measuredTextWidthAtBaseSize.value.coerceAtLeast(1f)

    fun fitsAt(padding: Dp): Boolean =
        measuredTextWidthAtBaseSize + padding * 2 <= cardMaxWidth

    val resolvedPadding =
        when {
            fitsAt(comfortablePadding) -> comfortablePadding
            fitsAt(mediumPadding) -> mediumPadding
            else -> tightPadding
        }

    val targetTextWidth = (cardMaxWidth - resolvedPadding * 2).coerceAtLeast(36.dp)
    val rawScale = targetTextWidth.value / measuredTextValue
    val textScale = rawScale.coerceIn(AppTopBarTitleMinScale, AppTopBarTitleMaxScale)
    val titleTextSize = (AppTopBarTitleBaseSize.value * textScale).sp
    val titleLineHeight =
        (AppTopBarTitleBaseLineHeight.value * textScale)
            .coerceAtLeast(15.5f)
            .sp
    // Use an ε of 0.5 px so floating-point noise from the measurer doesn't push us over the
    // edge and clip the last glyph by half a pixel.
    val scaledTextWidth = measuredTextWidthAtBaseSize * textScale + 0.5.dp
    val cardWidth =
        (scaledTextWidth + resolvedPadding * 2)
            .coerceIn(AppChromeTokens.topBarTitleMinWidth, cardMaxWidth)
    return AppTopBarTitleLayout(
        cardWidth = cardWidth,
        textSize = titleTextSize,
        lineHeight = titleLineHeight,
        horizontalPadding = resolvedPadding,
    )
}

@Composable
private fun AppTopBarTitleCardSurface(
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    height: Dp = AppChromeTokens.topBarTitleHeight,
    width: Dp,
    textSize: TextUnit,
    lineHeight: TextUnit,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .width(width),
        contentAlignment = Alignment.Center,
    ) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = title,
            onClick = onClick,
            modifier =
                Modifier
                    .height(height)
                    .width(width),
            textColor = MiuixTheme.colorScheme.onSurface,
            variant = GlassVariant.Bar,
            minHeight = height,
            horizontalPadding = horizontalPadding,
            verticalPadding = 0.dp,
            textMaxLines = 1,
            textOverflow = TextOverflow.Clip,
            textSoftWrap = false,
            textSize = textSize,
            textLineHeight = lineHeight,
            textFontWeight = FontWeight.SemiBold,
            consumeDragChangesForInteraction = false,
        )
    }
}
