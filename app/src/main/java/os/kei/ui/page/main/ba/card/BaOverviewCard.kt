package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidMetricPanel
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BA_DEFAULT_NICKNAME
import os.kei.ui.page.main.ba.support.cafeDailyCapacity
import os.kei.ui.page.main.ba.support.calculateApFullAtMs
import os.kei.ui.page.main.ba.support.calculateApNextPointAtMs
import os.kei.ui.page.main.ba.support.displayAp
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaOverviewCard(
    backdrop: Backdrop?,
    overviewTitle: String,
    idFriendCode: String,
    idNicknameInput: String,
    onIdNicknameInputChange: (String) -> Unit,
    onSaveIdNickname: () -> Unit,
    idFriendCodeInput: String,
    onIdFriendCodeInputChange: (String) -> Unit,
    onSaveIdFriendCode: () -> Unit,
    uiNowMs: Long,
    uiMinuteMs: Long,
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
    cafeStoredAp: Double,
    cafeLevel: Int,
    serverOptions: List<String>,
    serverIndex: Int,
    showOverviewServerPopup: Boolean,
    overviewServerPopupAnchorBounds: IntRect?,
    onOverviewServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOverviewServerPopupChange: (Boolean) -> Unit,
    onServerSelected: (Int) -> Unit,
    onClaimCafeStoredAp: () -> Unit,
    onOpenGuideCatalog: () -> Unit,
) {
    val isWorkActivated = idFriendCode != BA_DEFAULT_FRIEND_CODE
    val apNextPointAt = calculateApNextPointAtMs(
        apLimit = apLimit,
        apCurrent = apCurrent,
        apRegenBaseMs = apRegenBaseMs,
        nowMs = uiNowMs,
    )
    val apFullAt = calculateApFullAtMs(
        apLimit = apLimit,
        apCurrent = apCurrent,
        apRegenBaseMs = apRegenBaseMs,
        nowMs = uiMinuteMs,
    )
    val apNextPointRemain = formatBaRemainingTime(apNextPointAt, uiNowMs, includeSeconds = true)
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val apSyncTimeText =
        if (apSyncMs > 0L) formatBaDateTimeNoSeconds(apSyncMs, notSyncedText) else notSyncedText
    val apFullText = formatBaRemainingTime(apFullAt, uiMinuteMs)
    val apFullTimeText = formatBaDateTimeNoSeconds(apFullAt, notSyncedText)
    val accentBlue = AppStatusColors.Cached
    val accentGreen = AppStatusColors.Fresh
    val stateAccent =
        if (isWorkActivated) accentBlue else MiuixTheme.colorScheme.onBackgroundVariant
    val nicknameLengthForWidth =
        idNicknameInput.ifEmpty { BA_DEFAULT_NICKNAME }.length.coerceIn(1, 10)
    val nicknameFieldWidth = (nicknameLengthForWidth * 10 + 24).coerceIn(68, 108).dp
    val friendCodeLengthForWidth =
        idFriendCodeInput.ifEmpty { BA_DEFAULT_FRIEND_CODE }.length.coerceIn(1, 8)
    val friendCodeFieldWidth = (friendCodeLengthForWidth * 10 + 28).coerceIn(86, 116).dp
    val nicknameSuffixWidth = 44.dp
    val idTrailingSlotWidth = maxOf(
        nicknameFieldWidth + 4.dp + nicknameSuffixWidth,
        friendCodeFieldWidth
    )

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = stateAccent,
        accentAlpha = 0f,
    ) {
        BaCardHeader(title = overviewTitle)

        BaLiquidPanel(
            backdrop = backdrop,
            accentColor = accentBlue,
            accentAlpha = 0f,
        ) {
            BaOverviewIdFieldRow(
                label = stringResource(R.string.ba_id_label_nickname),
                trailingSlotWidth = idTrailingSlotWidth,
                labelIconRes = R.drawable.collectible_icon_guidemission_s3,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppLiquidSearchField(
                        modifier = Modifier.width(nicknameFieldWidth),
                        value = idNicknameInput,
                        onValueChange = onIdNicknameInputChange,
                        onImeActionDone = onSaveIdNickname,
                        label = BA_DEFAULT_NICKNAME,
                        backdrop = backdrop,
                        variant = GlassVariant.SheetInput,
                        singleLine = true,
                        textAlign = TextAlign.Center,
                        textColor = accentBlue,
                        minHeight = 34.dp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 5.dp,
                    )
                    Box(
                        modifier = Modifier
                            .width(nicknameSuffixWidth)
                            .height(34.dp)
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .offset(y = (-1).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.ba_id_nickname_suffix),
                            color = accentBlue,
                            fontWeight = FontWeight.Medium,
                            fontSize = AppTypographyTokens.Body.fontSize,
                            lineHeight = AppTypographyTokens.Body.fontSize,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            BaOverviewIdFieldRow(
                label = stringResource(R.string.ba_id_label_friend_code),
                trailingSlotWidth = idTrailingSlotWidth,
            ) {
                AppLiquidSearchField(
                    modifier = Modifier.width(friendCodeFieldWidth),
                    value = idFriendCodeInput,
                    onValueChange = onIdFriendCodeInputChange,
                    onImeActionDone = onSaveIdFriendCode,
                    label = BA_DEFAULT_FRIEND_CODE,
                    backdrop = backdrop,
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    textColor = accentBlue,
                    minHeight = 34.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 5.dp,
                )
            }
        }

        BaLiquidPanel(
            backdrop = backdrop,
            accentColor = stateAccent,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        Text(stringResource(R.string.ba_overview_catalog_title), color = MiuixTheme.colorScheme.onBackground)
                    }
                    AppLiquidIconButton(
                        backdrop = backdrop,
                        painter = painterResource(id = R.drawable.common_icon_dailyreward),
                        contentDescription = stringResource(R.string.ba_overview_cd_open_catalog),
                        variant = GlassVariant.Content,
                        onClick = onOpenGuideCatalog,
                        width = 48.dp,
                        height = 34.dp,
                        iconTint = Color.Unspecified,
                        iconModifier = Modifier
                            .width(26.dp)
                            .height(26.dp)
                    )
                }

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
                            Text(stringResource(R.string.ba_overview_server_label), color = MiuixTheme.colorScheme.onBackground)
                            Image(
                                painter = painterResource(id = R.drawable.lobby_icon_work),
                                contentDescription = stringResource(R.string.ba_overview_cd_server_icon),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    AppDropdownSelector(
                        selectedText = serverOptions[serverIndex],
                        options = serverOptions,
                        selectedIndex = serverIndex,
                        expanded = showOverviewServerPopup,
                        anchorBounds = overviewServerPopupAnchorBounds,
                        onExpandedChange = onOverviewServerPopupChange,
                        onSelectedIndexChange = onServerSelected,
                        onAnchorBoundsChange = onOverviewServerPopupAnchorBoundsChange,
                        backdrop = backdrop,
                        variant = GlassVariant.Content
                    )
                }
            }
        }

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
                        Text("AP", color = accentGreen, fontWeight = FontWeight.Bold)
                        Image(
                            painter = painterResource(id = R.drawable.ba_ap_icon_tight),
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
                    Text(stringResource(R.string.ba_overview_cafe_ap_title), color = MiuixTheme.colorScheme.onBackground)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppLiquidIconButton(
                        backdrop = backdrop,
                        painter = painterResource(id = R.drawable.item_icon_consumable_ap_3),
                        contentDescription = stringResource(R.string.ba_overview_cd_claim_cafe_ap),
                        variant = GlassVariant.Content,
                        iconTint = Color.Unspecified,
                        containerColor = accentGreen,
                        onClick = onClaimCafeStoredAp,
                    )
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text = "${displayAp(cafeStoredAp)}/${cafeDailyCapacity(cafeLevel)}",
                        textColor = accentGreen,
                        containerColor = accentGreen,
                        variant = GlassVariant.Floating,
                        onClick = {},
                    )
                }
            }
        }

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
            BaLiquidMetricPanel(
                backdrop = backdrop,
                label = stringResource(R.string.ba_metric_ap_full),
                value = apFullTimeText,
                accentColor = Color(0xFF60A5FA),
                valueColor = Color(0xFF60A5FA),
                valueMaxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BaOverviewIdFieldRow(
    label: String,
    trailingSlotWidth: Dp,
    labelIconRes: Int? = null,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(64.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (labelIconRes != null) {
                Image(
                    painter = painterResource(id = labelIconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .width(30.dp)
                        .height(24.dp),
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier.width(trailingSlotWidth),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent,
            )
        }
    }
}
