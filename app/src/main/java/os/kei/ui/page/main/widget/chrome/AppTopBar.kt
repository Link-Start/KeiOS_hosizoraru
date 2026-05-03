package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.ScrollBehavior
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
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appTopBarSearch",
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val density = LocalDensity.current
    val titleAlpha by animateFloatAsState(
        targetValue = 1f - collapsedFraction.coerceIn(0f, 1f),
        label = "appTopBarTitleAlpha"
    )
    val collapsedTitleAlpha by animateFloatAsState(
        targetValue = collapsedFraction.coerceIn(0f, 1f),
        label = "appTopBarCollapsedTitleAlpha"
    )
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val barHeight = if (largeTitle.isBlank() && title.isBlank()) {
        AppChromeTokens.topBarCollapsedHeight
    } else {
        val progress = collapsedFraction.coerceIn(0f, 1f)
        (
            AppChromeTokens.topBarExpandedHeight.value +
                (AppChromeTokens.topBarCollapsedHeight.value - AppChromeTokens.topBarExpandedHeight.value) *
                progress
            ).dp
    }
    SideEffect {
        val collapseRangePx = with(density) {
            if (largeTitle.isBlank() && title.isBlank()) {
                0f
            } else {
                (AppChromeTokens.topBarExpandedHeight - AppChromeTokens.topBarCollapsedHeight).toPx()
            }
        }
        scrollBehavior?.state?.let { state ->
            val limit = -collapseRangePx
            if (state.heightOffsetLimit != limit) {
                state.heightOffsetLimit = limit
                state.heightOffset = state.heightOffset
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
            if (title.isNotBlank()) {
                BasicText(
                    text = title,
                    style = TextStyle(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = collapsedTitleAlpha),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (largeTitle.isNotBlank()) {
                BasicText(
                    text = largeTitle,
                    style = TextStyle(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = AppChromeTokens.pageHorizontalPadding,
                            end = AppChromeTokens.pageHorizontalPadding,
                            bottom = 12.dp
                        )
                )
            }
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
