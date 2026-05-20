package os.kei.ui.page.main.github.page

import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.githubProfileSourceSignature
import os.kei.feature.github.model.requiredCapabilities
import os.kei.ui.page.main.github.VersionCheckUi

internal fun shouldAutoRefreshRepositoryHealthDetail(
    itemState: VersionCheckUi?,
    lookupConfig: GitHubLookupConfig,
    refreshing: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (refreshing || itemState?.loading == true) return false
    val profile = itemState?.repositoryProfile ?: return true
    val requiredCapabilities =
        GitHubRepositoryProfilePurpose.DetailFull.requiredCapabilities(
            lookupConfig.profileDepth,
        )
    val sourceSignature = lookupConfig.githubProfileSourceSignature(requiredCapabilities)
    return !profile.isFreshFor(
        activeSourceConfigSignature = sourceSignature,
        nowMillis = nowMillis,
        requiredCapabilities = requiredCapabilities,
    )
}
