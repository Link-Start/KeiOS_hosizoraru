// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.ui.page.main.about.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.TabbedPageBottomChrome

@Composable
internal fun AboutBottomChrome(
    visible: Boolean,
    navigationBarBottom: Dp,
    categories: List<AboutCategory>,
    selectedPage: Int,
    selectedPagePosition: Float?,
    selectedPagePositionProvider: (() -> Float?)? = null,
    selectedPageProvider: () -> Int,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchIcon: ImageVector,
    searchContentDescription: String,
    searchPlaceholder: String,
    backdrop: Backdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectCategory: (Int) -> Unit,
    onExpandDock: () -> Unit,
) {
    TabbedPageBottomChrome(
        visible = visible,
        navigationBarBottom = navigationBarBottom,
        categories = categories,
        selectedPage = selectedPage,
        selectedPagePosition = selectedPagePosition,
        selectedPagePositionProvider = selectedPagePositionProvider,
        selectedPageProvider = selectedPageProvider,
        searchExpanded = searchExpanded,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchExpandedChange = onSearchExpandedChange,
        searchIcon = searchIcon,
        searchContentDescription = searchContentDescription,
        searchPlaceholder = searchPlaceholder,
        backdrop = backdrop,
        isLiquidEffectEnabled = isLiquidEffectEnabled,
        onSelectCategory = onSelectCategory,
        onExpandDock = onExpandDock,
        // Also what tags the search dock and the category tabs, via tabbedPageSearchDockTestTag and
        // tabbedPageCategoryTabTestTag. The component lab lives behind this search: it is not one of
        // About's three overview cards, so nothing else on the page reaches it.
        labelPrefix = "about",
    )
}
