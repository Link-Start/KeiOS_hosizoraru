@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarTitleCard
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.LiquidToolbarAction
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant

@Composable
internal fun GuideWebMemoryLobbyHeader(backdrop: Backdrop, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val titleModifier = if (maxWidth >= 600.dp) {
            Modifier.widthIn(
                max = AppChromeTokens.topBarTitleMaxWidth + AppChromeTokens.topBarTitleNavigationReserve,
            )
        } else {
            Modifier
        }
        AppTopBarTitleCard(
            title = stringResource(R.string.guide_gallery_dynamic_lobby),
            backdrop = backdrop,
            startReserve = AppChromeTokens.topBarTitleNavigationReserve,
            endReserve = 0.dp,
            textOverflow = TextOverflow.Ellipsis,
            modifier = titleModifier.fillMaxWidth(),
        )
        LiquidToolbar(
            backdrop = backdrop,
            actions = listOf(
                LiquidToolbarAction(appLucideCloseIcon(), stringResource(R.string.common_close), onDismiss),
            ),
        )
    }
}

@Composable
internal fun GuideWebMemoryLobbyControls(
    backdrop: Backdrop,
    playing: Boolean,
    actions: List<String>,
    selectedAction: String,
    onTogglePlayback: () -> Unit,
    onSelectAction: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit,
    onHideControls: () -> Unit,
    onActionsRequested: () -> Unit = {},
) {
    var expanded by remember(actions, playing) { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppChromeTokens.liquidToolbarGroupSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidToolbar(
            backdrop = backdrop,
            actions = listOf(
                LiquidToolbarAction(
                    if (playing) appLucidePauseIcon() else appLucidePlayIcon(),
                    stringResource(if (playing) R.string.guide_action_pause else R.string.guide_action_play),
                    onTogglePlayback,
                ),
                LiquidToolbarAction(appLucideRefreshIcon(), stringResource(R.string.guide_action_retry), onRetry),
            ),
        )
        AppDropdownSelector(
            selectedText = stringResource(R.string.guide_gallery_dynamic_lobby_actions),
            options = actions,
            selectedIndex = actions.indexOf(selectedAction),
            expanded = expanded,
            anchorBounds = anchor,
            onExpandedChange = { expanded = it; if (it) onActionsRequested() },
            onAnchorBoundsChange = { anchor = it },
            onSelectedIndexChange = { index -> actions.getOrNull(index)?.let(onSelectAction) },
            modifier = Modifier.weight(1f),
            backdrop = backdrop,
            variant = GlassVariant.Bar,
            minHeight = AppChromeTokens.liquidActionBarOuterHeight,
            anchorFillMaxWidth = true,
            anchorAlignment = Alignment.Center,
            enabled = playing && actions.isNotEmpty(),
            popupMaxHeight = 360.dp,
        )
        LiquidToolbar(
            backdrop = backdrop,
            actions = listOf(
                LiquidToolbarAction(
                    appLucideExternalLinkIcon(),
                    stringResource(R.string.guide_gallery_dynamic_lobby_source),
                    onOpenSource,
                ),
                LiquidToolbarAction(
                    appLucideFullscreenIcon(),
                    stringResource(R.string.guide_gallery_dynamic_lobby_hide_controls),
                    onHideControls,
                ),
            ),
        )
    }
}
