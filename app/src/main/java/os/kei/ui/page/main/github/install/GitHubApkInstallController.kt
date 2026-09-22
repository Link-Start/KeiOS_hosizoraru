package os.kei.ui.page.main.github.install

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
import os.kei.feature.github.data.remote.isVerifiedManagedInstallAsset
import os.kei.feature.github.install.GitHubPageManagedInstallConfirmRegistry
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.localizedGitHubPageErrorMessage
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.page.githubManagedInstallKey

/** What a surface brings to the shared flow: how it speaks, and what a finished install changes on it. */
internal interface GitHubApkInstallHost {
    fun toast(message: String)

    /** Where 📦 goes when KeiOS will not install the file itself: wherever ⬇ goes. */
    suspend fun downloadInstead(asset: GitHubReleaseAssetFile)

    /**
     * A managed install of [item]'s own package finished. The tracked card moves its local version, a
     * history page moves its "Installed" mark.
     */
    fun onInstalled(
        item: GitHubTrackedApp,
        installedInfo: GitHubInstalledPackageInfo,
        appLabel: String,
    )
}

/**
 * Where a managed install runs: somewhere that outlives the page it was confirmed on.
 *
 * An install carries its own notification with a cancel action and can take a minute on a slow
 * connection, and the page is free to close in that time — the release list in particular, which a
 * reader leaves once they have picked the build to roll back to. Main, because an install writes the
 * snapshot state the rows and sheets read.
 */
internal object GitHubManagedInstallScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Main.immediate
}

/**
 * ⓘ and 📦 on an asset row, for every surface that shows one: the tracked card, the release history and
 * the F-Droid version history.
 *
 * ⓘ inspects the file's manifest and opens the APK info sheet. 📦 opens the install confirm sheet when
 * KeiOS installs the file itself, and hands the file to the downloader otherwise, the same rule on every
 * surface. The two history pages used to wire both buttons to a browser because this lived inside the
 * GitHub page's action environment, so an old build could only be inspected or installed by going back
 * to the tracked card, which offers the newest one.
 *
 * A surface owns its [state] and supplies a [host]; the confirm, the install and its notifications are
 * this class's, and an install runs in [installScope] rather than in [scope], so it is not cancelled by
 * the page closing.
 */
internal class GitHubApkInstallController(
    private val context: Context,
    private val scope: CoroutineScope,
    val state: GitHubApkInstallState,
    private val lookupConfig: () -> GitHubLookupConfig,
    private val host: GitHubApkInstallHost,
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository(),
    private val runner: GitHubManagedInstallRunner = GitHubManagedInstallRunner(apkInfoRepository),
    private val installScope: CoroutineScope = GitHubManagedInstallScope,
) {
    private var registrationToken: Long? = null

    init {
        // [state] can outlive this controller: after a configuration change the page is composed again
        // around the same state, with the confirm sheet still open. Its notification's confirm button has
        // to keep answering through that, so the new controller takes the registration over.
        if (state.managedInstallConfirmRequest != null) registerConfirmAction()
    }

    /** Whether 📦 installs [asset] through KeiOS here, rather than handing it to the downloader. */
    fun managedInstallAvailable(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ): Boolean =
        lookupConfig().appManagedShareInstallEnabled &&
            asset.isVerifiedManagedInstallAsset(
                expectedPackageName = item.packageName,
                inspectedPackageName = state.apkInfoResults[asset.githubApkInfoKey()]?.packageName.orEmpty(),
            )

    fun managedInstallRunning(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ): Boolean = state.managedInstallLoading[item.githubManagedInstallKey(asset)] == true

    fun openApkInfo(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean = false,
    ) {
        state.apkInfoDetailRequest = GitHubApkInfoDetailRequest(item = item, asset = asset)
        loadApkInfo(asset = asset, forceRefresh = forceRefresh)
    }

    fun dismissApkInfo() {
        state.apkInfoDetailRequest = null
    }

    fun loadApkInfo(
        asset: GitHubReleaseAssetFile,
        forceRefresh: Boolean,
    ) {
        val key = asset.githubApkInfoKey()
        if (forceRefresh) {
            state.apkInfoResults.remove(key)
            state.apkInfoInstalledResults.remove(key)
            state.apkInfoErrors.remove(key)
        }
        state.apkInfoResults[key]?.let { cachedInfo ->
            if (!state.apkInfoInstalledResults.containsKey(key)) {
                state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(context, cachedInfo.packageName)
            }
            notifyConfirmIfOpenFor(asset, cachedInfo)
            return
        }
        if (state.apkInfoLoading[key] == true) return
        state.apkInfoLoading[key] = true
        state.apkInfoErrors.remove(key)
        scope.launch {
            val result =
                withContext(AppDispatchers.githubNetwork) {
                    apkInfoRepository.inspect(
                        asset = asset,
                        lookupConfig = lookupConfig(),
                        forceRefresh = forceRefresh,
                    )
                }
            state.apkInfoLoading[key] = false
            result
                .onSuccess { info ->
                    state.apkInfoResults[key] = info
                    state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(context, info.packageName)
                    notifyConfirmIfOpenFor(asset, info)
                }.onFailure { error ->
                    state.apkInfoErrors[key] =
                        localizedGitHubPageErrorMessage(
                            context = context,
                            error = error,
                            fallbackMessage = context.getString(R.string.github_apk_info_error_failed),
                        )
                }
        }
    }

    fun installOrFallback(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        scope.launch {
            if (managedInstallAvailable(item, asset)) {
                openManagedInstallConfirm(item, asset)
            } else {
                host.downloadInstead(asset)
            }
        }
    }

    fun openManagedInstallConfirm(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) {
        state.apkInfoDetailRequest = null
        state.managedInstallConfirmRequest =
            GitHubManagedInstallConfirmRequest(
                item = item,
                asset = asset,
            )
        registerConfirmAction()
        notifyConfirm(item, asset, state.apkInfoResults[asset.githubApkInfoKey()])
        loadApkInfo(asset = asset, forceRefresh = false)
    }

    fun confirmManagedInstall() {
        val request =
            consumeConfirmRequest() ?: run {
                host.toast(context.getString(R.string.github_page_install_confirm_expired))
                return
            }
        launchManagedInstall(request)
    }

    fun dismissManagedInstallConfirm() {
        val request = state.managedInstallConfirmRequest
        state.managedInstallConfirmRequest = null
        clearRegistration()
        if (request != null && !managedInstallRunning(request.item, request.asset)) {
            GitHubShareImportNotificationHelper.cancel(context)
        }
    }

    fun dispose() {
        clearRegistration()
    }

    private fun notifyConfirmIfOpenFor(
        asset: GitHubReleaseAssetFile,
        info: GitHubApkManifestInfo,
    ) {
        state.managedInstallConfirmRequest
            ?.takeIf { it.asset.githubApkInfoKey() == asset.githubApkInfoKey() }
            ?.let { request -> notifyConfirm(request.item, asset, info) }
    }

    private fun notifyConfirm(
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

    private fun consumeConfirmRequest(): GitHubManagedInstallConfirmRequest? {
        val request = state.managedInstallConfirmRequest ?: return null
        state.managedInstallConfirmRequest = null
        clearRegistration()
        return request
    }

    private fun launchManagedInstall(request: GitHubManagedInstallConfirmRequest) {
        val config = lookupConfig()
        installScope.launch {
            runner.install(
                context = context,
                item = request.item,
                asset = request.asset,
                lookupConfig = config,
                state = state,
                host = host,
            )
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
