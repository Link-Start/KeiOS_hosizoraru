package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appTopBarSearch",
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val barHeight = AppChromeTokens.topBarCollapsedHeight
    val topBarTitle = title.ifBlank { largeTitle }
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
            if (topBarTitle.isNotBlank()) {
                AppTopBarLiquidTitleCard(
                    title = topBarTitle,
                    backdrop = titleBackdrop,
                    modifier = Modifier.align(Alignment.Center)
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
private fun AppTopBarLiquidTitleCard(
    title: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
) {
    AppLiquidFloatingSurface(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = 92.dp),
        shape = ContinuousCapsule,
        backdrop = backdrop,
        clipContent = true,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
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
