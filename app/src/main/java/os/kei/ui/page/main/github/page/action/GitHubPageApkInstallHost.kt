package os.kei.ui.page.main.github.page.action

import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.install.GitHubApkInstallHost

/**
 * The GitHub page's side of the shared install flow: its toasts, its downloader, and the tracked card a
 * finished install moves.
 *
 * An install started from a history page does not come through here, and does not need to: when this page
 * is active again, `syncLocalAppStateOnPageActive` reads the installed version code and refreshes any card
 * whose version moved.
 */
internal class GitHubPageApkInstallHost(
    private val env: GitHubPageActionEnvironment,
    private val transferActions: GitHubAssetTransferActions,
) : GitHubApkInstallHost {
    override fun toast(message: String) = env.toast(message)

    override suspend fun downloadInstead(asset: GitHubReleaseAssetFile) {
        transferActions.openApkInDownloader(asset)
    }

    override fun onInstalled(
        item: GitHubTrackedApp,
        installedInfo: GitHubInstalledPackageInfo,
        appLabel: String,
    ) {
        val packageName = installedInfo.packageName.trim()
        if (packageName.isBlank()) return
        val previous = env.state.checkStates[item.id]
        if (previous != null) {
            env.state.checkStates[item.id] =
                previous.copy(
                    loading = false,
                    localVersion = installedInfo.versionName,
                    localVersionCode = installedInfo.versionCode,
                    message =
                        previous.message.takeIf { it.isNotBlank() }
                            ?: env.string(R.string.github_status_up_to_date),
                )
        }
        val installedItem =
            InstalledAppItem(
                label = appLabel.ifBlank { installedInfo.appLabel }.ifBlank { packageName },
                packageName = packageName,
            )
        env.state.appList =
            env.state.appList
                .filterNot { it.packageName.equals(packageName, ignoreCase = true) } + installedItem
        env.state.appListLoaded = true
        env.state.requestTrackCardFocus(item.id)
    }
}
