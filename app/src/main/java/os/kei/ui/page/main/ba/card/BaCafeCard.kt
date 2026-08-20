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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.BaPageClockState
import os.kei.ui.page.main.ba.support.cafeDailyCapacity
import os.kei.ui.page.main.ba.support.cafeHourlyGain
import os.kei.ui.page.main.ba.support.calculateCafeFullAtMs
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.nextArenaRefreshMs
import os.kei.ui.page.main.ba.support.nextCafeStudentRefreshMs
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaCafeCard(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    serverIndex: Int,
    cafeLevel: Int,
    cafeStoredAp: Double,
    cafeLastHourMs: Long,
    cafeStoredApInput: String,
    cafeLevelOptions: List<Int>,
    showCafeLevelPopup: Boolean,
    cafeLevelPopupAnchorBounds: IntRect?,
    onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onCafeLevelPopupChange: (Boolean) -> Unit,
    onCafeLevelChange: (Int) -> Unit,
    onCafeStoredApInputChange: (String) -> Unit,
    onCafeStoredApDone: () -> Unit,
    onOpenCafeApTools: () -> Unit,
    onClaimCafeStoredAp: () -> Unit,
) {
    val uiNowMs = clockState.uiMinuteMs.longValue
    val accentPink = Color(0xFFF472B6)
    val countdownBlue = Color(0xFF60A5FA)
    val nextStudentRefreshAt = nextCafeStudentRefreshMs(uiNowMs, serverIndex)
    val nextArenaRefreshAt = nextArenaRefreshMs(uiNowMs, serverIndex)
    val nextStudentRefreshText = formatBaRemainingTime(nextStudentRefreshAt, uiNowMs)
    val nextArenaRefreshText = formatBaRemainingTime(nextArenaRefreshAt, uiNowMs)
    val cafeHourlyText = stringResource(R.string.ba_cafe_ap_hourly_gain_format, cafeHourlyGain(cafeLevel))
    val cafeCap = cafeDailyCapacity(cafeLevel)
    val cafeFullAt =
        calculateCafeFullAtMs(
            cafeLevel = cafeLevel,
            cafeStoredAp = cafeStoredAp,
            cafeLastHourMs = cafeLastHourMs,
            nowMs = uiNowMs,
        )
    val cafeFullText =
        if (cafeStoredAp >= cafeCap.toDouble()) {
            stringResource(R.string.ba_cafe_ap_full_pill_now)
        } else {
            stringResource(
                R.string.ba_cafe_ap_full_pill_format,
                formatBaRemainingTime(cafeFullAt, uiNowMs),
            )
        }

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentPink,
        accentAlpha = 0f,
    ) {
        BaCardHeader(
            title = stringResource(R.string.ba_cafe_title),
            titleIconRes = R.drawable.mp_cafe_small,
            trailing = {
                StatusPill(
                    label = cafeFullText,
                    color = countdownBlue,
                    size = AppStatusPillSize.Compact,
                    backdrop = backdrop,
                )
                StatusPill(
                    label = cafeHourlyText,
                    color = accentPink,
                    size = AppStatusPillSize.Compact,
                    backdrop = backdrop,
                )
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

        BaCafeApStockPanel(
            backdrop = backdrop,
            cafeLevel = cafeLevel,
            cafeStoredApInput = cafeStoredApInput,
            accentPink = accentPink,
            onCafeStoredApInputChange = onCafeStoredApInputChange,
            onCafeStoredApDone = onCafeStoredApDone,
            onOpenCafeApTools = onOpenCafeApTools,
            onClaimCafeStoredAp = onClaimCafeStoredAp,
        )

        BaCompactDualMetricPanel(
            backdrop = backdrop,
            firstLabel = stringResource(R.string.ba_cafe_metric_tactical_challenge),
            firstValue = nextArenaRefreshText,
            secondLabel = stringResource(R.string.ba_cafe_metric_student_visit),
            secondValue = nextStudentRefreshText,
            accentColor = accentPink,
            valueColor = countdownBlue,
        )
    }
}

@Composable
private fun BaCafeApStockPanel(
    backdrop: Backdrop?,
    cafeLevel: Int,
    cafeStoredApInput: String,
    accentPink: Color,
    onCafeStoredApInputChange: (String) -> Unit,
    onCafeStoredApDone: () -> Unit,
    onOpenCafeApTools: () -> Unit,
    onClaimCafeStoredAp: () -> Unit,
) {
    val cafeCap = cafeDailyCapacity(cafeLevel)

    BaLiquidPanel(
        backdrop = backdrop,
        flattenOverUniformParent = true,
        accentColor = accentPink,
        onLongClick = onOpenCafeApTools,
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
                        text = stringResource(R.string.ba_overview_cafe_ap_title),
                        color = accentPink,
                        fontWeight = FontWeight.Bold,
                    )
                    Image(
                        painter = painterResource(id = R.drawable.item_icon_consumable_ap_3_small),
                        contentDescription = stringResource(R.string.ba_overview_cd_claim_cafe_ap),
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
                    value = cafeStoredApInput,
                    onValueChange = onCafeStoredApInputChange,
                    onImeActionDone = onCafeStoredApDone,
                    label = "0",
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    textColor = accentPink,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                )
                Text("/", color = MiuixTheme.colorScheme.onBackgroundVariant)
                BaLimitValueText(
                    text = cafeCap.toString(),
                    color = accentPink,
                )
                AppLiquidIconButton(
                    backdrop = backdrop,
                    painter = painterResource(id = R.drawable.item_icon_consumable_ap_3_small),
                    contentDescription = stringResource(R.string.ba_overview_cd_claim_cafe_ap),
                    variant = GlassVariant.Content,
                    iconTint = Color.Unspecified,
                    containerColor = accentPink,
                    onClick = onClaimCafeStoredAp,
                )
            }
        }
    }
}
