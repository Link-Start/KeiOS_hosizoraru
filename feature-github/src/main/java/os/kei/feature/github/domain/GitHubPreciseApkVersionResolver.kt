package os.kei.feature.github.domain

import os.kei.feature.github.GitHubExecution
import os.kei.core.io.cancellableResult
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.engine.apk.GitHubApkCandidateSelectionEngine
import os.kei.feature.github.engine.apk.GitHubInspectedApkCandidate
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.githubAssetSourceSignature

data class GitHubPreciseApkVersionRequest(
    val owner: String,
    val repo: String,
    val release: GitHubReleaseVersionSignals,
    val packageName: String,
    val lookupConfig: GitHubLookupConfig
)

interface GitHubPreciseApkVersionSource {
    suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle>

    suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo>
}

/** Positive package evidence, distinct from a timeout or an APK we could not read. */
internal class GitHubApkPackageMismatchException(expectedPackageName: String) :
    IllegalStateException("No APK manifest matched package $expectedPackageName")

internal data class GitHubResolvedApkRelease(
    val version: GitHubRemoteApkVersionInfo,
    val isPreRelease: Boolean?,
)

class GitHubPreciseApkVersionResolver(
    private val source: GitHubPreciseApkVersionSource = DefaultGitHubPreciseApkVersionSource()
) {
    suspend fun resolve(request: GitHubPreciseApkVersionRequest): Result<GitHubRemoteApkVersionInfo> =
        resolveRelease(request).map { it.version }

    internal suspend fun resolveRelease(request: GitHubPreciseApkVersionRequest): Result<GitHubResolvedApkRelease> =
        cancellableResult {
            val rawTag = request.release.rawTag.trim().ifBlank {
                GitHubReleaseAssetRepository.parseReleaseTagFromUrl(request.release.link)
            }
            check(rawTag.isNotBlank()) { "Release tag is required for precise APK version" }
            val releaseUrl = request.release.link.trim().ifBlank {
                GitHubVersionUtils.buildReleaseTagUrl(request.owner, request.repo, rawTag)
            }
            val bundle = source.loadReleaseAssetBundle(
                owner = request.owner,
                repo = request.repo,
                rawTag = rawTag,
                releaseUrl = releaseUrl,
                lookupConfig = request.lookupConfig
            ).getOrThrow()
            val allApkAssets = GitHubApkCandidateSelectionEngine.planInspection(
                assets = bundle.assets,
                expectedPackageName = request.packageName,
                maxCandidates = Int.MAX_VALUE,
            )
            val apkAssets = allApkAssets.take(GitHubApkCandidateSelectionEngine.DEFAULT_MAX_INSPECTION_CANDIDATES)
            check(apkAssets.isNotEmpty()) { "Release contains no APK asset" }

            val requestedPackageName = request.packageName.trim()
            val inspected = inspectApkAssetsUntilSelected(
                apkAssets = apkAssets,
                requestedPackageName = requestedPackageName,
                lookupConfig = request.lookupConfig
            )
            val selected = inspected.selected
            if (selected == null) {
                throw inspected.firstFailure ?: when {
                    inspected.packageMismatchVerified && apkAssets.size == allApkAssets.size ->
                        GitHubApkPackageMismatchException(requestedPackageName)
                    else -> IllegalStateException("No APK manifest could be inspected")
                }
            }
            val (asset, info) = selected

            GitHubResolvedApkRelease(
                version = GitHubRemoteApkVersionInfo(
                    releaseName = bundle.releaseName.ifBlank { request.release.rawName },
                    releaseTag = bundle.tagName.ifBlank { rawTag },
                    releaseUrl = bundle.htmlUrl.ifBlank { releaseUrl },
                    assetName = asset.name,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = info.versionCode,
                    fetchSource = info.fetchSource.ifBlank { bundle.fetchSource },
                ),
                isPreRelease = bundle.isPreRelease,
            )
        }

    private suspend fun inspectApkAssetsUntilSelected(
        apkAssets: List<GitHubReleaseAssetFile>,
        requestedPackageName: String,
        lookupConfig: GitHubLookupConfig
    ): ApkInspectSelection {
        val inspectedCandidates = mutableListOf<GitHubInspectedApkCandidate>()
        var firstFailure: Throwable? = null
        apkAssets.chunked(MAX_PARALLEL_APK_INSPECTS).forEach { chunk ->
            val inspected = GitHubExecution.mapOrderedBounded(
                items = chunk,
                maxConcurrency = MAX_PARALLEL_APK_INSPECTS
            ) { asset ->
                asset to source.inspectApk(asset = asset, lookupConfig = lookupConfig)
            }
            inspected.forEach { (asset, result) ->
                val info = result.getOrNull()
                if (info != null) {
                    inspectedCandidates += GitHubInspectedApkCandidate(
                        asset = asset,
                        manifest = info,
                    )
                } else if (firstFailure == null) {
                    firstFailure = result.exceptionOrNull()
                }
            }
            val selection = GitHubApkCandidateSelectionEngine.selectInspected(
                inspected = inspectedCandidates,
                expectedPackageName = requestedPackageName,
            )
            if (
                selection != null &&
                (requestedPackageName.isBlank() || selection.packageMatched)
            ) {
                return ApkInspectSelection(
                    selected = selection.candidate.asset to selection.candidate.manifest,
                    firstFailure = firstFailure,
                    packageMismatchVerified = false,
                )
            }
        }
        val fallback = GitHubApkCandidateSelectionEngine.selectInspected(
            inspected = inspectedCandidates,
            expectedPackageName = requestedPackageName,
        )
        val selected = fallback?.takeIf { selection ->
            requestedPackageName.isBlank() || selection.packageMatched
        }
        return ApkInspectSelection(
            selected = selected?.candidate?.let { it.asset to it.manifest },
            firstFailure = firstFailure,
            packageMismatchVerified = requestedPackageName.isNotBlank() &&
                inspectedCandidates.size == apkAssets.size &&
                inspectedCandidates.all {
                    it.manifest.packageName.isNotBlank() &&
                        !it.manifest.packageName.equals(requestedPackageName, ignoreCase = true)
                },
        )
    }

    private companion object {
        const val MAX_PARALLEL_APK_INSPECTS = 4
    }

    private data class ApkInspectSelection(
        val selected: Pair<GitHubReleaseAssetFile, GitHubApkManifestInfo>?,
        val firstFailure: Throwable?,
        val packageMismatchVerified: Boolean,
    )
}

