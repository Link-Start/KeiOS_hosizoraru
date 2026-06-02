package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle

@Composable
internal fun BaDebugControlsContent(
    backdrop: Backdrop?,
    onSendApTestNotification: () -> Unit,
    onSendCafeApTestNotification: () -> Unit,
    onSendCafeVisitTestNotification: () -> Unit,
    onSendArenaRefreshTestNotification: () -> Unit,
    onSendCalendarUpcomingTestNotification: () -> Unit,
    onSendCalendarEndingTestNotification: () -> Unit,
    onSendPoolUpcomingTestNotification: () -> Unit,
    onSendPoolEndingTestNotification: () -> Unit,
    onSendCalendarPoolChangeTestNotification: () -> Unit,
    useRealCalendarPoolData: Boolean,
    onUseRealCalendarPoolDataChange: (Boolean) -> Unit,
    onTestCafePlus3Hours: () -> Unit,
) {
    SheetSectionTitle(stringResource(R.string.ba_debug_title))
    SheetSectionCard {
        SheetSectionTitle(stringResource(R.string.ba_debug_section_daily))
        BaDebugActionGrid(
            backdrop = backdrop,
            actions =
                listOf(
                    stringResource(R.string.ba_debug_action_ap_notification) to onSendApTestNotification,
                    stringResource(R.string.ba_debug_action_cafe_ap_notification) to onSendCafeApTestNotification,
                    stringResource(R.string.ba_debug_action_arena_refresh_notification) to onSendArenaRefreshTestNotification,
                    stringResource(R.string.ba_debug_action_cafe_visit_notification) to onSendCafeVisitTestNotification,
                    stringResource(R.string.ba_debug_action_cafe_plus_3h_ap) to onTestCafePlus3Hours,
                )
        )
        SheetSectionTitle(stringResource(R.string.ba_debug_section_calendar_pool))
        SheetControlRow(
            label = stringResource(R.string.ba_debug_label_use_real_calendar_pool_data),
            summary = stringResource(R.string.ba_debug_summary_use_real_calendar_pool_data),
        ) {
            AppSwitch(
                checked = useRealCalendarPoolData,
                onCheckedChange = onUseRealCalendarPoolDataChange,
            )
        }
        BaDebugActionGrid(
            backdrop = backdrop,
            actions =
                listOf(
                    stringResource(R.string.ba_debug_action_calendar_upcoming_notification) to onSendCalendarUpcomingTestNotification,
                    stringResource(R.string.ba_debug_action_calendar_ending_notification) to onSendCalendarEndingTestNotification,
                    stringResource(R.string.ba_debug_action_pool_upcoming_notification) to onSendPoolUpcomingTestNotification,
                    stringResource(R.string.ba_debug_action_pool_ending_notification) to onSendPoolEndingTestNotification,
                    stringResource(R.string.ba_debug_action_calendar_pool_change_notification) to onSendCalendarPoolChangeTestNotification,
                )
        )
    }
}

@Composable
private fun BaDebugActionGrid(
    backdrop: Backdrop?,
    actions: List<Pair<String, () -> Unit>>,
) {
    val rows = actions.chunked(2)
    rows.forEach { rowActions ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowActions.forEach { (label, action) ->
                BaDebugActionButton(
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop,
                    label = label,
                    onClick = action,
                )
            }
            if (rowActions.size == 1) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BaDebugActionButton(
    modifier: Modifier,
    backdrop: Backdrop?,
    label: String,
    onClick: () -> Unit,
) {
    AppLiquidTextButton(
        modifier = modifier,
        backdrop = backdrop,
        text = label,
        textColor = Color(0xFF3B82F6),
        variant = GlassVariant.SheetPrimaryAction,
        textMaxLines = 2,
        onClick = onClick,
    )
}
