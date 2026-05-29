// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.ui.page.main.about.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
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
    backdrop: LayerBackdrop,
    isLiquidEffectEnabled: Boolean,
    onSelectCategory: (Int) -> Unit,
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
        labelPrefix = "about",
    )
}
