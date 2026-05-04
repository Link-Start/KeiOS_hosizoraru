package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideSortIcon
import os.kei.ui.page.main.student.catalog.component.BaGuideCatalogSortActionPopup
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogSortMode
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionBarPopupAnchors
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogMusicTopBar(
    title: String,
    accent: Color,
    onBack: () -> Unit,
    showSortPopup: Boolean,
    sortMode: BaGuideCatalogSortMode,
    onSort: () -> Unit,
    onDismissSort: () -> Unit,
    onSelectSortMode: (BaGuideCatalogSortMode) -> Unit,
    onTransfer: () -> Unit,
    onRefresh: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val sortContentDescription = stringResource(R.string.ba_catalog_action_sort)
    val refreshContentDescription = stringResource(R.string.ba_catalog_action_refresh)
    val transferContentDescription = stringResource(R.string.ba_catalog_action_transfer)
    val sortIcon = appLucideSortIcon()
    val refreshIcon = appLucideRefreshIcon()
    val moreIcon = appLucideMoreIcon()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppChromeTokens.liquidActionBarOuterHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = backdrop
            )
            Box {
                LiquidActionBar(
                    modifier = Modifier.height(AppChromeTokens.liquidActionBarOuterHeight),
                    backdrop = backdrop,
                    selectedIndex = 0,
                    items = remember(
                        sortContentDescription,
                        transferContentDescription,
                        refreshContentDescription,
                        sortIcon,
                        moreIcon,
                        refreshIcon,
                        onSort,
                        onTransfer,
                        onRefresh
                    ) {
                        listOf(
                            LiquidActionItem(
                                icon = sortIcon,
                                contentDescription = sortContentDescription,
                                onClick = onSort
                            ),
                            LiquidActionItem(
                                icon = moreIcon,
                                contentDescription = transferContentDescription,
                                onClick = onTransfer
                            ),
                            LiquidActionItem(
                                icon = refreshIcon,
                                contentDescription = refreshContentDescription,
                                onClick = onRefresh
                            )
                        )
                    }
                )
                LiquidActionBarPopupAnchors(itemCount = 3) { slotIndex, popupAnchorBounds ->
                    if (slotIndex != 0) return@LiquidActionBarPopupAnchors
                    BaGuideCatalogSortActionPopup(
                        show = showSortPopup,
                        anchorBounds = popupAnchorBounds,
                        sortMode = sortMode,
                        onDismissRequest = onDismissSort,
                        onSelectSortMode = onSelectSortMode
                    )
                }
            }
        }
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.SectionTitle.fontSize,
            lineHeight = AppTypographyTokens.SectionTitle.lineHeight,
            fontWeight = AppTypographyTokens.SectionTitle.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp, end = 172.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
internal fun BaGuideCatalogMusicPlaceholder(
    label: String,
    topPadding: Dp,
    bottomPadding: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = topPadding,
                bottom = bottomPadding,
                start = AppChromeTokens.pageHorizontalPadding,
                end = AppChromeTokens.pageHorizontalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight
        )
    }
}
