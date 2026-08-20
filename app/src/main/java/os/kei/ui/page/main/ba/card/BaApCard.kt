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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.BaPageClockState
import os.kei.ui.page.main.ba.support.calculateApFullAtMs
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
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
    onOpenApLimitTools: () -> Unit,
) {
    val uiMinuteMs = clockState.uiMinuteMs.longValue
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val apSyncTimeText =
        if (apSyncMs > 0L) formatBaDateTimeNoSeconds(apSyncMs, notSyncedText) else notSyncedText
    val apFullAt =
        calculateApFullAtMs(
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            nowMs = uiMinuteMs,
        )
    val apFullTimeText = formatBaDateTimeNoSeconds(apFullAt, notSyncedText)
    val apFullStatusText =
        stringResource(
            R.string.ba_ap_full_remaining_format,
            formatBaRemainingTime(apFullAt, uiMinuteMs),
        )
    val apRegenRateText = stringResource(R.string.ba_ap_regen_rate_format, 6)
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
            trailing = {
                StatusPill(
                    label = apFullStatusText,
                    color = accentBlue,
                    size = AppStatusPillSize.Compact,
                    backdrop = backdrop,
                )
                StatusPill(
                    label = apRegenRateText,
                    color = accentGreen,
                    size = AppStatusPillSize.Compact,
                    backdrop = backdrop,
                )
            },
        )
        BaApInputPanel(
            backdrop = backdrop,
            accentGreen = accentGreen,
            apLimit = apLimit,
            apCurrentInput = apCurrentInput,
            onApCurrentInputChange = onApCurrentInputChange,
            onApCurrentDone = onApCurrentDone,
            onOpenApLimitTools = onOpenApLimitTools,
        )
        BaCompactDualMetricPanel(
            backdrop = backdrop,
            firstLabel = stringResource(R.string.ba_metric_ap_sync),
            firstValue = apSyncTimeText,
            secondLabel = stringResource(R.string.ba_metric_ap_full),
            secondValue = apFullTimeText,
            accentColor = accentBlue,
            valueColor = accentBlue,
        )
    }
}

@Composable
private fun BaApInputPanel(
    backdrop: Backdrop?,
    accentGreen: Color,
    apLimit: Int,
    apCurrentInput: String,
    onApCurrentInputChange: (String) -> Unit,
    onApCurrentDone: () -> Unit,
    onOpenApLimitTools: () -> Unit,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        flattenOverUniformParent = true,
        accentColor = accentGreen,
        onLongClick = onOpenApLimitTools,
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
                BaLimitValueText(
                    text = apLimit.toString(),
                    color = accentGreen,
                )
            }
        }
    }
}
