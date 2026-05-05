package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppTopBarTitleCard(
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    startReserve: Dp = AppChromeTokens.topBarTitleEdgePadding,
    endReserve: Dp = AppChromeTokens.topBarTitleEdgePadding,
) {
    if (title.isBlank()) return
    BoxWithConstraints(
        modifier = modifier.padding(start = startReserve, end = endReserve),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth.coerceAtLeast(AppChromeTokens.topBarTitleMinWidth)
        val cardMaxWidth = availableWidth.coerceAtMost(AppChromeTokens.topBarTitleMaxWidth)
        val horizontalPadding = when {
            cardMaxWidth < 112.dp -> 13.dp
            cardMaxWidth < 150.dp -> 15.dp
            else -> 18.dp
        }
        val estimatedTextWidthAt18Sp = title.sumOf { char ->
            when {
                char.code <= 0x007F -> 10
                char.isJapaneseKana() -> 18
                else -> 19
            }
        }.dp
        val targetContentWidth = (cardMaxWidth - horizontalPadding * 2)
            .coerceAtLeast(36.dp)
        val textScale = (targetContentWidth.value / estimatedTextWidthAt18Sp.value)
            .coerceIn(0.62f, 1f)
        val titleTextSize = (18f * textScale).sp
        val titleLineHeight = (22f * textScale).coerceAtLeast(15.5f).sp
        val scaledTextWidth = estimatedTextWidthAt18Sp * textScale
        val cardWidth = (scaledTextWidth + horizontalPadding * 2)
            .coerceIn(AppChromeTokens.topBarTitleMinWidth, cardMaxWidth)
        AppTopBarTitleCardSurface(
            title = title,
            backdrop = backdrop,
            width = cardWidth,
            textSize = titleTextSize,
            lineHeight = titleLineHeight,
            horizontalPadding = horizontalPadding,
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
) {
    AppLiquidFloatingSurface(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = AppChromeTokens.topBarTitleMinWidth)
            .width(width),
        shape = ContinuousCapsule,
        backdrop = backdrop,
        clipContent = true,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = textSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
    }
}

private fun Char.isJapaneseKana(): Boolean {
    return this in '\u3040'..'\u30FF' || this in '\u31F0'..'\u31FF'
}
