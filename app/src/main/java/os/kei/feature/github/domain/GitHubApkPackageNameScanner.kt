package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import kotlin.coroutines.cancellation.CancellationException

internal data class GitHubStableReleaseTarget(
    val tag: String,
    val releaseUrl: String
)

internal data class GitHubStableReleaseApkAssets(
    val release: GitHubStableReleaseTarget,
    val assets: List<GitHubReleaseAssetFile>
)

internal interface GitHubApkPackageNameScanSource {
    suspend fun loadLatestStableRelease(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseTarget>

    suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubStableReleaseTarget,
        lookupConfig: GitHubLookupConfig
    ): Result<List<GitHubReleaseAssetFile>>

    suspend fun loadLatestStableApkAssets(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseApkAssets> = runCatching {
        val release = loadLatestStableRelease(
            owner = owner,
            repo = repo,
            lookupConfig = lookupConfig
        ).getOrThrow()
        val assets = fetchApkAssets(
            owner = owner,
            repo = repo,
            release = release,
            lookupConfig = lookupConfig
        ).getOrThrow()
        GitHubStableReleaseApkAssets(
            release = release,
            assets = assets
        )
    }

    suspend fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray>
}

internal class GitHubApkPackageNameScanner(
    private val source: GitHubApkPackageNameScanSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun scan(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> = runCatching {
        val parsed = GitHubVersionUtils.parseOwnerRepo(request.repoUrl)
            ?: error("Invalid GitHub repository URL")
        val owner = parsed.first
        val repo = parsed.second
        val releaseAssets = withContext(ioDispatcher) {
            source.loadLatestStableApkAssets(
                owner = owner,
                repo = repo,
                lookupConfig = request.lookupConfig
            )
        }.getOrThrow()
        val release = releaseAssets.release
        val assets = releaseAssets.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val scannedAsset = scanApkAssetsAsync(
            assets = assets,
            lookupConfig = request.lookupConfig,
            expectedPackageName = request.expectedPackageName
        )
            ?: error("The latest stable release contains no APK asset")
        GitHubApkPackageNameScanResult(
            owner = owner,
            repo = repo,
            releaseTag = release.tag,
            releaseUrl = release.releaseUrl,
            assetName = scannedAsset.asset.name,
            packageName = scannedAsset.packageName
        )
    }.preserveCancellation()

    suspend fun scanAssetPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return scanAssetManifestInfo(
            asset = asset,
            lookupConfig = lookupConfig
        ).map { info ->
            info.packageName
        }
    }

    suspend fun scanAssetManifestInfo(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return scanApkAssetAsync(
            asset = asset,
            lookupConfig = lookupConfig
        ).map { scannedAsset ->
            scannedAsset.manifestInfo
        }
    }

    private suspend fun scanApkAssetsAsync(
        assets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig,
        expectedPackageName: String = ""
    ): ScannedApkAsset? {
        val scanTargets = assets.take(MAX_APK_ASSET_SCAN_CANDIDATES)
        if (scanTargets.isEmpty()) return null
        if (scanTargets.size == 1) {
            return scanApkAssetAsync(
                asset = scanTargets.single(),
                lookupConfig = lookupConfig
            ).getOrThrow()
        }
        val normalizedExpectedPackageName = expectedPackageName.trim()
        if (normalizedExpectedPackageName.isNotEmpty()) {
            return scanApkAssetsForExpectedPackageAsync(
                assets = scanTargets,
                lookupConfig = lookupConfig,
                expectedPackageName = normalizedExpectedPackageName
            )
        }

        return GitHubExecution.firstSuccessBounded(
            items = scanTargets,
            maxConcurrency = MAX_PARALLEL_APK_ASSET_SCANS,
            dispatcher = ioDispatcher
        ) { asset ->
            scanApkAssetAsync(
                asset = asset,
                lookupConfig = lookupConfig
            )
        }.getOrElse { error ->
            throw error
        }
    }

    private suspend fun scanApkAssetsForExpectedPackageAsync(
        assets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig,
        expectedPackageName: String
    ): ScannedApkAsset {
        var firstError: Throwable? = null
        val results = GitHubExecution.mapOrderedBounded(
            items = assets,
            maxConcurrency = MAX_PARALLEL_APK_ASSET_SCANS,
            dispatcher = ioDispatcher
        ) { asset ->
            scanApkAssetAsync(
                asset = asset,
                lookupConfig = lookupConfig
            )
        }
        val scannedAssets = results.mapNotNull { result ->
            result.fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (firstError == null) firstError = error
                    null
                }
            )
        }
        scannedAssets.firstOrNull { scanned ->
            scanned.packageName.equals(expectedPackageName, ignoreCase = true)
        }?.let { matched ->
            return matched
        }
        scannedAssets.firstOrNull()?.let { fallback ->
            return fallback
        }
        throw firstError ?: IllegalStateException("No APK asset manifest could be parsed")
    }

    private suspend fun scanApkAssetAsync(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ScannedApkAsset> = runCatching {
        val manifestBytes = withContext(ioDispatcher) {
            source.readAndroidManifestBytes(
                asset = asset,
                lookupConfig = lookupConfig
            )
        }.getOrThrow()
        val manifestInfo = AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifestBytes)
            .getOrThrow()
            .copy(assetName = asset.name)
        val packageName = manifestInfo.packageName
        check(GitHubPackageNameValidator.isValid(packageName)) {
            "Scanned package name is invalid: $packageName"
        }
        ScannedApkAsset(
            asset = asset,
            manifestInfo = manifestInfo
        )
    }.preserveCancellation()

    private fun <T> Result<T>.preserveCancellation(): Result<T> {
        exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        return this
    }

    private data class ScannedApkAsset(
        val asset: GitHubReleaseAssetFile,
        val manifestInfo: GitHubApkManifestInfo
    ) {
        val packageName: String
            get() = manifestInfo.packageName
    }

    companion object {
        private const val MAX_APK_ASSET_SCAN_CANDIDATES = 12
        private const val MAX_PARALLEL_APK_ASSET_SCANS = 4
    }
}

internal object GitHubPackageNameValidator {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$""")

    fun isValid(packageName: String): Boolean {
        return packageNamePattern.matches(packageName.trim())
    }
}

internal val GitHubLookupConfig.scanPreferHtmlAssets: Boolean
    get() = selectedStrategy == GitHubLookupStrategyOption.AtomFeed
