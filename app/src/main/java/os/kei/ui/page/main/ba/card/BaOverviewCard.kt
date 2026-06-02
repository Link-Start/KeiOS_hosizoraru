@file:Suppress("FunctionName")

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
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BA_DEFAULT_NICKNAME
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
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
    serverOptions: List<String>,
    serverIndex: Int,
    showOverviewServerPopup: Boolean,
    overviewServerPopupAnchorBounds: IntRect?,
    onOverviewServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOverviewServerPopupChange: (Boolean) -> Unit,
    onServerSelected: (Int) -> Unit,
    onOpenGuideCatalog: () -> Unit,
) {
    val isWorkActivated = idFriendCode != BA_DEFAULT_FRIEND_CODE
    val accentBlue = AppStatusColors.Cached
    val stateAccent =
        if (isWorkActivated) accentBlue else MiuixTheme.colorScheme.onBackgroundVariant
    val nicknameLengthForWidth =
        idNicknameInput.ifEmpty { BA_DEFAULT_NICKNAME }.length.coerceIn(1, 10)
    val nicknameFieldWidth = (nicknameLengthForWidth * 10 + 24).coerceIn(68, 108).dp
    val friendCodeLengthForWidth =
        idFriendCodeInput.ifEmpty { BA_DEFAULT_FRIEND_CODE }.length.coerceIn(1, 8)
    val friendCodeFieldWidth = (friendCodeLengthForWidth * 10 + 28).coerceIn(86, 116).dp
    val nicknameSuffixWidth = 44.dp
    val idTrailingSlotWidth =
        maxOf(
            nicknameFieldWidth + 4.dp + nicknameSuffixWidth,
            friendCodeFieldWidth,
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
                labelIconRes = R.drawable.collectible_icon_guidemission_s3_small,
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
                        modifier =
                            Modifier
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
                        painter = painterResource(id = R.drawable.common_icon_dailyreward_small),
                        contentDescription = stringResource(R.string.ba_overview_cd_open_catalog),
                        variant = GlassVariant.Content,
                        onClick = onOpenGuideCatalog,
                        width = 48.dp,
                        height = 34.dp,
                        iconTint = Color.Unspecified,
                        iconModifier =
                            Modifier
                                .width(26.dp)
                                .height(26.dp),
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
                                painter = painterResource(id = R.drawable.lobby_icon_work_small),
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
                        variant = GlassVariant.Content,
                    )
                }
            }
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
                    modifier =
                        Modifier
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
