@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidMetricPanel
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.BaPageClockState
import os.kei.ui.page.main.ba.support.calculateApFullAtMs
import os.kei.ui.page.main.ba.support.calculateApNextPointAtMs
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaApCard(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    apSyncMs: Long,
    apLimit: Int,
    apCurrent: Double,
    apRegenBaseMs: Long,
    apCurrentInput: String,
    onApCurrentInputChange: (String) -> Unit,
    onApCurrentDone: () -> Unit,
    apLimitInput: String,
    onApLimitInputChange: (String) -> Unit,
    onApLimitDone: () -> Unit,
) {
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val apSyncTimeText =
        if (apSyncMs > 0L) formatBaDateTimeNoSeconds(apSyncMs, notSyncedText) else notSyncedText
    val accentGreen = AppStatusColors.Fresh
    val accentBlue = AppStatusColors.Cached

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentGreen,
        accentAlpha = 0f,
    ) {
        BaCardHeader(
            title = stringResource(R.string.ba_ap_card_title),
            titleIconRes = R.drawable.ba_ap_icon_tight_small,
        )
        BaApInputPanel(
            backdrop = backdrop,
            clockState = clockState,
            accentGreen = accentGreen,
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            apCurrentInput = apCurrentInput,
            onApCurrentInputChange = onApCurrentInputChange,
            onApCurrentDone = onApCurrentDone,
            apLimitInput = apLimitInput,
            onApLimitInputChange = onApLimitInputChange,
            onApLimitDone = onApLimitDone,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BaLiquidMetricPanel(
                backdrop = backdrop,
                label = stringResource(R.string.ba_metric_ap_sync),
                value = apSyncTimeText,
                accentColor = accentBlue,
                valueColor = accentBlue,
                valueMaxLines = 2,
                modifier = Modifier.weight(1f),
            )
            BaApFullMetric(
                backdrop = backdrop,
                clockState = clockState,
                apLimit = apLimit,
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BaApInputPanel(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    accentGreen: Color,
    apLimit: Int,
    apCurrent: Double,
    apRegenBaseMs: Long,
    apCurrentInput: String,
    onApCurrentInputChange: (String) -> Unit,
    onApCurrentDone: () -> Unit,
    apLimitInput: String,
    onApLimitInputChange: (String) -> Unit,
    onApLimitDone: () -> Unit,
) {
    val uiNowMs = clockState.uiNowMs.longValue
    val uiMinuteMs = clockState.uiMinuteMs.longValue
    val apNextPointAt =
        calculateApNextPointAtMs(
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            nowMs = uiNowMs,
        )
    val apFullAt =
        calculateApFullAtMs(
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            nowMs = uiMinuteMs,
        )
    val apNextPointRemain = formatBaRemainingTime(apNextPointAt, uiNowMs, includeSeconds = true)
    val apFullText = formatBaRemainingTime(apFullAt, uiMinuteMs)

    BaLiquidPanel(
        backdrop = backdrop,
        accentColor = accentGreen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.heightIn(min = 40.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ba_ap_card_title),
                        color = accentGreen,
                        fontWeight = FontWeight.Bold,
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ba_ap_icon_tight_small),
                        contentDescription = stringResource(R.string.ba_overview_cd_ap_icon),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLiquidSearchField(
                    modifier = Modifier.width(72.dp),
                    value = apCurrentInput,
                    onValueChange = onApCurrentInputChange,
                    onImeActionDone = onApCurrentDone,
                    label = "0",
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    textColor = accentGreen,
                )
                Text("/", color = MiuixTheme.colorScheme.onBackgroundVariant)
                AppLiquidSearchField(
                    modifier = Modifier.width(72.dp),
                    value = apLimitInput,
                    onValueChange = onApLimitInputChange,
                    onImeActionDone = onApLimitDone,
                    label = "240",
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    textColor = accentGreen,
                )
            }
        }
        Text(
            text = stringResource(R.string.ba_overview_ap_regen_status, apNextPointRemain, apFullText),
            color = Color(0xFF60A5FA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BaApFullMetric(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    apLimit: Int,
    apCurrent: Double,
    apRegenBaseMs: Long,
    modifier: Modifier = Modifier,
) {
    val uiMinuteMs = clockState.uiMinuteMs.longValue
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val apFullAt =
        calculateApFullAtMs(
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            nowMs = uiMinuteMs,
        )
    val apFullTimeText = formatBaDateTimeNoSeconds(apFullAt, notSyncedText)
    BaLiquidMetricPanel(
        backdrop = backdrop,
        label = stringResource(R.string.ba_metric_ap_full),
        value = apFullTimeText,
        accentColor = Color(0xFF60A5FA),
        valueColor = Color(0xFF60A5FA),
        valueMaxLines = 2,
        modifier = modifier,
    )
}
