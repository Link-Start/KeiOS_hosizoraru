@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem

@Composable
internal fun BaCalendarPoolActionBar(
    backdrop: Backdrop,
    settingsContentDescription: String,
    refreshContentDescription: String,
    refreshing: Boolean,
    refreshIconRotation: Float,
    refreshingTint: Color,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val settingsIcon = appLucideConfigIcon()
    val refreshIcon = appLucideRefreshIcon()
    val actionItems =
        remember(
            settingsContentDescription,
            refreshContentDescription,
            refreshing,
            refreshIconRotation,
            refreshingTint,
            onOpenSettings,
            onRefresh,
        ) {
            listOf(
                LiquidActionItem(
                    icon = settingsIcon,
                    contentDescription = settingsContentDescription,
                    onClick = onOpenSettings,
                ),
                LiquidActionItem(
                    icon = refreshIcon,
                    contentDescription = refreshContentDescription,
                    onClick = onRefresh,
                    iconRotationDegrees = refreshIconRotation,
                    iconTint = refreshingTint.takeIf { refreshing },
                ),
            )
        }

    LiquidActionBar(
        backdrop = backdrop,
        layeredStyleEnabled = true,
        items = actionItems,
        selectedIndex = if (refreshing) 1 else 0,
    )
}
