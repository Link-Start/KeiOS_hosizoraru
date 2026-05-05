package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBellIcon
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideFlaskIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun BaTopBar(
    topBarColor: Color,
    scrollBehavior: ScrollBehavior?,
    titleBackdrop: LayerBackdrop? = null,
) {
    AppTopBarSection(
        title = "",
        largeTitle = stringResource(R.string.ba_topbar_title),
        scrollBehavior = scrollBehavior,
        color = topBarColor,
        titleBackdrop = titleBackdrop,
        titleEndReserve = AppChromeTokens.topBarTitleActionReserve,
    )
}

@Composable
internal fun BaTopBarActions(
    backdrop: LayerBackdrop,
    liquidActionBarLayeredStyleEnabled: Boolean,
    onShowSettings: () -> Unit,
    onShowNotificationSettings: () -> Unit,
    onShowDebug: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
) {
    val editIcon = appLucideEditIcon()
    val bellIcon = appLucideBellIcon()
    val debugIcon = appLucideFlaskIcon()
    val editContentDescription = stringResource(R.string.ba_cd_edit)
    val notificationContentDescription = stringResource(R.string.ba_cd_notification_settings)
    val debugContentDescription = stringResource(R.string.ba_cd_debug_tools)
    val actionItems = remember(
        editContentDescription,
        notificationContentDescription,
        debugContentDescription,
        onShowSettings,
        onShowNotificationSettings,
        onShowDebug,
    ) {
        listOf(
            LiquidActionItem(
                icon = bellIcon,
                contentDescription = notificationContentDescription,
                onClick = onShowNotificationSettings,
            ),
            LiquidActionItem(
                icon = editIcon,
                contentDescription = editContentDescription,
                onClick = onShowSettings,
            ),
            LiquidActionItem(
                icon = debugIcon,
                contentDescription = debugContentDescription,
                onClick = onShowDebug,
            )
        )
    }

    LiquidActionBar(
        backdrop = backdrop,
        layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
        items = actionItems,
        onInteractionChanged = onInteractionChanged,
    )
}
