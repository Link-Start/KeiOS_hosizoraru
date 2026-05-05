package os.kei.feature.github.domain

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
        val assets = releaseAssets.assets
        val asset = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: error("The latest stable release contains no APK asset")
        val manifestBytes = source.readAndroidManifestBytes(
            asset = asset,
            lookupConfig = request.lookupConfig
        ).getOrThrow()
        val packageName =
            AndroidBinaryXmlPackageNameParser.parsePackageName(manifestBytes).getOrThrow()
        check(GitHubPackageNameValidator.isValid(packageName)) {
            "Scanned package name is invalid: $packageName"
        }
        GitHubApkPackageNameScanResult(
            owner = owner,
            repo = repo,
            releaseTag = release.tag,
            releaseUrl = release.releaseUrl,
            assetName = asset.name,
            packageName = packageName
        )
    }

}

internal object GitHubPackageNameValidator {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    fun isValid(packageName: String): Boolean {
        return packageNamePattern.matches(packageName.trim())
    }
}

internal val GitHubLookupConfig.scanPreferHtmlAssets: Boolean
    get() = selectedStrategy == GitHubLookupStrategyOption.AtomFeed
