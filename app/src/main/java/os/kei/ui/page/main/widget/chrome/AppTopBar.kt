package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppTopBarSection(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    color: Color = Color.Transparent,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleBackdrop: Backdrop? = null,
    titleStartReserve: Dp? = null,
    titleEndReserve: Dp = AppChromeTokens.topBarTitleEdgePadding,
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appTopBarSearch",
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val barHeight = AppChromeTokens.topBarCollapsedHeight
    val topBarTitle = title.ifBlank { largeTitle }
    val resolvedTitleStartReserve = titleStartReserve ?: if (navigationIcon != null) {
        AppChromeTokens.topBarTitleNavigationReserve
    } else {
        AppChromeTokens.topBarTitleEdgePadding
    }
    SideEffect {
        scrollBehavior?.state?.let { state ->
            if (state.heightOffsetLimit != 0f) {
                state.heightOffsetLimit = 0f
                state.heightOffset = 0f
            }
        }
    }
    Column(modifier = modifier) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(safeTop + barHeight)
                .background(color)
                .padding(top = safeTop)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = AppChromeTokens.topBarHorizontalPadding, top = 6.dp)
            ) {
                navigationIcon?.invoke()
            }
            AppTopBarTitleSlot(
                title = topBarTitle,
                backdrop = titleBackdrop,
                startReserve = resolvedTitleStartReserve,
                endReserve = titleEndReserve,
                modifier = Modifier.align(Alignment.Center)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = AppChromeTokens.topBarHorizontalPadding, top = 6.dp),
                content = actions
            )
        }
        if (searchBarContent != null) {
            SearchBarHost(
                visible = searchBarVisible,
                animationLabelPrefix = searchBarAnimationLabelPrefix,
                content = searchBarContent
            )
        }
    }
}

@Composable
private fun AppTopBarTitleSlot(
    title: String,
    backdrop: Backdrop?,
    startReserve: Dp,
    endReserve: Dp,
    modifier: Modifier = Modifier,
) {
    if (title.isBlank()) return
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startReserve, end = endReserve),
        contentAlignment = Alignment.Center
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
        AppTopBarLiquidTitleCard(
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
private fun AppTopBarLiquidTitleCard(
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
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
    }
}

private fun Char.isJapaneseKana(): Boolean {
    return this in '\u3040'..'\u30FF' || this in '\u31F0'..'\u31FF'
}

@Composable
fun AppTopBarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    singleLine: Boolean = true
) {
    Column {
        AppLiquidSearchField(
            modifier = modifier,
            value = value,
            onValueChange = onValueChange,
            label = label,
            backdrop = backdrop,
            variant = GlassVariant.Bar,
            singleLine = singleLine
        )
        Spacer(modifier = Modifier.height(AppChromeTokens.searchFieldBottomSpacing))
    }
}
