@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogFilterActionPopup
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogSortActionPopup
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogSortMode
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppTopBarTitleCard
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogMusicTopBar(
    title: String,
    accent: Color,
    onBack: () -> Unit,
    showSortPopup: Boolean,
    sortMode: BaGuideCatalogSortMode,
    showFilterPopup: Boolean,
    filterEnabled: Boolean,
    filterDefinitions: List<BaGuideCatalogFilterDefinition>,
    selectedFilterOptions: Map<Int, Set<Int>>,
    onSort: () -> Unit,
    onDismissSort: () -> Unit,
    onSelectSortMode: (BaGuideCatalogSortMode) -> Unit,
    onFilter: () -> Unit,
    onDismissFilter: () -> Unit,
    onToggleFilterOption: (filterId: Int, optionId: Int) -> Unit,
    onClearFilters: () -> Unit,
    onTransfer: () -> Unit,
    onRefresh: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val filterContentDescription = stringResource(R.string.ba_catalog_action_filter)
    val sortContentDescription = stringResource(R.string.ba_catalog_action_sort)
    val refreshContentDescription = stringResource(R.string.ba_catalog_action_refresh)
    val transferContentDescription = stringResource(R.string.ba_catalog_action_transfer)
    val filterIcon = appLucideFilterIcon()
    val sortIcon = appLucideSortIcon()
    val refreshIcon = appLucideRefreshIcon()
    val moreIcon = appLucideMoreIcon()
    var actionBarAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppChromeTokens.liquidActionBarOuterHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = backdrop,
            )
            Box(
                modifier = Modifier.capturePopupAnchor { actionBarAnchorBounds = it },
            ) {
                LiquidActionBar(
                    modifier = Modifier.height(AppChromeTokens.liquidActionBarOuterHeight),
                    backdrop = backdrop,
                    selectedIndex = 0,
                    items =
                        remember(
                            filterContentDescription,
                            sortContentDescription,
                            transferContentDescription,
                            refreshContentDescription,
                            filterIcon,
                            sortIcon,
                            moreIcon,
                            refreshIcon,
                            filterEnabled,
                            onFilter,
                            onSort,
                            onTransfer,
                            onRefresh,
                        ) {
                            listOf(
                                LiquidActionItem(
                                    icon = filterIcon,
                                    contentDescription = filterContentDescription,
                                    onClick = onFilter,
                                    enabled = filterEnabled,
                                ),
                                LiquidActionItem(
                                    icon = sortIcon,
                                    contentDescription = sortContentDescription,
                                    onClick = onSort,
                                ),
                                LiquidActionItem(
                                    icon = moreIcon,
                                    contentDescription = transferContentDescription,
                                    onClick = onTransfer,
                                ),
                                LiquidActionItem(
                                    icon = refreshIcon,
                                    contentDescription = refreshContentDescription,
                                    onClick = onRefresh,
                                ),
                            )
                        },
                )
                LiquidActionBarPopupAnchors(itemCount = 4) { slotIndex, popupAnchorBounds ->
                    when (slotIndex) {
                        0 -> {
                            BaGuideCatalogFilterActionPopup(
                                show = showFilterPopup && filterEnabled,
                                anchorBounds = actionBarAnchorBounds ?: popupAnchorBounds,
                                definitions = filterDefinitions,
                                selectedOptionIdsByFilterId = selectedFilterOptions,
                                onDismissRequest = onDismissFilter,
                                onToggleOption = onToggleFilterOption,
                                onClearFilters = onClearFilters,
                            )
                        }

                        1 -> {
                            BaGuideCatalogSortActionPopup(
                                show = showSortPopup,
                                anchorBounds = popupAnchorBounds,
                                sortMode = sortMode,
                                onDismissRequest = onDismissSort,
                                onSelectSortMode = onSelectSortMode,
                            )
                        }
                    }
                }
            }
        }
        AppTopBarTitleCard(
            title = title,
            backdrop = backdrop,
            startReserve = AppChromeTokens.topBarTitleNavigationReserve,
            endReserve = AppChromeTokens.topBarTitleActionReserve,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
        )
    }
}

@Composable
internal fun BaGuideCatalogMusicPlaceholder(
    label: String,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    top = topPadding,
                    bottom = bottomPadding,
                    start = AppChromeTokens.pageHorizontalPadding,
                    end = AppChromeTokens.pageHorizontalPadding,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
        )
    }
}
