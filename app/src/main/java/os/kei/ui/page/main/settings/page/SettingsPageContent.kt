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
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsSearchContent(
    innerPadding: PaddingValues,
    searchListState: LazyListState,
    matchingSearchTargets: List<SettingsSearchTarget>,
    settingsSearchCardInput: SettingsSearchCardRenderInput,
    scrollNestedConnection: NestedScrollConnection,
    topBarBackdrop: LayerBackdrop,
    bottomBarBackdrop: LayerBackdrop,
    sliderInteractionActive: Boolean,
) {
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = searchListState,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollNestedConnection)
                .layerBackdrop(topBarBackdrop)
                .layerBackdrop(bottomBarBackdrop),
        bottomExtra =
            appPageBottomPaddingWithFloatingOverlay(
                AppChromeTokens.floatingBottomBarOuterHeight,
            ),
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
                settingsCardItem(target.card, settingsSearchCardInput)
            }
        }
    }
}

@Composable
internal fun SettingsCategoryPagerContent(
    innerPadding: PaddingValues,
    pagerState: MainLoadedPagerState,
    categories: List<SettingsCategory>,
    listStates: SettingsCategoryListStates,
    settingsSearchCardInput: SettingsSearchCardRenderInput,
    scrollNestedConnection: NestedScrollConnection,
    topBarBackdrop: LayerBackdrop,
    bottomBarBackdrop: LayerBackdrop,
    sliderInteractionActive: Boolean,
    transitionAnimationsEnabled: Boolean,
    farJumpAlphaProvider: () -> Float,
) {
    MainLoadedPager(
        state = pagerState,
        userScrollEnabled = !sliderInteractionActive,
        animationsEnabled = transitionAnimationsEnabled,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = farJumpAlphaProvider() }
                .layerBackdrop(topBarBackdrop)
                .layerBackdrop(bottomBarBackdrop),
    ) { pageIndex ->
        val category = categories[pageIndex]
        val pageListState = listStates.forCategory(category)
        val pageNestedScrollConnection =
            remember(pageListState, scrollNestedConnection) {
                settingsChromeNestedScrollConnection(
                    listState = pageListState,
                    delegate = scrollNestedConnection,
                )
            }
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = pageListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(pageNestedScrollConnection),
            bottomExtra =
                appPageBottomPaddingWithFloatingOverlay(
                    AppChromeTokens.floatingBottomBarOuterHeight,
                ),
            sectionSpacing = 12.dp,
            userScrollEnabled = !sliderInteractionActive,
        ) {
            settingsCategoryItems(category, settingsSearchCardInput)
        }
    }
}

internal data class SettingsCategoryListStates(
    val access: LazyListState,
    val appearance: LazyListState,
    val effects: LazyListState,
    val data: LazyListState,
) {
    fun forCategory(category: SettingsCategory): LazyListState =
        when (category) {
            SettingsCategory.Access -> access
            SettingsCategory.Appearance -> appearance
            SettingsCategory.Effects -> effects
            SettingsCategory.Data -> data
        }
}
