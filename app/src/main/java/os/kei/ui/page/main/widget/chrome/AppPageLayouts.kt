package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

fun appPageContentPadding(
    innerPadding: PaddingValues,
    bottomExtra: Dp = AppChromeTokens.pageBottomInsetExtra,
    topExtra: Dp = 0.dp
): PaddingValues {
    return PaddingValues(
        top = innerPadding.calculateTopPadding() + topExtra,
        bottom = innerPadding.calculateBottomPadding() + bottomExtra,
        start = AppChromeTokens.pageHorizontalPadding,
        end = AppChromeTokens.pageHorizontalPadding
    )
}

fun appPageBottomPaddingWithFloatingOverlay(contentBottomPadding: Dp): Dp {
    return contentBottomPadding + AppChromeTokens.pageFloatingOverlayBottomExtra
}

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    MiuixScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun AppPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    scrollBehavior: ScrollBehavior? = null,
    topBarColor: Color = Color.Transparent,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleBackdrop: Backdrop? = null,
    reserveTopEndActionSpace: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
    searchBarVisible: Boolean = false,
    searchBarAnimationLabelPrefix: String = "appPageSearch",
    searchBarContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = modifier) {
        AppScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppTopBarSection(
                    title = title,
                    largeTitle = largeTitle,
                    scrollBehavior = scrollBehavior,
                    color = topBarColor,
                    navigationIcon = navigationIcon,
                    titleBackdrop = titleBackdrop,
                    titleEndReserve = if (reserveTopEndActionSpace) {
                        AppChromeTokens.topBarTitleActionReserve
                    } else {
                        null
                    },
                    searchBarVisible = searchBarVisible,
                    searchBarAnimationLabelPrefix = searchBarAnimationLabelPrefix,
                    searchBarContent = searchBarContent
                )
            },
            bottomBar = bottomBar,
            content = content
        )
        AppTopEndActionBarOverlay {
            Row {
                actions()
            }
        }
    }
}

@Composable
fun AppPageLazyColumn(
    innerPadding: PaddingValues,
    state: LazyListState,
    modifier: Modifier = Modifier,
    bottomExtra: Dp = AppChromeTokens.pageBottomInsetExtra,
    topExtra: Dp = AppChromeTokens.topBarToHeaderGap,
    sectionSpacing: Dp = AppChromeTokens.pageSectionGapLarge,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        overscrollEffect = null,
        userScrollEnabled = userScrollEnabled,
        contentPadding = appPageContentPadding(
            innerPadding = innerPadding,
            bottomExtra = bottomExtra,
            topExtra = topExtra
        ),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        content = content
    )
}
