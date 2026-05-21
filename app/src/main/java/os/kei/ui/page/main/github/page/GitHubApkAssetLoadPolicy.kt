package os.kei.ui.page.main.github.page

import android.content.Context
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.isLocalAppUninstalled

internal fun canLoadApkAssets(
    item: GitHubTrackedApp,
    itemState: VersionCheckUi,
    context: Context,
): Boolean =
    item.isGitHubRepositoryTrack() &&
        (
            item.alwaysShowLatestReleaseDownloadButton ||
                itemState.hasUpdate == true ||
                itemState.recommendsPreRelease ||
                itemState.hasPreReleaseUpdate ||
                (
                    itemState.isLocalAppUninstalled() &&
                        itemState.apkAssetTarget(
                            owner = item.owner,
                            repo = item.repo,
                            context = context,
                            allowLatestReleaseFallback = true,
                        ) != null
                )
        )
