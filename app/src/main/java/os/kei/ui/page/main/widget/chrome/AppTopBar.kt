package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.ScrollBehavior

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
    titleEndReserve: Dp? = null,
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appTopBarSearch",
    onTitleClick: () -> Unit = {},
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val barHeight = AppChromeTokens.topBarCollapsedHeight
    val topBarTitle = title.ifBlank { largeTitle }
    val resolvedTitleStartReserve =
        titleStartReserve ?: if (navigationIcon != null) {
            AppChromeTokens.topBarTitleNavigationReserve
        } else {
            AppChromeTokens.topBarTitleEdgePadding
        }
    val resolvedTitleEndReserve =
        titleEndReserve ?: if (navigationIcon != null) {
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(safeTop + barHeight)
                    .background(color)
                    .padding(top = safeTop),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = AppChromeTokens.topBarHorizontalPadding,
                            top = AppChromeTokens.topBarChromeTopPadding,
                        ),
            ) {
                navigationIcon?.invoke()
            }
            AppTopBarTitleCard(
                title = topBarTitle,
                backdrop = titleBackdrop,
                startReserve = resolvedTitleStartReserve,
                endReserve = resolvedTitleEndReserve,
                onClick = onTitleClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = AppChromeTokens.topBarChromeTopPadding),
            )
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            end = AppChromeTokens.topBarHorizontalPadding,
                            top = AppChromeTokens.topBarChromeTopPadding,
                        ),
                content = actions,
            )
        }
        if (searchBarContent != null) {
            SearchBarHost(
                visible = searchBarVisible,
                animationLabelPrefix = searchBarAnimationLabelPrefix,
                content = searchBarContent,
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
    singleLine: Boolean = true,
) {
    Column {
        AppLiquidSearchField(
            modifier = modifier,
            value = value,
            onValueChange = onValueChange,
            label = label,
            backdrop = backdrop,
            variant = GlassVariant.SearchField,
            singleLine = singleLine,
        )
        Spacer(modifier = Modifier.height(AppChromeTokens.searchFieldBottomSpacing))
    }
}
