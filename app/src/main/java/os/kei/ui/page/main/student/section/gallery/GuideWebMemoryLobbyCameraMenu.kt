@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideUndoIcon
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.LiquidToolbarAction
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import com.composables.icons.lucide.R as LucideR

@Composable
internal fun GuideWebMemoryLobbyCameraMenu(backdrop: Backdrop, camera: GuideWebMemoryLobbyCamera, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    LiquidToolbar(
        backdrop = backdrop,
        actions = listOf(LiquidToolbarAction(appLucideConfigIcon(),
            stringResource(R.string.guide_gallery_dynamic_lobby_adjust_view), { expanded = true }, active = expanded)),
        modifier = modifier.capturePopupAnchor { anchor = it },
    )
    SnapshotWindowListPopup(
        show = expanded,
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBounds = anchor,
        placement = SnapshotPopupPlacement.ButtonEnd,
        onDismissRequest = { expanded = false },
        minWidth = 208.dp,
        maxWidth = 280.dp,
    ) {
        LiquidGlassActionMenu(
            backdrop = backdrop,
            items = listOf(
                LiquidGlassActionMenuActionRow("zoom_in", stringResource(R.string.guide_gallery_dynamic_lobby_zoom_in),
                    leadingIcon = ImageVector.vectorResource(LucideR.drawable.lucide_ic_zoom_in), onClick = camera::zoomIn),
                LiquidGlassActionMenuActionRow("zoom_out", stringResource(R.string.guide_gallery_dynamic_lobby_zoom_out),
                    leadingIcon = ImageVector.vectorResource(LucideR.drawable.lucide_ic_zoom_out), onClick = camera::zoomOut),
                LiquidGlassActionMenuActionRow("reset_view", stringResource(R.string.guide_gallery_dynamic_lobby_reset_view),
                    leadingIcon = appLucideUndoIcon(), onClick = camera::reset),
            ),
            onDismissRequest = { expanded = false },
        )
    }
}