private class DefaultGitHubPreciseApkVersionSource(
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository()
) : GitHubPreciseApkVersionSource {
    override suspend fun loadReleaseAssetBundle(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubReleaseAssetBundle> = runCatching {
        val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
        val cacheKey = GitHubReleaseAssetCacheStore.buildCacheKey(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            hasApiToken = lookupConfig.apiToken.isNotBlank()
        )
        val sourceSignature = lookupConfig.githubAssetSourceSignature()
        val refreshIntervalHours = GitHubTrackStore.loadRefreshIntervalHours()
        val cached = GitHubReleaseAssetCacheStore.load(
            cacheKey = cacheKey,
            refreshIntervalHours = refreshIntervalHours
        )
        if (cached != null) {
            val signed = cached.copy(sourceConfigSignature = sourceSignature)
            if (cached.sourceConfigSignature != sourceSignature) {
                GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = signed)
            }
            return@runCatching signed
        }

        GitHubReleaseAssetRepository.fetchApkAssets(
            owner = owner,
            repo = repo,
            rawTag = rawTag,
            releaseUrl = releaseUrl,
            preferHtml = preferHtml,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            apiToken = lookupConfig.apiToken
        ).getOrThrow().copy(
            sourceConfigSignature = sourceSignature
        ).also { bundle ->
            GitHubReleaseAssetCacheStore.save(cacheKey = cacheKey, bundle = bundle)
        }
    }

    override suspend fun inspectApk(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> {
        return apkInfoRepository.inspect(asset = asset, lookupConfig = lookupConfig)
    }
}
