@file:Suppress("FunctionName")

package os.kei.ui.page.main.github

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubCredentialStatusCard(status: GitHubApiCredentialStatus) {
    val context = LocalContext.current
    val accent =
        when (status.authMode) {
            GitHubApiAuthMode.Token -> GitHubStatusPalette.Update
            GitHubApiAuthMode.Guest -> GitHubStatusPalette.PreRelease
        }
    SheetSummaryCard(
        title = stringResource(R.string.github_strategy_card_title_credential_status),
        accentColor = accent,
        badgeLabel = status.localizedSummaryLabel(),
        badgeColor = accent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_mode),
            value = status.authMode.localizedLabel(),
            valueColor = accent,
            emphasized = true,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_quota),
            value = "${status.coreRemaining} / ${status.coreLimit}",
            valueColor = if (status.coreRemaining > 0) accent else GitHubStatusPalette.Error,
            emphasized = true,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_used),
            value = status.coreUsed.toString(),
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp,
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_reset),
            value = formatFutureEta(context, status.resetAtMillis),
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp,
        )
    }
}
