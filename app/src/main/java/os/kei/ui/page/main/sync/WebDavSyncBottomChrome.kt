package os.kei.ui.page.main.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.chrome.TabbedPageBottomChrome

@Composable
internal fun WebDavSyncBottomChrome(
    visible: Boolean,
    navigationBarBottom: Dp,
    categories: List<WebDavSyncCategory>,
    selectedPage: Int,
    selectedPagePosition: Float?,
    selectedPagePositionProvider: (() -> Float?)? = null,
    selectedPageProvider: () -> Int,
    searchIcon: ImageVector,
    searchContentDescription: String,
    searchPlaceholder: String,
    backdrop: LayerBackdrop,
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
        searchExpanded = false,
        searchQuery = "",
        onSearchQueryChange = {},
        onSearchExpandedChange = {},
        searchIcon = searchIcon,
        searchContentDescription = searchContentDescription,
        searchPlaceholder = searchPlaceholder,
        searchEnabled = false,
        backdrop = backdrop,
        isLiquidEffectEnabled = isLiquidEffectEnabled,
        onSelectCategory = onSelectCategory,
        onExpandDock = onExpandDock,
        labelPrefix = "webdav_sync",
    )
}
