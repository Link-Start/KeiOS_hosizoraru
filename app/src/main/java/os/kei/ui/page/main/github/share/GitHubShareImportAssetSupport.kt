package os.kei.ui.page.main.github.share

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.ui.page.main.github.query.systemDownloadManagerOption

internal sealed interface ShareImportDeliveryResult {
    data class Success(
        val toastResId: Int,
    ) : ShareImportDeliveryResult

    data class Failure(
        val toastResId: Int,
    ) : ShareImportDeliveryResult
}

internal suspend fun sendAssetToConfiguredChannel(
    context: Context,
    lookupConfig: GitHubLookupConfig,
    asset: GitHubReleaseAssetFile,
    newTask: Boolean = false,
): ShareImportDeliveryResult {
    val resolvedUrl =
        SafeExternalIntents.httpsExternalUrlOrNull(
            resolvePreferredAssetUrl(lookupConfig, asset),
        ) ?: return ShareImportDeliveryResult.Failure(R.string.github_toast_open_downloader_failed)
    val onlineSharePackage = lookupConfig.onlineShareTargetPackage.trim()
    if (onlineSharePackage.isNotBlank()) {
        val intent =
            SafeExternalIntents
                .textShareIntent(
                    text = resolvedUrl,
                    subject = asset.name,
                    targetPackage = onlineSharePackage,
                    extras =
                        mapOf(
                            "channel" to "Online",
                            "extra_channel" to "Online",
                            "online_channel" to true,
                        ),
                ).apply {
                    if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        return runCatching {
            context.startActivity(intent)
            ShareImportDeliveryResult.Success(R.string.github_toast_downloader_selected)
        }.getOrElse {
            ShareImportDeliveryResult.Failure(R.string.github_toast_share_link_failed)
        }
    }

    val preferredPackage = lookupConfig.preferredDownloaderPackage.trim()
    val systemDmPackage = systemDownloadManagerOption(context).packageName
    if (preferredPackage == systemDmPackage) {
        return runCatching {
            enqueueWithSystemDownloadManager(context, resolvedUrl, asset.name)
            ShareImportDeliveryResult.Success(R.string.github_toast_downloader_system_builtin)
        }.getOrElse {
            ShareImportDeliveryResult.Failure(R.string.github_toast_open_downloader_failed)
        }
    }
    if (preferredPackage.isBlank()) {
        return if (SafeExternalIntents.startBrowsableUrl(context, resolvedUrl, newTask = newTask)) {
            ShareImportDeliveryResult.Success(R.string.github_toast_downloader_system_default)
        } else {
            ShareImportDeliveryResult.Failure(R.string.github_toast_open_downloader_failed)
        }
    }

    return runCatching {
        require(
            SafeExternalIntents.startBrowsableUrl(
                context = context,
                url = resolvedUrl,
                targetPackage = preferredPackage,
                newTask = newTask,
            ),
        )
        ShareImportDeliveryResult.Success(R.string.github_toast_downloader_selected)
    }.recoverCatching {
        require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl, newTask = newTask))
        ShareImportDeliveryResult.Success(R.string.github_toast_downloader_fallback_system)
    }.getOrElse {
        ShareImportDeliveryResult.Failure(R.string.github_toast_open_downloader_failed)
    }
}

internal suspend fun resolvePreferredAssetUrl(
    lookupConfig: GitHubLookupConfig,
    asset: GitHubReleaseAssetFile,
): String {
    val token = lookupConfig.apiToken.trim()
    val preferApiAsset = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken
    return withContext(AppDispatchers.githubNetwork) {
        GitHubReleaseAssetRepository
            .resolvePreferredDownloadUrl(
                asset = asset,
                useApiAssetUrl = preferApiAsset,
                apiToken = token,
            ).getOrElse { asset.downloadUrl }
    }
}

internal suspend fun scanShareImportAssetPackageName(
    asset: GitHubReleaseAssetFile,
    lookupConfig: GitHubLookupConfig,
    scanner: GitHubApkPackageNameScanner =
        GitHubApkPackageNameScanner(
            GitHubApkPackageNameScanRepository(),
        ),
): Result<String> =
    scanShareImportAssetManifestInfo(
        asset = asset,
        lookupConfig = lookupConfig,
        scanner = scanner,
    ).map { info ->
        info.packageName.trim()
    }

internal suspend fun scanShareImportAssetManifestInfo(
    asset: GitHubReleaseAssetFile,
    lookupConfig: GitHubLookupConfig,
    apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository(),
    scanner: GitHubApkPackageNameScanner =
        GitHubApkPackageNameScanner(
            GitHubApkPackageNameScanRepository(),
        ),
): Result<GitHubApkManifestInfo> {
    apkInfoRepository
        .inspect(
            asset = asset,
            lookupConfig = lookupConfig,
        ).getOrNull()
        ?.let { info ->
            return Result.success(info)
        }
    return scanner.scanAssetManifestInfo(
        asset = asset,
        lookupConfig = lookupConfig,
    )
}

internal fun enqueueWithSystemDownloadManager(
    context: Context,
    url: String,
    fileName: String,
) {
    AppPrivateDownloadManager.enqueueHttpsDownload(
        context = context,
        url = url,
        fileName = fileName,
    )
}
