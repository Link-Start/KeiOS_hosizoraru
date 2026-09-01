@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.R
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageAlternatingLanes
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.tabbedPageContentNestedScrollConnection
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsSearchContent(
    innerPadding: PaddingValues,
    searchListState: LazyListState,
    searchSecondaryState: LazyListState,
    matchingSearchTargets: List<SettingsSearchTarget>,
    settingsSearchCardInput: SettingsSearchCardRenderInput,
    chromeNestedScrollConnection: NestedScrollConnection,
    topBarNestedScrollConnection: NestedScrollConnection,
    topBarBackdrop: LayerBackdrop,
    bottomBarBackdrop: LayerBackdrop,
    sliderInteractionActive: Boolean,
) {
    val searchNestedScrollConnection =
        remember(searchListState, chromeNestedScrollConnection, topBarNestedScrollConnection) {
            tabbedPageContentNestedScrollConnection(
                listState = searchListState,
                chrome = chromeNestedScrollConnection,
                delegate = topBarNestedScrollConnection,
            )
        }
    // Results are cards like any other, so they flow into the same columns. A search that reflowed the page
    // back to one column would read as a different screen rather than a filter of this one.
    val columnCount = appPageColumnCount()
    val contentModifier =
        Modifier
            .fillMaxSize()
            .nestedScroll(searchNestedScrollConnection)
            .layerBackdrop(topBarBackdrop)
            .layerBackdrop(bottomBarBackdrop)
    val bottomExtra =
        appPageBottomPaddingWithFloatingOverlay(
            AppChromeTokens.floatingBottomBarOuterHeight,
        )
    if (matchingSearchTargets.isEmpty() || columnCount < 2) {
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = searchListState,
            modifier = contentModifier,
            bottomExtra = bottomExtra,
            sectionSpacing = 12.dp,
            userScrollEnabled = !sliderInteractionActive,
        ) {
            if (matchingSearchTargets.isEmpty()) {
                item(
                    key = "settings_search_empty",
                    contentType = "settings_search_empty",
                ) {
                    Text(
                        text = stringResource(R.string.common_no_matched_results),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Body.fontSize,
                        lineHeight = AppTypographyTokens.Body.lineHeight,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppChromeTokens.pageHorizontalPadding),
                    )
                }
            } else {
                matchingSearchTargets.forEach { target ->
                    settingsCardItem(
                        card = target.card,
                        input = settingsSearchCardInput,
                        isSearchResult = true,
                    )
                }
            }
        }
        return
    }
    val (searchLeft, searchRight) =
        appPageAlternatingLanes(matchingSearchTargets.map { target -> target.card })
    AppPageTwoColumnLists(
        innerPadding = innerPadding,
        primaryState = searchListState,
        secondaryState = searchSecondaryState,
        modifier = contentModifier,
        bottomExtra = bottomExtra,
        sectionSpacing = 12.dp,
        userScrollEnabled = !sliderInteractionActive,
        primary = { settingsCardLane(searchLeft, settingsSearchCardInput, isSearchResult = true) },
        secondary = { settingsCardLane(searchRight, settingsSearchCardInput, isSearchResult = true) },
    )
}

@Composable
internal fun SettingsCategoryPagerContent(
    innerPadding: PaddingValues,
    pagerState: MainLoadedPagerState,
    categories: List<SettingsCategory>,
    listStates: SettingsCategoryListStates,
    settingsSearchCardInput: SettingsSearchCardRenderInput,
    chromeNestedScrollConnection: NestedScrollConnection,
    topBarNestedScrollConnection: NestedScrollConnection,
    topBarBackdrop: LayerBackdrop,
    bottomBarBackdrop: LayerBackdrop,
    sliderInteractionActive: Boolean,
    transitionAnimationsEnabled: Boolean,
    farJumpAlphaProvider: () -> Float,
    backdropEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    MainLoadedPager(
        state = pagerState,
        userScrollEnabled = !sliderInteractionActive,
        animationsEnabled = transitionAnimationsEnabled,
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = farJumpAlphaProvider() }
                .then(if (backdropEnabled) Modifier.layerBackdrop(topBarBackdrop).layerBackdrop(bottomBarBackdrop) else Modifier),
    ) { pageIndex ->
        val category = categories[pageIndex]
        val pageListState = listStates.forCategory(category)
        val pageNestedScrollConnection =
            remember(pageListState, chromeNestedScrollConnection, topBarNestedScrollConnection) {
                tabbedPageContentNestedScrollConnection(
                    listState = pageListState,
                    chrome = chromeNestedScrollConnection,
                    delegate = topBarNestedScrollConnection,
                )
            }
        val columnCount = appPageColumnCount()
        val pageModifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(pageNestedScrollConnection)
        val pageBottomExtra =
            appPageBottomPaddingWithFloatingOverlay(
                AppChromeTokens.floatingBottomBarOuterHeight,
            )
        if (columnCount < 2) {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = pageListState,
                modifier = pageModifier,
                bottomExtra = pageBottomExtra,
                sectionSpacing = 12.dp,
                userScrollEnabled = !sliderInteractionActive,
            ) {
                settingsCategoryItems(category, settingsSearchCardInput)
            }
        } else {
            val (left, right) = appPageAlternatingLanes(settingsCategoryCards(category))
            AppPageTwoColumnLists(
                innerPadding = innerPadding,
                primaryState = pageListState,
                secondaryState = listStates.secondaryForCategory(category),
                modifier = pageModifier,
                bottomExtra = pageBottomExtra,
                sectionSpacing = 12.dp,
                userScrollEnabled = !sliderInteractionActive,
                primary = { settingsCardLane(left, settingsSearchCardInput) },
                secondary = { settingsCardLane(right, settingsSearchCardInput) },
            )
        }
    }
}

/**
 * A scroll position per category, for each of the two shapes a category can take.
 *
 * Both are held rather than one being derived, because the shape can change under a live page: rotating a
 * tablet from a 1280dp landscape into an 800dp portrait keeps two columns, but folding a device or entering
 * split screen drops to one. Keeping both means the position of the shape you return to is still there.
 */
internal data class SettingsCategoryListStates(
    val access: LazyListState,
    val keepAlive: LazyListState,
    val interfaceState: LazyListState,
    val data: LazyListState,
    val accessSecondary: LazyListState,
    val keepAliveSecondary: LazyListState,
    val interfaceSecondary: LazyListState,
    val dataSecondary: LazyListState,
) {
    fun forCategory(category: SettingsCategory): LazyListState =
        when (category) {
            SettingsCategory.Access -> access
            SettingsCategory.KeepAlive -> keepAlive
            SettingsCategory.Interface -> interfaceState
            SettingsCategory.Data -> data
        }

    /** The second column's own scroll position, so the two columns keep their places apart. */
    fun secondaryForCategory(category: SettingsCategory): LazyListState =
        when (category) {
            SettingsCategory.Access -> accessSecondary
            SettingsCategory.KeepAlive -> keepAliveSecondary
            SettingsCategory.Interface -> interfaceSecondary
            SettingsCategory.Data -> dataSecondary
        }
}
