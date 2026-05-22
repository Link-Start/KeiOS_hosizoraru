package os.kei.ui.page.main.github

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubStrategyBenchmarkResult

@Composable
internal fun GitHubApiAuthMode.localizedLabel(): String =
    when (this) {
        GitHubApiAuthMode.Guest -> stringResource(R.string.common_guest)
        GitHubApiAuthMode.Token -> stringResource(R.string.github_strategy_label_token)
    }

@Composable
internal fun GitHubApiCredentialStatus.localizedSummaryLabel(): String =
    when (authMode) {
        GitHubApiAuthMode.Guest -> stringResource(R.string.github_strategy_credential_guest_available)
        GitHubApiAuthMode.Token -> stringResource(R.string.github_strategy_credential_token_available)
    }

@Composable
internal fun GitHubStrategyBenchmarkResult.localizedSummaryLabel(): String {
    val authModeLabel = authMode?.localizedLabel()
    return if (authModeLabel == null) {
        displayName
    } else {
        stringResource(R.string.github_strategy_benchmark_title_with_auth, displayName, authModeLabel)
    }
}

internal fun GitHubLookupStrategyOption.accentColor(): Color =
    when (this) {
        GitHubLookupStrategyOption.AtomFeed -> GitHubStatusPalette.Active
        GitHubLookupStrategyOption.GitHubApiToken -> GitHubStatusPalette.Update
    }

internal fun GitHubActionsLookupStrategyOption.accentColor(): Color =
    when (this) {
        GitHubActionsLookupStrategyOption.NightlyLink -> GitHubStatusPalette.Active
        GitHubActionsLookupStrategyOption.GitHubApiToken -> GitHubStatusPalette.Update
    }
