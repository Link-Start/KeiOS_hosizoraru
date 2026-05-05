package os.kei.feature.github.data.remote

import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.domain.GitHubApkPackageNameScanSource
import os.kei.feature.github.domain.GitHubStableReleaseApkAssets
import os.kei.feature.github.domain.GitHubStableReleaseTarget
import os.kei.feature.github.domain.scanPreferHtmlAssets
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption

internal class GitHubApkPackageNameScanRepository(
    private val zipEntryReader: RemoteZipEntryReader = RemoteZipEntryReader()
) : GitHubApkPackageNameScanSource {
    override fun loadLatestStableRelease(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseTarget> = runCatching {
        val snapshot = when (lookupConfig.selectedStrategy) {
            GitHubLookupStrategyOption.AtomFeed -> {
                GitHubAtomReleaseStrategy.loadSnapshot(owner, repo).getOrThrow()
            }

            GitHubLookupStrategyOption.GitHubApiToken -> {
                GitHubApiTokenReleaseStrategy(
                    apiToken = lookupConfig.apiToken
                ).loadSnapshot(owner, repo).getOrThrow()
            }
        }
        check(snapshot.hasStableRelease) { "This repository has no stable release" }
        val latestStable = snapshot.latestStable
        val tag = latestStable.rawTag.trim().ifBlank {
            GitHubReleaseAssetRepository.parseReleaseTagFromUrl(latestStable.link)
        }
        check(tag.isNotBlank()) { "This repository has no stable release tag" }
        GitHubStableReleaseTarget(
            tag = tag,
            releaseUrl = latestStable.link.trim().ifBlank {
                GitHubVersionUtils.buildReleaseTagUrl(owner, repo, tag)
            }
        )
    }

    override fun loadLatestStableApkAssets(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseApkAssets> {
        val fastPath = GitHubReleaseAssetRepository.fetchLatestStableApkAssets(
            owner = owner,
            repo = repo,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            apiToken = lookupConfig.apiToken
        ).map { bundle ->
            GitHubStableReleaseApkAssets(
                release = GitHubStableReleaseTarget(
                    tag = bundle.tagName,
                    releaseUrl = bundle.htmlUrl.ifBlank {
                        GitHubVersionUtils.buildReleaseTagUrl(owner, repo, bundle.tagName)
                    }
                ),
                assets = bundle.assets.filter { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true)
                }
            )
        }
        if (fastPath.isSuccess) return fastPath

        return try {
            super.loadLatestStableApkAssets(
                owner = owner,
                repo = repo,
                lookupConfig = lookupConfig
            )
        } finally {
            clearScanFallbackCaches(lookupConfig)
        }
    }

    override fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubStableReleaseTarget,
        lookupConfig: GitHubLookupConfig
    ): Result<List<GitHubReleaseAssetFile>> {
        return GitHubReleaseAssetRepository.fetchApkAssets(
            owner = owner,
            repo = repo,
            rawTag = release.tag,
            releaseUrl = release.releaseUrl,
            preferHtml = lookupConfig.scanPreferHtmlAssets,
            aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
            includeAllAssets = false,
            apiToken = lookupConfig.apiToken
        ).map { bundle ->
            bundle.assets.filter { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            }
        }
    }

    override fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        val primary = zipEntryReader.readEntry(
            url = asset.downloadUrl,
            entryName = ANDROID_MANIFEST_ENTRY,
            apiToken = lookupConfig.apiToken
        )
        if (primary.isSuccess || lookupConfig.selectedStrategy != GitHubLookupStrategyOption.GitHubApiToken) {
            return primary
        }

        val token = lookupConfig.apiToken.trim()
        if (token.isBlank() || asset.apiAssetUrl.isBlank()) return primary

        return GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = true,
            apiToken = token
        ).mapCatching { apiDownloadUrl ->
            zipEntryReader.readEntry(
                url = apiDownloadUrl,
                entryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token
            ).getOrThrow()
        }.recoverCatching {
            primary.getOrThrow()
        }
    }

    companion object {
        private const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"

        private fun clearScanFallbackCaches(lookupConfig: GitHubLookupConfig) {
            when (lookupConfig.selectedStrategy) {
                GitHubLookupStrategyOption.AtomFeed -> {
                    GitHubAtomReleaseStrategy.clearCaches()
                }

                GitHubLookupStrategyOption.GitHubApiToken -> {
                    GitHubApiTokenReleaseStrategy(
                        apiToken = lookupConfig.apiToken
                    ).clearCaches()
                }
            }
        }
    }
}
