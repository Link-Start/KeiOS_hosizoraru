package os.kei.ui.page.main.github.install

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.asset.GitHubAssetHandoffActions
import os.kei.ui.page.main.github.asset.rememberGitHubAssetHandoffActions
import os.kei.ui.page.main.github.page.githubApkInfoKey

/**
 * The four actions of an asset row on a history page, for one track: ⓘ and 📦 through [install], ⬇ and
 * share through [handoff] — the same two implementations the tracked card's row reaches through the
 * GitHub page's action environment.
 *
 * [item] is null only while the page has not found its track, which is also when it has no rows.
 */
@Stable
internal class GitHubAssetRowActions(
    val item: GitHubTrackedApp?,
    val install: GitHubApkInstallController,
    val handoff: GitHubAssetHandoffActions,
) {
    fun openInfo(asset: GitHubReleaseAssetFile) {
        item?.let { install.openApkInfo(it, asset) }
    }

    fun installApk(asset: GitHubReleaseAssetFile) {
        item?.let { install.installOrFallback(it, asset) }
    }

    fun download(asset: GitHubReleaseAssetFile) = handoff.download(asset)

    fun share(asset: GitHubReleaseAssetFile) = handoff.share(asset)

    /** What ⓘ found in [asset], which is also what lets a zip's 📦 decide it holds this track's APK. */
    fun manifestInfo(asset: GitHubReleaseAssetFile): GitHubApkManifestInfo? =
        install.state.apkInfoResults[asset.githubApkInfoKey()]

    fun installRunning(asset: GitHubReleaseAssetFile): Boolean =
        item?.let { install.managedInstallRunning(it, asset) } == true
}

/** A history page's row actions, with the controller's state held by the page's view model. */
@Composable
internal fun rememberGitHubAssetRowActions(
    item: GitHubTrackedApp?,
    lookupConfig: GitHubLookupConfig,
    installState: GitHubApkInstallState,
    onInstalled: (GitHubInstalledPackageInfo) -> Unit,
): GitHubAssetRowActions {
    val handoff = rememberGitHubAssetHandoffActions(lookupConfig)
    val install =
        rememberGitHubApkInstallController(
            state = installState,
            lookupConfig = lookupConfig,
            assetHandoff = handoff,
            onInstalled = onInstalled,
        )
    return remember(item, install, handoff) {
        GitHubAssetRowActions(item = item, install = install, handoff = handoff)
    }
}
