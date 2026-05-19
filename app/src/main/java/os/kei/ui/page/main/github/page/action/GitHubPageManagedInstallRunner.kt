package os.kei.ui.page.main.github.page.action

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import os.kei.core.concurrency.AppDispatchers
import os.kei.R
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallFailureReason
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.install.GitHubManagedApkInstaller
import os.kei.feature.github.install.GitHubShizukuPackageInstaller
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.InstalledAppItem
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.page.githubManagedInstallKey

private const val GITHUB_PAGE_MANAGED_INSTALL_TAG = "GitHubPageInstall"

internal class GitHubPageManagedInstallRunner(
    private val env: GitHubPageActionEnvironment,
    private val apkInfoRepository: GitHubApkInfoRepository,
    private val managedApkInstaller: GitHubManagedApkInstaller = GitHubShizukuPackageInstaller()
) {
    suspend fun install(item: GitHubTrackedApp, asset: GitHubReleaseAssetFile): Boolean {
        val appContext = env.context.applicationContext
        val installKey = item.githubManagedInstallKey(asset)
        if (env.state.managedInstallLoading[installKey] == true) return true
        env.state.managedInstallLoading[installKey] = true
        env.toast(R.string.github_toast_page_install_started, assetDisplayName(asset.name))
        return try {
            try {
                val request = buildRequest(appContext, item, asset)
                val result = managedApkInstaller.install(appContext, request) { progress ->
                    notifyProgress(appContext, request, progress)
                }
                applyResult(appContext, item, asset, request, result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val reason = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(
                        R.string.github_share_import_error_app_managed_install_failed
                    )
                AppLogger.w(
                    GITHUB_PAGE_MANAGED_INSTALL_TAG,
                    "GitHub page managed install crashed: $reason",
                    error
                )
                GitHubShareImportNotificationHelper.notifyPageInstallFailed(
                    context = appContext,
                    reason = reason,
                    owner = item.owner,
                    repo = item.repo,
                    packageName = item.packageName,
                    targetDisplayName = item.appLabel.ifBlank { item.repo }
                )
                env.toast(R.string.github_toast_page_install_failed, reason)
                false
            }
        } finally {
            env.state.managedInstallLoading.remove(installKey)
        }
    }

    private suspend fun buildRequest(
        context: Context,
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile
    ): GitHubApkInstallRequest = coroutineScope {
        val lookupConfig = env.state.lookupConfig
        val targetDisplayName = item.appLabel
            .ifBlank { item.packageName }
            .ifBlank { item.repo }
            .ifBlank { assetDisplayName(asset.name) }
        GitHubShareImportNotificationHelper.notifyInstalling(
            context = context,
            owner = item.owner,
            repo = item.repo,
            releaseTag = PAGE_INSTALL_RELEASE_TAG,
            assetName = asset.name,
            progressPercent = 4,
            packageName = item.packageName,
            targetDisplayName = targetDisplayName
        )
        val manifestDeferred = async(AppDispatchers.githubNetwork) {
            apkInfoRepository.inspect(
                asset = asset,
                lookupConfig = lookupConfig
            ).getOrNull()
        }
        val urlDeferred = async(AppDispatchers.githubNetwork) {
            resolvePreferredAssetUrl(asset)
        }
        val manifestInfo = manifestDeferred.await()
        val resolvedDownloadUrl = urlDeferred.await()
        GitHubApkInstallRequest(
            owner = item.owner,
            repo = item.repo,
            releaseTag = PAGE_INSTALL_RELEASE_TAG,
            projectUrl = item.repoUrl.ifBlank { item.projectUrl() },
            asset = asset,
            lookupConfig = lookupConfig,
            targetDisplayName = targetDisplayName,
            scannedAppLabel = manifestInfo?.appLabel.orEmpty(),
            scannedPackageName = manifestInfo?.packageName.orEmpty().ifBlank { item.packageName },
            scannedVersionName = manifestInfo?.versionName.orEmpty(),
            scannedVersionCode = manifestInfo?.versionCode.orEmpty(),
            scannedMinSdk = manifestInfo?.minSdk.orEmpty(),
            scannedTargetSdk = manifestInfo?.targetSdk.orEmpty(),
            scannedNativeAbis = manifestInfo?.nativeAbis.orEmpty(),
            resolvedDownloadUrl = resolvedDownloadUrl,
            requestId = GitHubApkInstallRequestIds.newId(context.packageName)
        )
    }

    private suspend fun resolvePreferredAssetUrl(asset: GitHubReleaseAssetFile): String {
        val token = env.state.lookupConfig.apiToken.trim()
        val preferApiAsset =
            env.state.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken
        return env.repository.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = preferApiAsset,
            apiToken = token
        )
    }

    private fun notifyProgress(
        context: Context,
        request: GitHubApkInstallRequest,
        progress: GitHubApkInstallProgress
    ) {
        val appLabel = progress.appLabel.trim()
        val packageName = progress.packageName.trim().ifBlank { request.scannedPackageName }
        val versionName = progress.versionName.trim().ifBlank { request.scannedVersionName }
        when (progress.stage) {
            GitHubApkInstallStage.Preparing,
            GitHubApkInstallStage.Staging -> {
                GitHubShareImportNotificationHelper.notifyInstalling(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    appLabel = appLabel,
                    packageName = packageName,
                    versionName = versionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Downloading -> {
                GitHubShareImportNotificationHelper.notifyInstallDownloading(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    progressPercent = progress.boundedProgressPercent,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    appLabel = appLabel,
                    packageName = packageName,
                    versionName = versionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.ReadyToCommit,
            GitHubApkInstallStage.Committing -> {
                GitHubShareImportNotificationHelper.notifyInstallCommitting(
                    context = context,
                    owner = request.owner,
                    repo = request.repo,
                    releaseTag = request.releaseTag,
                    assetName = request.asset.name,
                    appLabel = appLabel,
                    packageName = packageName,
                    versionName = versionName,
                    targetDisplayName = request.targetDisplayName
                )
            }

            GitHubApkInstallStage.Succeeded,
            GitHubApkInstallStage.Failed,
            GitHubApkInstallStage.Cancelled -> Unit
        }
    }

    private suspend fun applyResult(
        context: Context,
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult
    ): Boolean {
        return when (result) {
            is GitHubApkInstallResult.Succeeded -> {
                val packageName = result.packageName.ifBlank { request.scannedPackageName }
                val installedInfo = loadInstalledPackageInfo(context, packageName)
                val appLabel = installedInfo?.appLabel
                    .orEmpty()
                    .ifBlank { result.appLabel }
                    .ifBlank { request.scannedAppLabel }
                    .ifBlank { request.targetDisplayName }
                env.state.apkInfoInstalledResults[asset.githubApkInfoKey()] = installedInfo
                if (installedInfo != null &&
                    packageName.equals(item.packageName.trim(), ignoreCase = true)
                ) {
                    applyInstalledPackageToTrackedState(item, installedInfo, appLabel)
                }
                GitHubShareImportNotificationHelper.notifyPageInstallCompleted(
                    context = context,
                    owner = item.owner,
                    repo = item.repo,
                    releaseTag = request.releaseTag,
                    assetName = asset.name,
                    appLabel = appLabel,
                    packageName = packageName,
                    versionName = installedInfo?.versionName.orEmpty()
                        .ifBlank { request.scannedVersionName },
                    targetDisplayName = request.targetDisplayName
                )
                env.toast(
                    R.string.github_toast_page_install_completed,
                    appLabel.ifBlank { packageName }
                )
                true
            }

            is GitHubApkInstallResult.Cancelled -> {
                GitHubShareImportNotificationHelper.notifyCancelled(context)
                env.toast(R.string.github_share_import_notify_content_cancelled)
                false
            }

            is GitHubApkInstallResult.Failed -> {
                val reason = managedInstallFailureMessage(context, result)
                AppLogger.w(
                    GITHUB_PAGE_MANAGED_INSTALL_TAG,
                    "GitHub page managed install failed: ${result.reason}, $reason"
                )
                GitHubShareImportNotificationHelper.notifyPageInstallFailed(
                    context = context,
                    reason = reason,
                    owner = item.owner,
                    repo = item.repo,
                    packageName = result.packageName.ifBlank { request.scannedPackageName },
                    targetDisplayName = request.targetDisplayName
                )
                env.toast(R.string.github_toast_page_install_failed, reason)
                false
            }

            is GitHubApkInstallResult.Staged -> false
        }
    }

    private fun applyInstalledPackageToTrackedState(
        item: GitHubTrackedApp,
        installedInfo: GitHubInstalledPackageInfo,
        appLabel: String
    ) {
        val packageName = installedInfo.packageName.trim()
        if (packageName.isBlank()) return
        val previous = env.state.checkStates[item.id]
        if (previous != null) {
            env.state.checkStates[item.id] = previous.copy(
                loading = false,
                localVersion = installedInfo.versionName,
                localVersionCode = installedInfo.versionCode,
                message = previous.message.takeIf { it.isNotBlank() }
                    ?: env.string(R.string.github_status_up_to_date)
            )
        }
        val installedItem = InstalledAppItem(
            label = appLabel.ifBlank { installedInfo.appLabel }.ifBlank { packageName },
            packageName = packageName
        )
        env.state.appList = env.state.appList
            .filterNot { it.packageName.equals(packageName, ignoreCase = true) } + installedItem
        env.state.appListLoaded = true
        env.state.requestTrackCardFocus(item.id)
    }

    private fun loadInstalledPackageInfo(
        context: Context,
        packageName: String
    ): GitHubInstalledPackageInfo? {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return null
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(
                normalizedPackageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        }.getOrNull() ?: return null
        val applicationInfo = packageInfo.applicationInfo
        return GitHubInstalledPackageInfo(
            packageName = normalizedPackageName,
            appLabel = applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
            versionName = packageInfo.versionName?.trim().orEmpty(),
            versionCode = packageInfo.longVersionCode,
            minSdk = applicationInfo?.minSdkVersion ?: -1,
            targetSdk = applicationInfo?.targetSdkVersion ?: -1,
            apkSizeBytes = applicationInfo.installedApkSizeBytes()
        )
    }

    private fun managedInstallFailureMessage(
        context: Context,
        result: GitHubApkInstallResult.Failed
    ): String {
        return when (result.reason) {
            GitHubApkInstallFailureReason.ShizukuUnavailable ->
                context.getString(R.string.github_share_import_error_shizuku_unavailable)

            GitHubApkInstallFailureReason.ShizukuPermissionMissing ->
                context.getString(R.string.github_share_import_error_shizuku_permission_missing)

            GitHubApkInstallFailureReason.RemoteInstallPermissionMissing ->
                context.getString(R.string.github_share_import_error_shizuku_install_permission_missing)

            GitHubApkInstallFailureReason.DownloadUrlInvalid ->
                context.getString(R.string.github_toast_open_downloader_failed)

            GitHubApkInstallFailureReason.PackageNameMissing ->
                context.getString(R.string.github_share_import_error_app_managed_package_missing)

            else -> result.message.ifBlank {
                context.getString(R.string.github_share_import_error_app_managed_install_failed)
            }
        }
    }

    private fun GitHubTrackedApp.projectUrl(): String {
        return SafeExternalIntents.httpsExternalUrlOrNull("https://github.com/$owner/$repo")
            ?: "https://github.com/$owner/$repo"
    }

    private companion object {
        const val PAGE_INSTALL_RELEASE_TAG = "latest"
    }
}
