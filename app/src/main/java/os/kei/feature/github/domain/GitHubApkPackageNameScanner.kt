package os.kei.feature.github.domain

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubApkPackageNameScanResult
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption

internal data class GitHubStableReleaseTarget(
    val tag: String,
    val releaseUrl: String
)

internal data class GitHubStableReleaseApkAssets(
    val release: GitHubStableReleaseTarget,
    val assets: List<GitHubReleaseAssetFile>
)

internal interface GitHubApkPackageNameScanSource {
    fun loadLatestStableRelease(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseTarget>

    fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubStableReleaseTarget,
        lookupConfig: GitHubLookupConfig
    ): Result<List<GitHubReleaseAssetFile>>

    fun loadLatestStableApkAssets(
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

    fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray>
}

internal class GitHubApkPackageNameScanner(
    private val source: GitHubApkPackageNameScanSource
) {
    fun scan(
        request: GitHubApkPackageNameScanRequest
    ): Result<GitHubApkPackageNameScanResult> = runCatching {
        val parsed = GitHubVersionUtils.parseOwnerRepo(request.repoUrl)
            ?: error("Invalid GitHub repository URL")
        val owner = parsed.first
        val repo = parsed.second
        val releaseAssets = source.loadLatestStableApkAssets(
            owner = owner,
            repo = repo,
            lookupConfig = request.lookupConfig
        ).getOrThrow()
        val release = releaseAssets.release
        val assets = releaseAssets.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val scannedAsset = scanApkAssets(
            assets = assets,
            lookupConfig = request.lookupConfig
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
    }

    fun scanAssetPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return scanApkAsset(
            asset = asset,
            lookupConfig = lookupConfig
        ).map { scannedAsset ->
            scannedAsset.packageName
        }
    }

    private fun scanApkAssets(
        assets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig
    ): ScannedApkAsset? {
        val scanTargets = assets.take(MAX_APK_ASSET_SCAN_CANDIDATES)
        if (scanTargets.isEmpty()) return null
        if (scanTargets.size == 1) {
            return scanApkAsset(
                asset = scanTargets.single(),
                lookupConfig = lookupConfig
            ).getOrThrow()
        }

        return GitHubExecution.firstSuccessBoundedBlocking(
            items = scanTargets,
            maxConcurrency = MAX_PARALLEL_APK_ASSET_SCANS
        ) { asset ->
            scanApkAsset(
                asset = asset,
                lookupConfig = lookupConfig
            )
        }.getOrElse { error ->
            throw error
        }
    }

    private fun scanApkAsset(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ScannedApkAsset> = runCatching {
        val manifestBytes = source.readAndroidManifestBytes(
            asset = asset,
            lookupConfig = lookupConfig
        ).getOrThrow()
        val packageName =
            AndroidBinaryXmlPackageNameParser.parsePackageName(manifestBytes).getOrThrow()
        check(GitHubPackageNameValidator.isValid(packageName)) {
            "Scanned package name is invalid: $packageName"
        }
        ScannedApkAsset(
            asset = asset,
            packageName = packageName
        )
    }

    private data class ScannedApkAsset(
        val asset: GitHubReleaseAssetFile,
        val packageName: String
    )

    companion object {
        private const val MAX_APK_ASSET_SCAN_CANDIDATES = 6
        private const val MAX_PARALLEL_APK_ASSET_SCANS = 3
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
