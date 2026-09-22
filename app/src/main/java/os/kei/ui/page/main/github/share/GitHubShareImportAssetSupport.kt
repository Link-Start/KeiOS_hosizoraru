package os.kei.ui.page.main.github.share

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.asset.GitHubAssetHandoff
import os.kei.ui.page.main.github.asset.GitHubAssetHandoffResult

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
): ShareImportDeliveryResult =
    when (
        val result =
            GitHubAssetHandoff.sendAssetToConfiguredChannel(
                context = context,
                lookupConfig = lookupConfig,
                asset = asset,
                newTask = newTask,
            )
    ) {
        // An installer opening is its own confirmation on a card, but here the result is also the line the
        // flow closes on, so a share still says where the file was handed.
        is GitHubAssetHandoffResult.Delivered ->
            ShareImportDeliveryResult.Success(result.messageRes ?: R.string.github_toast_downloader_selected)

        is GitHubAssetHandoffResult.Failed -> ShareImportDeliveryResult.Failure(result.messageRes)
    }

internal suspend fun resolvePreferredAssetUrl(
    lookupConfig: GitHubLookupConfig,
    asset: GitHubReleaseAssetFile,
): String = GitHubAssetHandoff.assetUrl(lookupConfig, asset)

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
