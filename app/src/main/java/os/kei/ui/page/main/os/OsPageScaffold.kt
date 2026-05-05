package os.kei.ui.page.main.os

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun OsPageScaffoldShell(
    scrollBehavior: ScrollBehavior,
    topBarColor: Color,
    topBarBackdrop: LayerBackdrop,
    layeredStyleEnabled: Boolean,
    manageCardsContentDescription: String,
    manageActivitiesContentDescription: String,
    manageShellCardsContentDescription: String,
    refreshParamsContentDescription: String,
    refreshing: Boolean,
    onOpenCardManager: () -> Unit,
    onOpenActivityVisibilityManager: () -> Unit,
    onOpenShellCardVisibilityManager: () -> Unit,
    onRefresh: () -> Unit,
    onActionBarInteractingChanged: (Boolean) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val manageCardsIcon = appLucideLayersIcon()
    val manageActivitiesIcon = appLucideAppWindowIcon()
    val manageShellCardsIcon = osLucideShellIcon()
    val actionItems = remember(
        manageCardsContentDescription,
        manageActivitiesContentDescription,
        manageShellCardsContentDescription,
        onOpenCardManager,
        onOpenActivityVisibilityManager,
        onOpenShellCardVisibilityManager,
    ) {
        listOf(
            LiquidActionItem(
                icon = manageCardsIcon,
                contentDescription = manageCardsContentDescription,
                onClick = onOpenCardManager
            ),
            LiquidActionItem(
                icon = manageActivitiesIcon,
                contentDescription = manageActivitiesContentDescription,
                onClick = onOpenActivityVisibilityManager
            ),
            LiquidActionItem(
                icon = manageShellCardsIcon,
                contentDescription = manageShellCardsContentDescription,
                onClick = onOpenShellCardVisibilityManager
            )
        )
    }

    AppPageScaffold(
        title = "",
        modifier = Modifier.fillMaxSize(),
        largeTitle = "OS",
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = topBarBackdrop,
        reserveTopEndActionSpace = true,
        actions = {
            LiquidActionBar(
                backdrop = topBarBackdrop,
                layeredStyleEnabled = layeredStyleEnabled,
                items = actionItems,
                onInteractionChanged = onActionBarInteractingChanged
            )
        },
        content = content
    )
}
