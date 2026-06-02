@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidMetricPanel
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.BaOfficeAccountCardUiState
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaAccountPagerCard(
    backdrop: Backdrop?,
    accounts: List<BaOfficeAccountCardUiState>,
    activeAccountId: BaAccountId?,
    serverOptions: List<String>,
    onAccountSelected: (BaAccountId) -> Unit,
) {
    if (accounts.isEmpty()) return

    val activeIndex = accounts.indexOfFirst { it.id == activeAccountId }.coerceAtLeast(0)
    val pagerState =
        rememberPagerState(
            initialPage = activeIndex.coerceIn(0, accounts.lastIndex),
            pageCount = { accounts.size },
        )
    val currentActiveAccountId by rememberUpdatedState(activeAccountId)
    val currentAccounts by rememberUpdatedState(accounts)

    LaunchedEffect(activeIndex, accounts.size, pagerState) {
        if (activeIndex in accounts.indices && pagerState.currentPage != activeIndex) {
            pagerState.animateScrollToPage(activeIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val account = currentAccounts.getOrNull(page) ?: return@collect
                if (account.id != currentActiveAccountId) {
                    onAccountSelected(account.id)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = accounts.size > 1,
        pageSpacing = 8.dp,
    ) { page ->
        val account = accounts[page]
        BaAccountPageCard(
            backdrop = backdrop,
            account = account,
            page = page,
            pageCount = accounts.size,
            serverOptions = serverOptions,
        )
    }
}

@Composable
private fun BaAccountPageCard(
    backdrop: Backdrop?,
    account: BaOfficeAccountCardUiState,
    page: Int,
    pageCount: Int,
    serverOptions: List<String>,
) {
    val accentColor = AppStatusColors.Cached
    val serverName = serverOptions.getOrElse(account.serverIndex) { serverOptions.lastOrNull().orEmpty() }
    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentColor,
        accentAlpha = 0f,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaCardHeader(title = stringResource(R.string.ba_account_card_title))
            Text(
                text = stringResource(R.string.ba_account_card_count, page + 1, pageCount),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.End,
            )
        }

        BaLiquidPanel(
            backdrop = backdrop,
            accentColor = accentColor,
            accentAlpha = 0f,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = account.displayName,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = serverName,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!account.enabled) {
                    Text(
                        text = stringResource(R.string.ba_account_disabled_badge),
                        color = AppStatusColors.Failed,
                        maxLines = 1,
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
                label = stringResource(R.string.ba_id_label_nickname),
                value = account.nickname,
                accentColor = accentColor,
                valueColor = accentColor,
                valueMaxLines = 1,
                modifier = Modifier.weight(1f),
            )
            BaLiquidMetricPanel(
                backdrop = backdrop,
                label = stringResource(R.string.ba_id_label_friend_code),
                value = account.friendCode,
                accentColor = accentColor,
                valueColor = accentColor,
                valueMaxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        if (pageCount > 1) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier =
                            Modifier
                                .size(if (index == page) 7.dp else 5.dp)
                                .appSquircleBackground(
                                    color =
                                        if (index == page) {
                                            accentColor
                                        } else {
                                            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.28f)
                                        },
                                    cornerRadius = 8.dp,
                                ),
                    )
                    if (index != pageCount - 1) {
                        Box(modifier = Modifier.size(5.dp))
                    }
                }
            }
        }
    }
}
