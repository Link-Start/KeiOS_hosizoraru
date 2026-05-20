package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(
        modifier = modifier.padding(start = startReserve, end = endReserve),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth.coerceAtLeast(AppChromeTokens.topBarTitleMinWidth)
        val cardMaxWidth = availableWidth.coerceAtMost(AppChromeTokens.topBarTitleMaxWidth)
        val horizontalPadding =
            when {
                cardMaxWidth < 112.dp -> 13.dp
                cardMaxWidth < 150.dp -> 15.dp
                else -> 18.dp
            }
        val estimatedTextWidthAt18Sp = estimateTopBarTitleWidthAt18Sp(title)
        val targetContentWidth =
            (cardMaxWidth - horizontalPadding * 2)
                .coerceAtLeast(36.dp)
        val scaledEstimate = estimatedTextWidthAt18Sp * fontScale
        val textScale =
            (targetContentWidth.value / scaledEstimate.value)
                .coerceIn(0.52f, 1f)
        val titleTextSize = (18f * textScale).sp
        val titleLineHeight = (22f * textScale).coerceAtLeast(15.5f).sp
        val scaledTextWidth = scaledEstimate * textScale
        val cardWidth =
            (scaledTextWidth + horizontalPadding * 2)
                .coerceIn(AppChromeTokens.topBarTitleMinWidth, cardMaxWidth)
        AppTopBarTitleCardSurface(
            title = title,
            backdrop = backdrop,
            width = cardWidth,
            textSize = titleTextSize,
            lineHeight = titleLineHeight,
            horizontalPadding = horizontalPadding,
            onClick = onClick,
        )
    }
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

internal fun estimateTopBarTitleWidthAt18Sp(title: String): Dp =
    title
        .sumOf { char ->
            when {
                char == ' ' -> 5
                char.code <= 0x007F -> if (char.isUpperCase()) 11 else 10
                char.isJapaneseKana() -> 18
                else -> 19
            }
        }.dp

private fun Char.isJapaneseKana(): Boolean = this in '\u3040'..'\u30FF' || this in '\u31F0'..'\u31FF'
