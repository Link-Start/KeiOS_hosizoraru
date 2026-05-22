package os.kei.ui.page.main.github.page.action

import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.page.githubApkInfoKey

internal class GitHubApkInfoActions(
    private val env: GitHubPageActionEnvironment,
    private val apkInfoRepository: GitHubApkInfoRepository,
    private val managedInstallConfirmActions: GitHubManagedInstallConfirmActions,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state

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
                state.apkInfoInstalledResults[key] =
                    loadInstalledPackageInfo(cachedInfo.packageName)
            }
            state.managedInstallConfirmRequest
                ?.takeIf { it.asset.githubApkInfoKey() == key }
                ?.let { request ->
                    managedInstallConfirmActions.notifyConfirm(request.item, asset, cachedInfo)
                }
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
                        lookupConfig = state.lookupConfig,
                        forceRefresh = forceRefresh,
                    )
                }
            state.apkInfoLoading[key] = false
            result
                .onSuccess { info ->
                    state.apkInfoResults[key] = info
                    state.apkInfoInstalledResults[key] = loadInstalledPackageInfo(info.packageName)
                    state.managedInstallConfirmRequest
                        ?.takeIf { it.asset.githubApkInfoKey() == key }
                        ?.let { request ->
                            managedInstallConfirmActions.notifyConfirm(request.item, asset, info)
                        }
                }.onFailure { error ->
                    state.apkInfoErrors[key] = error.message
                        ?: context.getString(R.string.github_apk_info_error_failed)
                }
        }
    }

    private fun loadInstalledPackageInfo(packageName: String): GitHubInstalledPackageInfo? {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return null
        val packageInfo =
            runCatching {
                context.packageManager.getPackageInfo(
                    normalizedPackageName,
                    PackageManager.PackageInfoFlags.of(0),
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
            apkSizeBytes = applicationInfo.installedApkSizeBytes(),
        )
    }
}
