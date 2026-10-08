package os.kei.feature.github.data.remote

import os.kei.core.io.cancellableResult
import os.kei.feature.github.domain.GitHubApkPackageNameScanSource
import os.kei.feature.github.domain.GitHubScanReleaseApkAssets
import os.kei.feature.github.domain.GitHubScanReleaseTarget
import os.kei.feature.github.engine.release.GitHubReleaseCandidateRanker
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot

class GitHubApkPackageNameScanRepository(
    private val manifestReader: GitHubApkManifestReader = GitHubApkManifestReader(),
    private val releases: GitHubPackageScanReleaseSource = RemoteGitHubPackageScanReleaseSource,
) : GitHubApkPackageNameScanSource {
    override suspend fun loadScanRelease(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        includePreRelease: Boolean,
    ): Result<GitHubScanReleaseTarget> = loadScanReleaseApkAssets(
        owner, repo, lookupConfig, includePreRelease,
    ).map { it.release }

    override suspend fun loadScanReleaseApkAssets(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        includePreRelease: Boolean,
    ): Result<GitHubScanReleaseApkAssets> = cancellableResult {
        // /latest deliberately excludes prereleases. Keep its inexpensive stable path,
        // but use the release window when the editor permits the preview channel.
        if (!includePreRelease && lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken) {
            tryLatestStableApkAssets(owner, repo, lookupConfig)?.let {
                return@cancellableResult it
            }
        }

        val selected = cancellableResult {
            releases.loadSnapshot(owner, repo, lookupConfig).getOrThrow()
        }
        val snapshot = selected.getOrElse {
            val fallback = lookupConfig.copy(
                selectedStrategy = when (lookupConfig.selectedStrategy) {
                    GitHubLookupStrategyOption.AtomFeed -> GitHubLookupStrategyOption.GitHubApiToken
                    GitHubLookupStrategyOption.GitHubApiToken -> GitHubLookupStrategyOption.AtomFeed
                },
            )
            releases.loadSnapshot(owner, repo, fallback).getOrThrow()
        }
        val candidates = scanCandidates(snapshot, includePreRelease)

        var stableFallback: GitHubScanReleaseApkAssets? = null
        var sawAllowedRelease = false
        var firstFailure: Throwable? = null
        for (candidate in candidates) {
            val result = cancellableResult {
                releases.fetchApkAssets(owner, repo, candidate.release, lookupConfig).getOrThrow()
            }
            val bundle = result.getOrNull()
            if (bundle == null) {
                if (firstFailure == null) firstFailure = result.exceptionOrNull()
                continue
            }
            // Atom cannot reliably classify numeric tags such as v0.3.1. Asset
            // metadata carries the API flag or the release page's Pre-release badge.
            val isPreRelease = bundle.isPreRelease ?: candidate.isPreRelease
            if (!includePreRelease && isPreRelease) continue
            sawAllowedRelease = true
            val assets = bundle.scanAssets(owner, repo) ?: continue
            if (!includePreRelease || isPreRelease) return@cancellableResult assets
            if (stableFallback == null) stableFallback = assets
        }
        stableFallback?.let { return@cancellableResult it }
        // Atom's inferred channel can also exclude a real stable release whose
        // tag contains alpha/beta. Retain the authoritative /latest fallback.
        if (!includePreRelease && lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed) {
            tryLatestStableApkAssets(owner, repo, lookupConfig)?.let { return@cancellableResult it }
        }
        firstFailure?.let { throw it }
        error(
            if (sawAllowedRelease) "The target release contains no usable APK"
            else if (includePreRelease && candidates.isEmpty()) "This repository has no usable release"
            else "This repository has no stable release",
        )
    }

    override suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubScanReleaseTarget,
        lookupConfig: GitHubLookupConfig,
    ): Result<List<GitHubReleaseAssetFile>> = releases.fetchApkAssets(
        owner, repo, release, lookupConfig,
    ).map { bundle -> bundle.assets.filter { it.name.endsWith(".apk", ignoreCase = true) } }

    override suspend fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
    ): Result<ByteArray> = manifestReader.readAndroidManifestBytes(asset, lookupConfig)

    private suspend fun tryLatestStableApkAssets(
        owner: String,
        repo: String,
        config: GitHubLookupConfig,
    ): GitHubScanReleaseApkAssets? = cancellableResult {
        releases.fetchLatestStableApkAssets(owner, repo, config).getOrThrow()
    }.getOrNull()?.takeIf { it.isPreRelease != true }?.scanAssets(owner, repo)

    private fun GitHubReleaseAssetBundle.scanAssets(owner: String, repo: String): GitHubScanReleaseApkAssets? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty() || tagName.isBlank()) return null
        return GitHubScanReleaseApkAssets(
            release = GitHubScanReleaseTarget(
                tag = tagName,
                releaseUrl = htmlUrl.ifBlank { GitHubVersionUtils.buildReleaseTagUrl(owner, repo, tagName) },
            ),
            assets = apks,
        )
    }

    private fun scanCandidates(
        snapshot: GitHubRepositoryReleaseSnapshot,
        includePreRelease: Boolean,
    ): List<ScanCandidate> {
        val ranked = GitHubReleaseCandidateRanker.newestFirst(snapshot.feed.entries)
        val candidates = buildList {
            if (includePreRelease) {
                snapshot.latestPreRelease?.let { add(ScanCandidate(it.rawTag, it.link, true)) }
                ranked.filter { it.isLikelyPreRelease }.forEach { add(ScanCandidate(it.tag, it.link, true)) }
            }
            if (snapshot.hasStableRelease) {
                val stable = snapshot.latestStable
                add(ScanCandidate(stable.rawTag, stable.link, false))
            }
            ranked.filter { !it.isLikelyPreRelease }.forEach { add(ScanCandidate(it.tag, it.link, false)) }
        }
        return candidates.filter { it.release.tag.isNotBlank() }
            .distinctBy { it.release.tag }
            .take(MAX_RELEASE_SCAN_CANDIDATES)
    }

    private data class ScanCandidate(val release: GitHubScanReleaseTarget, val isPreRelease: Boolean) {
        constructor(tag: String, url: String, isPreRelease: Boolean) : this(
            GitHubScanReleaseTarget(tag.trim().ifBlank { GitHubReleaseAssetRepository.parseReleaseTagFromUrl(url) }, url),
            isPreRelease,
        )
    }

    companion object {
        private const val MAX_RELEASE_SCAN_CANDIDATES = 12
    }
}
