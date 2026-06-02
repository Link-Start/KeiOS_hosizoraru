@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaAccountManagementSheet(
    show: Boolean,
    backdrop: Backdrop?,
    state: BaOfficeAccountUiState,
    onAllAccountsFollowGlobalNotificationSettingsChange: (Boolean) -> Unit,
    onAccountEnabledChange: (BaAccountId, Boolean) -> Unit,
    onSelectAccount: (BaAccountId) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val settingsAccent = Color(0xFF3B82F6)
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_account_management_title),
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
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(stringResource(R.string.ba_account_management_section_global))
            SheetSectionCard {
                SheetControlRow(
                    label = stringResource(R.string.ba_account_management_follow_global),
                    summary = stringResource(R.string.ba_account_management_follow_global_summary),
                ) {
                    AppSwitch(
                        checked = state.allAccountsFollowGlobalNotificationSettings,
                        onCheckedChange = onAllAccountsFollowGlobalNotificationSettingsChange,
                    )
                }
            }

            SheetSectionTitle(stringResource(R.string.ba_account_management_section_accounts))
            if (state.accounts.isEmpty()) {
                SheetSectionCard {
                    Text(
                        text = stringResource(R.string.ba_account_management_empty),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                state.accounts.forEach { account ->
                    BaAccountManagementAccountRow(
                        backdrop = backdrop,
                        account = account,
                        active = account.id == state.activeAccountId,
                        settingsAccent = settingsAccent,
                        onAccountEnabledChange = onAccountEnabledChange,
                        onSelectAccount = onSelectAccount,
                    )
                }
            }
        }
    }
}

@Composable
private fun BaAccountManagementAccountRow(
    backdrop: Backdrop?,
    account: BaOfficeAccountCardUiState,
    active: Boolean,
    settingsAccent: Color,
    onAccountEnabledChange: (BaAccountId, Boolean) -> Unit,
    onSelectAccount: (BaAccountId) -> Unit,
) {
    SheetSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = account.displayName,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            text = stringResource(R.string.ba_account_management_active_badge),
                            color = settingsAccent,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text =
                        stringResource(
                            R.string.ba_account_management_account_summary,
                            accountServerLabel(account.serverIndex),
                            account.nickname,
                            account.friendCode,
                        ),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 84.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSwitch(
                    checked = account.enabled,
                    onCheckedChange = { enabled -> onAccountEnabledChange(account.id, enabled) },
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text =
                        if (active) {
                            stringResource(R.string.ba_account_management_active_action)
                        } else {
                            stringResource(R.string.ba_account_management_use_action)
                        },
                    textColor = settingsAccent,
                    containerColor = settingsAccent,
                    enabled = !active,
                    variant = GlassVariant.SheetAction,
                    onClick = { onSelectAccount(account.id) },
                )
            }
        }
    }
}

@Composable
private fun accountServerLabel(serverIndex: Int): String =
    when (serverIndex.coerceIn(0, 2)) {
        0 -> stringResource(R.string.ba_server_cn)
        1 -> stringResource(R.string.ba_server_global)
        else -> stringResource(R.string.ba_server_jp)
    }
