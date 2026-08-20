@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba.card

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaLiquidMetricPanel
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.BaOfficeAccountCardUiState
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.sanitizeBaAccountFriendCode
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private const val AccountPageLiquidActivationDistance = 0.99f

@Composable
internal fun BaAccountPagerCard(
    backdrop: Backdrop?,
    accounts: List<BaOfficeAccountCardUiState>,
    activeAccountId: BaAccountId?,
    serverOptions: List<String>,
    onAccountSelected: (BaAccountId) -> Unit,
    onEditAccount: (BaAccountId) -> Unit,
) {
    if (accounts.isEmpty()) {
        BaAccountLoadingCard(backdrop = backdrop)
        return
    }

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
        pageSpacing = 24.dp,
    ) { page ->
        val account = accounts[page]
        val pageOffset =
            abs(
                (pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction,
            )
        BaAccountPageCard(
            backdrop = backdrop,
            account = account,
            page = page,
            pageCount = accounts.size,
            serverOptions = serverOptions,
            effectsEnabled = pageOffset < AccountPageLiquidActivationDistance,
            onEditAccount = { onEditAccount(account.id) },
        )
    }
}

@Composable
private fun BaAccountLoadingCard(backdrop: Backdrop?) {
    val accentColor = AppStatusColors.Cached
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
            BaCardHeader(title = stringResource(R.string.ba_office_name_jp))
            Text(
                text = stringResource(R.string.common_loading),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.End,
            )
        }

        BaLiquidPanel(
            backdrop = backdrop,
            flattenOverUniformParent = true,
            accentColor = accentColor,
            accentAlpha = 0f,
        ) {
            Text(
                text = stringResource(R.string.common_loading),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BaAccountPageCard(
    backdrop: Backdrop?,
    account: BaOfficeAccountCardUiState,
    page: Int,
    pageCount: Int,
    serverOptions: List<String>,
    effectsEnabled: Boolean,
    onEditAccount: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = AppStatusColors.Cached
    val serverName = serverOptions.getOrElse(account.serverIndex) { serverOptions.lastOrNull().orEmpty() }
    val friendCode = sanitizeBaAccountFriendCode(account.friendCode, account.serverIndex)
    val currentOnEditAccount by rememberUpdatedState(onEditAccount)
    val officeTitle =
        when (account.serverIndex.coerceIn(0, 2)) {
            0 -> stringResource(R.string.ba_office_name_cn)
            1 -> stringResource(R.string.ba_office_name_global)
            else -> stringResource(R.string.ba_office_name_jp)
        }
    val teacherName =
        stringResource(
            R.string.ba_account_card_teacher_name,
            account.nickname,
            stringResource(R.string.ba_id_nickname_suffix),
        )
    val clipboardLabel = stringResource(R.string.ba_friend_code_clipboard_label)
    val copiedToastRes = R.string.ba_toast_friend_code_copied
    val editActionLabel = stringResource(R.string.ba_account_management_edit_action)
    val headerInteractionSource = remember { MutableInteractionSource() }
    fun copyFriendCode() {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                clipboardLabel,
                friendCode,
            ),
        )
        context.showToast(copiedToastRes)
    }

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentColor,
        accentAlpha = 0f,
        effectsEnabled = effectsEnabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        verticalSpacing = 6.dp,
    ) {
        BaAccountOfficeHeader(
            modifier =
                Modifier.combinedClickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                    onClickLabel = editActionLabel,
                    role = Role.Button,
                    onLongClickLabel = editActionLabel,
                    onLongClick = { currentOnEditAccount() },
                    onClick = { currentOnEditAccount() },
                ),
            title = officeTitle,
            displayName = account.displayName,
            serverName = serverName,
            enabled = account.enabled,
            pageLabel =
                if (pageCount > 1) {
                    stringResource(R.string.ba_account_card_count, page + 1, pageCount)
                } else {
                    null
                },
            accentColor = accentColor,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BaLiquidMetricPanel(
                backdrop = backdrop,
                flattenOverUniformParent = true,
                label = stringResource(R.string.ba_id_label_nickname),
                value = teacherName,
                accentColor = accentColor,
                valueColor = accentColor,
                valueMaxLines = 1,
                labelFontSize = AppTypographyTokens.Supporting.fontSize,
                labelLineHeight = AppTypographyTokens.Supporting.lineHeight,
                valueFontSize = AppTypographyTokens.CardHeader.fontSize,
                valueLineHeight = AppTypographyTokens.CardHeader.lineHeight,
                valueFontWeight = AppTypographyTokens.CardHeader.fontWeight,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                effectsEnabled = effectsEnabled,
                modifier = Modifier.weight(1f),
            )
            BaLiquidMetricPanel(
                backdrop = backdrop,
                flattenOverUniformParent = true,
                label = stringResource(R.string.ba_id_label_friend_code),
                value = friendCode,
                accentColor = accentColor,
                valueColor = accentColor,
                valueMaxLines = 1,
                valueFontFamily = FontFamily.Monospace,
                labelFontSize = AppTypographyTokens.Supporting.fontSize,
                labelLineHeight = AppTypographyTokens.Supporting.lineHeight,
                valueFontSize = AppTypographyTokens.CardHeader.fontSize,
                valueLineHeight = AppTypographyTokens.CardHeader.lineHeight,
                valueFontWeight = AppTypographyTokens.CardHeader.fontWeight,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                effectsEnabled = effectsEnabled,
                modifier = Modifier.weight(1f),
                onClick = ::copyFriendCode,
            )
        }
    }
}

@Composable
internal fun BaAccountOfficeHeader(
    modifier: Modifier = Modifier,
    title: String,
    displayName: String,
    serverName: String,
    enabled: Boolean,
    pageLabel: String?,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(2f, fill = false),
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.SectionTitle.fontSize,
                lineHeight = AppTypographyTokens.SectionTitle.lineHeight,
                fontWeight = AppTypographyTokens.SectionTitle.fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = displayName,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BaAccountServerBadge(
                text = serverName,
                accentColor = accentColor,
            )
            if (!enabled) {
                BaAccountDisabledBadge()
            }
        }
        pageLabel?.let { label ->
            BaAccountCountChip(
                text = label,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
internal fun BaAccountServerBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    BaAccountHeaderStatusPill(
        text = text,
        accentColor = accentColor,
        modifier = modifier,
    )
}

@Composable
internal fun BaAccountCountChip(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    BaAccountHeaderStatusPill(
        text = text,
        accentColor = accentColor,
        modifier = modifier.widthIn(min = 42.dp),
    )
}

@Composable
internal fun BaAccountDisabledBadge(modifier: Modifier = Modifier) {
    BaAccountHeaderStatusPill(
        text = stringResource(R.string.ba_account_disabled_badge),
        accentColor = AppStatusColors.Failed,
        modifier = modifier,
    )
}

@Composable
internal fun BaAccountHeaderStatusPill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    StatusPill(
        label = text,
        color = accentColor,
        modifier = modifier,
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
        backgroundAlphaOverride = 0.12f,
        borderAlphaOverride = 0f,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        typographyOverride =
            TextStyle(
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
            ),
        contentColorOverride = accentColor,
    )
}
