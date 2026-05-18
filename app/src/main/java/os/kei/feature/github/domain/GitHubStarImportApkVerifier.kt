package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import kotlin.coroutines.cancellation.CancellationException

internal interface GitHubStarImportApkVerificationCache {
    fun load(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        refreshIntervalHours: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubStarImportApkVerification?

    fun save(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        verification: GitHubStarImportApkVerification
    )
}

internal class GitHubStarImportApkVerifier(
    private val source: GitHubApkPackageNameScanSource,
    private val cache: GitHubStarImportApkVerificationCache? = null,
    private val packageNameScanner: GitHubApkPackageNameScanner =
        GitHubApkPackageNameScanner(source),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun verify(
        candidate: GitHubRepositoryImportCandidate,
        lookupConfig: GitHubLookupConfig,
        refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubStarImportApkVerification {
        val repository = candidate.repository
        val owner = repository.owner.trim()
        val repo = repository.repo.trim()
        withContext(ioDispatcher) {
            cache?.load(
                owner = owner,
                repo = repo,
                lookupConfig = lookupConfig,
                refreshIntervalHours = refreshIntervalHours,
                nowMillis = nowMillis
            )
        }?.let { return it.copy(fromCache = true) }

        val releaseAssetsResult = withContext(ioDispatcher) {
            source.loadLatestStableApkAssets(
                owner = owner,
                repo = repo,
                lookupConfig = lookupConfig
            )
        }
        releaseAssetsResult.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        val verification = releaseAssetsResult.getOrNull()?.let { releaseAssets ->
            val apkAssets = releaseAssets.assets.filter { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            }
            if (apkAssets.isNotEmpty()) {
                val scannedPackage = scanFirstPackageNameAsync(
                    assets = apkAssets,
                    lookupConfig = lookupConfig
                )
                GitHubStarImportApkVerification(
                    owner = owner,
                    repo = repo,
                    status = GitHubStarImportApkVerificationStatus.HasApk,
                    releaseTag = releaseAssets.release.tag,
                    releaseUrl = releaseAssets.release.releaseUrl,
                    apkAssetCount = apkAssets.size,
                    sampleAssetName = scannedPackage?.first?.name ?: apkAssets.first().name,
                    packageName = scannedPackage?.second.orEmpty(),
                    checkedAtMillis = nowMillis
                )
            } else {
                GitHubStarImportApkVerification(
                    owner = owner,
                    repo = repo,
                    status = GitHubStarImportApkVerificationStatus.NoApk,
                    releaseTag = releaseAssets.release.tag,
                    releaseUrl = releaseAssets.release.releaseUrl,
                    checkedAtMillis = nowMillis
                )
            }
        } ?: releaseAssetsResult.exceptionOrNull().let { error ->
            GitHubStarImportApkVerification(
                owner = owner,
                repo = repo,
                status = GitHubStarImportApkVerificationStatus.Failed,
                checkedAtMillis = nowMillis,
                errorMessage = error?.message.orEmpty().ifBlank {
                    error?.javaClass?.simpleName ?: "Unknown"
                }
            )
        }
        withContext(ioDispatcher) {
            cache?.save(
                owner = owner,
                repo = repo,
                lookupConfig = lookupConfig,
                verification = verification
            )
        }
        return verification
    }

    private suspend fun scanFirstPackageNameAsync(
        assets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig
    ): Pair<GitHubReleaseAssetFile, String>? {
        assets.take(MAX_PACKAGE_SCAN_ASSETS).forEach { asset ->
            val packageName = packageNameScanner.scanAssetPackageName(
                asset = asset,
                lookupConfig = lookupConfig
            ).getOrDefault("").trim()
            if (packageName.isNotBlank()) return asset to packageName
        }
        return null
    }

    companion object {
        private const val DEFAULT_REFRESH_INTERVAL_HOURS = 6
        private const val MAX_PACKAGE_SCAN_ASSETS = 4
    }
}
