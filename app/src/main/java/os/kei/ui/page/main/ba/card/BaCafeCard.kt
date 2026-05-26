@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidMetricPanel
import os.kei.ui.page.main.ba.BaPageClockState
import os.kei.ui.page.main.ba.support.calculateInviteTicketAvailableMs
import os.kei.ui.page.main.ba.support.calculateNextHeadpatAvailableMs
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.nextArenaRefreshMs
import os.kei.ui.page.main.ba.support.nextCafeStudentRefreshMs
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant

@Composable
internal fun BaCafeCard(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    serverIndex: Int,
    cafeLevel: Int,
    cafeLevelOptions: List<Int>,
    showCafeLevelPopup: Boolean,
    cafeLevelPopupAnchorBounds: IntRect?,
    onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onCafeLevelPopupChange: (Boolean) -> Unit,
    onCafeLevelChange: (Int) -> Unit,
    coffeeHeadpatMs: Long,
    coffeeInvite1UsedMs: Long,
    coffeeInvite2UsedMs: Long,
    onTouchHead: () -> Unit,
    onForceResetHeadpatCooldown: () -> Unit,
    onUseInviteTicket1: () -> Unit,
    onForceResetInviteTicket1Cooldown: () -> Unit,
    onUseInviteTicket2: () -> Unit,
    onForceResetInviteTicket2Cooldown: () -> Unit,
) {
    val uiNowMs = clockState.uiMinuteMs.longValue
    val accentPink = Color(0xFFF472B6)
    val countdownBlue = Color(0xFF60A5FA)
    val nextHeadpatAt = calculateNextHeadpatAvailableMs(coffeeHeadpatMs, serverIndex)
    val nextStudentRefreshAt = nextCafeStudentRefreshMs(uiNowMs, serverIndex)
    val nextArenaRefreshAt = nextArenaRefreshMs(uiNowMs, serverIndex)
    val nextHeadpatText =
        if (coffeeHeadpatMs <= 0L || nextHeadpatAt <= uiNowMs) {
            "0m"
        } else {
            formatBaRemainingTime(
                nextHeadpatAt,
                uiNowMs,
            )
        }
    val nextStudentRefreshText = formatBaRemainingTime(nextStudentRefreshAt, uiNowMs)
    val nextArenaRefreshText = formatBaRemainingTime(nextArenaRefreshAt, uiNowMs)
    val invite1AvailableAt = calculateInviteTicketAvailableMs(coffeeInvite1UsedMs)
    val invite2AvailableAt = calculateInviteTicketAvailableMs(coffeeInvite2UsedMs)
    val invite1Ready = coffeeInvite1UsedMs <= 0L || invite1AvailableAt <= uiNowMs
    val invite2Ready = coffeeInvite2UsedMs <= 0L || invite2AvailableAt <= uiNowMs
    val invite1Color = accentPink
    val invite2Color = accentPink
    val invite1Text = if (invite1Ready) "0m" else formatBaRemainingTime(invite1AvailableAt, uiNowMs)
    val invite2Text = if (invite2Ready) "0m" else formatBaRemainingTime(invite2AvailableAt, uiNowMs)
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val invite1TimeText =
        formatBaDateTimeNoSeconds(if (invite1Ready) uiNowMs else invite1AvailableAt, notSyncedText)
    val invite2TimeText =
        formatBaDateTimeNoSeconds(if (invite2Ready) uiNowMs else invite2AvailableAt, notSyncedText)
    val headpatTimeText = if (coffeeHeadpatMs > 0L) formatBaDateTimeNoSeconds(coffeeHeadpatMs, notSyncedText) else "-"

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentPink,
        accentAlpha = 0f,
    ) {
        BaCardHeader(
            title = stringResource(R.string.ba_cafe_title),
            titleIconRes = R.drawable.mp_cafe_small,
            trailing = {
                AppDropdownSelector(
                    selectedText = "Lv$cafeLevel",
                    options = cafeLevelOptions.map { level -> "Lv$level" },
                    selectedIndex = cafeLevelOptions.indexOf(cafeLevel).coerceAtLeast(0),
                    expanded = showCafeLevelPopup,
                    anchorBounds = cafeLevelPopupAnchorBounds,
                    onExpandedChange = onCafeLevelPopupChange,
                    onSelectedIndexChange = { selected ->
                        onCafeLevelChange(cafeLevelOptions[selected])
                    },
                    onAnchorBoundsChange = onCafeLevelPopupAnchorBoundsChange,
                    backdrop = backdrop,
                    variant = GlassVariant.Content,
                    textColor = accentPink,
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BaLiquidMetricPanel(
                backdrop = backdrop,
                label = stringResource(R.string.ba_cafe_metric_tactical_challenge),
                value = nextArenaRefreshText,
                accentColor = accentPink,
                valueColor = countdownBlue,
                modifier = Modifier.weight(1f),
            )
            BaLiquidMetricPanel(
                backdrop = backdrop,
                label = stringResource(R.string.ba_cafe_metric_student_visit),
                value = nextStudentRefreshText,
                accentColor = accentPink,
                valueColor = countdownBlue,
                modifier = Modifier.weight(1f),
            )
        }

        BaInlineActionPanel(
            backdrop = backdrop,
            buttonText = stringResource(R.string.ba_cafe_action_headpat),
            buttonIconRes = R.drawable.fx_tex_ch0071_prop_05_small,
            countdownText = nextHeadpatText,
            timeText = headpatTimeText,
            accentColor = accentPink,
            enabled = coffeeHeadpatMs <= 0L || nextHeadpatAt <= uiNowMs,
            onClick = onTouchHead,
            onLongClick = onForceResetHeadpatCooldown,
        )

        BaInlineActionPanel(
            backdrop = backdrop,
            buttonText = stringResource(R.string.ba_cafe_action_invite_ticket_1),
            buttonIconRes = R.drawable.cafe_icon_coupon_small,
            countdownText = invite1Text,
            timeText = invite1TimeText,
            accentColor = invite1Color,
            enabled = invite1Ready,
            onClick = onUseInviteTicket1,
            onLongClick = onForceResetInviteTicket1Cooldown,
        )

        BaInlineActionPanel(
            backdrop = backdrop,
            buttonText = stringResource(R.string.ba_cafe_action_invite_ticket_2),
            buttonIconRes = R.drawable.cafe_icon_coupon_small,
            countdownText = invite2Text,
            timeText = invite2TimeText,
            accentColor = invite2Color,
            enabled = invite2Ready,
            onClick = onUseInviteTicket2,
            onLongClick = onForceResetInviteTicket2Cooldown,
        )
    }
}
