package os.kei.ui.page.main.github.install

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.R
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.isPotentialNestedApkArchive
import os.kei.feature.github.install.GitHubApkInstallFailureReason
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallRequestIds
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.install.GitHubManagedApkInstaller
import os.kei.feature.github.install.GitHubModeRoutedApkInstaller
import os.kei.feature.github.install.managedInstallDownloadSpeedProfile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.feature.github.notification.GitHubPageManagedInstallCancelRegistry
import os.kei.ui.page.main.github.asset.GitHubAssetHandoff
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.localizedGitHubPageErrorMessage
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.page.githubManagedInstallKey
import java.util.concurrent.atomic.AtomicInteger

private const val GITHUB_PAGE_MANAGED_INSTALL_TAG = "GitHubPageInstall"

/**
 * One confirmed install, from inspecting the file to the notification that says how it ended.
 *
 * Nothing here knows which page confirmed it. What differs between the tracked card and a history page —
 * what a finished install changes on screen — goes through [GitHubApkInstallHost.onInstalled].
 */
internal class GitHubManagedInstallRunner(
    private val apkInfoRepository: GitHubApkInfoRepository,
    private val managedApkInstaller: GitHubManagedApkInstaller = GitHubModeRoutedApkInstaller()
) {
    suspend fun install(
        context: Context,
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        state: GitHubApkInstallState,
        host: GitHubApkInstallHost
    ): Boolean {
        val appContext = context.applicationContext
        val installKey = item.githubManagedInstallKey(asset)
        if (state.managedInstallLoading[installKey] == true) return true
        val installJob = checkNotNull(currentCoroutineContext()[Job]) {
            "GitHub page install requires a coroutine Job"
        }
        val activeSessionId = AtomicInteger(-1)
        val cancellationToken = GitHubPageManagedInstallCancelRegistry.register {
            installJob.cancel(CancellationException("GitHub page install cancelled"))
            activeSessionId.get().takeIf { it > 0 }?.let { sessionId ->
                managedApkInstaller.cancel(appContext, sessionId)
            }
        }
        state.managedInstallLoading[installKey] = true
        host.toast(appContext.getString(R.string.github_toast_page_install_started, assetDisplayName(asset.name)))
        return try {
            try {
                val request = buildRequest(appContext, item, asset, lookupConfig)
                val result = managedApkInstaller.install(appContext, request) { progress ->
                    if (progress.sessionId > 0) {
                        activeSessionId.set(progress.sessionId)
                    }
                    notifyProgress(appContext, request, progress)
                }
                applyResult(appContext, item, asset, request, result, state, host)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val reason =
                    localizedGitHubPageErrorMessage(
                        context = appContext,
                        error = error,
                        fallbackMessage =
                            appContext.getString(
                                R.string.github_share_import_error_app_managed_install_failed
                            ),
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
                host.toast(appContext.getString(R.string.github_toast_page_install_failed, reason))
                false
            }
        } finally {
            GitHubPageManagedInstallCancelRegistry.clear(cancellationToken)
            state.managedInstallLoading.remove(installKey)
        }
    }

    private suspend fun buildRequest(
        context: Context,
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): GitHubApkInstallRequest = coroutineScope {
        val targetDisplayName = item.appLabel
            .ifBlank { item.packageName }
            .ifBlank { item.repo }
            .ifBlank { assetDisplayName(asset.name) }
        GitHubShareImportNotificationHelper.notifyPageInstallPreparing(
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
            GitHubAssetHandoff.assetUrl(lookupConfig, asset)
        }
        val manifestInfo = manifestDeferred.await()
        if (asset.isPotentialNestedApkArchive()) {
            val expectedPackage = item.packageName.trim()
            val inspectedPackage = manifestInfo?.packageName.orEmpty().trim()
            check(expectedPackage.isNotBlank()) {
                "Tracked package is required for nested APK installation"
            }
            check(inspectedPackage.equals(expectedPackage, ignoreCase = true)) {
                "Nested APK package $inspectedPackage does not match $expectedPackage"
            }
        }
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
            downloadSpeedProfile = lookupConfig.managedInstallDownloadSpeedProfile(),
            requestId = GitHubApkInstallRequestIds.newId(context.packageName)
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
                GitHubShareImportNotificationHelper.notifyPageInstallPreparing(
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
                GitHubShareImportNotificationHelper.notifyPageInstallDownloading(
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
                GitHubShareImportNotificationHelper.notifyPageInstallCommitting(
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

    private fun applyResult(
        context: Context,
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequest,
        result: GitHubApkInstallResult,
        state: GitHubApkInstallState,
        host: GitHubApkInstallHost
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
                state.apkInfoInstalledResults[asset.githubApkInfoKey()] = installedInfo
                if (installedInfo != null &&
                    packageName.equals(item.packageName.trim(), ignoreCase = true)
                ) {
                    host.onInstalled(item, installedInfo, appLabel)
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
                host.toast(
                    context.getString(
                        R.string.github_toast_page_install_completed,
                        appLabel.ifBlank { packageName }
                    )
                )
                true
            }

            is GitHubApkInstallResult.Cancelled -> {
                GitHubShareImportNotificationHelper.notifyPageInstallCancelled(context)
                host.toast(context.getString(R.string.github_page_install_notify_content_cancelled))
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
                host.toast(context.getString(R.string.github_toast_page_install_failed, reason))
                false
            }

            is GitHubApkInstallResult.Staged -> false
        }
    }

    private fun managedInstallFailureMessage(
        context: Context,
        result: GitHubApkInstallResult.Failed
    ): String {
        return when (result.reason) {
            GitHubApkInstallFailureReason.PrivilegeModeDisabled ->
                context.getString(R.string.github_share_import_error_privilege_mode_disabled)

            GitHubApkInstallFailureReason.ShizukuUnavailable ->
                context.getString(R.string.github_share_import_error_shizuku_unavailable)

            GitHubApkInstallFailureReason.RootUnavailable ->
                context.getString(R.string.github_share_import_error_root_unavailable)

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
            }.let { message ->
                localizedGitHubPageErrorMessage(
                    context = context,
                    rawMessage = message,
                    fallbackMessage = context.getString(R.string.github_share_import_error_app_managed_install_failed),
                )
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
