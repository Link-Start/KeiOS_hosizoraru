package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.remote.GitHubShareImportAssetPlan
import os.kei.feature.github.model.GitHubLookupConfig

internal object GitHubShareImportPreviewBuilder {
    fun build(
        plan: GitHubShareImportAssetPlan,
        lookupConfig: GitHubLookupConfig,
    ): GitHubShareImportPreview =
        GitHubShareImportPreview(
            sourceUrl = plan.parsedLink.sourceUrl,
            projectUrl = plan.parsedLink.projectUrl,
            owner = plan.parsedLink.owner,
            repo = plan.parsedLink.repo,
            releaseTag = plan.resolvedReleaseTag,
            releaseUrl = plan.resolvedReleaseUrl,
            strategyLabel = lookupConfig.selectedStrategy.label,
            assets = plan.assets,
            preferredAssetName = plan.preferredAssetName,
            targetDisplayName =
                buildShareImportTargetDisplayName(
                    repo = plan.parsedLink.repo,
                    assetName =
                        plan.preferredAssetName.ifBlank {
                            plan.assets
                                .singleOrNull()
                                ?.name
                                .orEmpty()
                        },
                ),
        )
}
