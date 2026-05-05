package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok

internal data class BaNotificationSettingsSheetState(
    val apNotifyEnabled: Boolean,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
    val apNotifyThresholdText: String,
)

@Composable
internal fun BaNotificationSettingsSheet(
    show: Boolean,
    backdrop: Backdrop?,
    state: BaNotificationSettingsSheetState,
    onApNotifyEnabledChange: (Boolean) -> Unit,
    onArenaRefreshNotifyEnabledChange: (Boolean) -> Unit,
    onCafeVisitNotifyEnabledChange: (Boolean) -> Unit,
    onApNotifyThresholdTextChange: (String) -> Unit,
    onApNotifyThresholdDone: () -> Unit,
    onDismissRequest: () -> Unit,
    onSaveRequest: () -> Unit,
) {
    val settingsAccent = Color(0xFF3B82F6)
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_notification_settings_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = onDismissRequest,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Ok,
                contentDescription = stringResource(R.string.common_save),
                variant = GlassVariant.Bar,
                onClick = onSaveRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(stringResource(R.string.ba_settings_section_notifications))
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_ap_title),
                    color = settingsAccent,
                )
                SheetControlRow(label = stringResource(R.string.ba_settings_label_ap_notify)) {
                    AppSwitch(
                        checked = state.apNotifyEnabled,
                        onCheckedChange = onApNotifyEnabledChange,
                    )
                }
                if (state.apNotifyEnabled) {
                    SheetControlRow(
                        label = stringResource(R.string.ba_settings_label_ap_threshold),
                        summary = stringResource(
                            R.string.ba_settings_summary_ap_threshold,
                            BA_AP_MAX
                        ),
                    ) {
                        AppLiquidSearchField(
                            modifier = Modifier.width(70.dp),
                            value = state.apNotifyThresholdText,
                            onValueChange = { input ->
                                onApNotifyThresholdTextChange(normalizeBaApThresholdInput(input))
                            },
                            onImeActionDone = onApNotifyThresholdDone,
                            label = "120",
                            backdrop = backdrop,
                            variant = GlassVariant.SheetInput,
                            singleLine = true,
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp,
                            textColor = Color(0xFF22C55E),
                        )
                    }
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_arena_refresh_notify),
                    summary = stringResource(R.string.ba_settings_summary_arena_refresh_notify),
                ) {
                    AppSwitch(
                        checked = state.arenaRefreshNotifyEnabled,
                        onCheckedChange = onArenaRefreshNotifyEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_cafe_visit_notify),
                    summary = stringResource(R.string.ba_settings_summary_cafe_visit_notify),
                ) {
                    AppSwitch(
                        checked = state.cafeVisitNotifyEnabled,
                        onCheckedChange = onCafeVisitNotifyEnabledChange,
                    )
                }
            }
        }
    }
}

private fun normalizeBaApThresholdInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(3)
    if (digits.isBlank()) return ""
    return digits.toIntOrNull()?.coerceIn(0, BA_AP_MAX)?.toString().orEmpty()
}
