package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
import os.kei.feature.github.install.GitHubPageManagedInstallConfirmRegistry
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.github.page.githubManagedInstallKey

internal class GitHubManagedInstallConfirmActions(
    private val env: GitHubPageActionEnvironment,
    private val managedInstallRunner: GitHubPageManagedInstallRunner,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private var registrationToken: Long? = null

    fun dispose() {
        clearRegistration()
    }

    fun installOrFallback(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        onOpenConfirm: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
        onFallbackDownload: suspend (GitHubReleaseAssetFile) -> Unit,
    ) {
        scope.launch {
            if (shouldInstallWithKeiOs(asset)) {
                onOpenConfirm(item, asset)
            } else {
                onFallbackDownload(asset)
            }
        }
    }

    fun openConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        manifestInfo: GitHubApkManifestInfo?,
        onLoadApkInfo: (GitHubReleaseAssetFile) -> Unit,
    ) {
        state.apkInfoDetailRequest = null
        state.managedInstallConfirmRequest =
            GitHubManagedInstallConfirmRequest(
                item = item,
                asset = asset,
            )
        registerConfirmAction()
        notifyConfirm(item, asset, manifestInfo)
        onLoadApkInfo(asset)
    }

    fun confirmManagedInstall() {
        val request =
            consumeConfirmRequest() ?: run {
                env.toast(R.string.github_page_install_confirm_expired)
                return
            }
        launchManagedInstall(request)
    }

    fun dismissManagedInstallConfirm() {
        val request = state.managedInstallConfirmRequest
        state.managedInstallConfirmRequest = null
        clearRegistration()
        if (request != null &&
            state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] != true
        ) {
            GitHubShareImportNotificationHelper.cancel(context)
        }
    }

    fun notifyConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        info: GitHubApkManifestInfo?,
    ) {
        GitHubShareImportNotificationHelper.notifyPageInstallConfirm(
            context = context,
            owner = item.owner,
            repo = item.repo,
            releaseTag = "latest",
            assetName = asset.name,
            appLabel = info?.appLabel.orEmpty().ifBlank { item.appLabel },
            packageName = info?.packageName.orEmpty().ifBlank { item.packageName },
            versionName = info?.versionName.orEmpty(),
            targetDisplayName = item.appLabel.ifBlank { assetDisplayName(asset.name) },
            confirmActionEnabled = info != null || asset.isGitHubActionsApkArtifactArchive(),
        )
    }

    private fun shouldInstallWithKeiOs(asset: GitHubReleaseAssetFile): Boolean =
        state.lookupConfig.appManagedShareInstallEnabled &&
            asset.name.endsWith(".apk", ignoreCase = true)

    private fun consumeConfirmRequest(): GitHubManagedInstallConfirmRequest? {
        val request = state.managedInstallConfirmRequest ?: return null
        state.managedInstallConfirmRequest = null
        clearRegistration()
        return request
    }

    private fun launchManagedInstall(request: GitHubManagedInstallConfirmRequest) {
        scope.launch {
            managedInstallRunner.install(request.item, request.asset)
        }
    }

    private fun registerConfirmAction() {
        clearRegistration()
        registrationToken =
            GitHubPageManagedInstallConfirmRegistry.register {
                withContext(Dispatchers.Main.immediate) {
                    if (!scope.coroutineContext.isActive) return@withContext false
                    val request =
                        consumeConfirmRequest()
                            ?: return@withContext false
                    launchManagedInstall(request)
                    true
                }
            }
    }

    private fun clearRegistration() {
        registrationToken?.let { token ->
            GitHubPageManagedInstallConfirmRegistry.clear(token)
        }
        registrationToken = null
    }
}
