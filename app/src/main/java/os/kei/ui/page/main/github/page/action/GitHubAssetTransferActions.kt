package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.asset.GitHubAssetHandoff

/** The GitHub page's way into [GitHubAssetHandoff], saying the result through the page's own toasts. */
internal class GitHubAssetTransferActions(
    private val env: GitHubPageActionEnvironment,
) {
    private val context get() = env.context
    private val state get() = env.state

    suspend fun shareApkLink(asset: GitHubReleaseAssetFile): Boolean =
        GitHubAssetHandoff
            .shareAsset(context, state.lookupConfig, asset)
            .report { messageRes -> env.toast(messageRes) }

    suspend fun openApkInDownloader(asset: GitHubReleaseAssetFile): Boolean =
        GitHubAssetHandoff
            .downloadAsset(context, state.lookupConfig, asset)
            .report { messageRes -> env.toast(messageRes) }
}
