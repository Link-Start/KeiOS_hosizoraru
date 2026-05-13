package os.kei.feature.github.domain

import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubDirectApkJsonFallback
import os.kei.feature.github.data.remote.GitHubDirectApkJsonFallbackResolver
import os.kei.feature.github.data.remote.GitHubDirectApkVersionedDirectoryResolution
import os.kei.feature.github.data.remote.GitHubDirectApkVersionedDirectoryResolver
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GITHUB_DIRECT_APK_STRATEGY_ID
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubDirectApkRemoteHealth
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.directApkCheckSourceSignature

internal class GitHubDirectApkReleaseCheckSource(
    private val apkInfoRepository: GitHubApkInfoRepository = GitHubApkInfoRepository(),
    private val jsonFallbackResolver: GitHubDirectApkJsonFallbackResolver =
        GitHubDirectApkJsonFallbackResolver(),
    private val versionedDirectoryResolver: GitHubDirectApkVersionedDirectoryResolver =
        GitHubDirectApkVersionedDirectoryResolver()
) {
    suspend fun evaluate(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        localVersion: String,
        localVersionCode: Long,
        forceRefresh: Boolean
    ): GitHubTrackedReleaseCheck {
        val asset = buildDirectApkAsset(item)
            ?: return failedCheck(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                detail = "invalid direct APK URL"
            )
        val directLookupConfig = lookupConfig.copy(
            selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
            apiToken = "",
            checkAllTrackedPreReleases = false,
            preciseApkVersionEnabled = true
        )
        // Direct APK URLs often keep the same URL and filename while serving a newer APK.
        val versionedDirectoryResolution = versionedDirectoryResolver
            .resolve(asset.downloadUrl)
            .getOrNull()
        val primaryAsset = versionedDirectoryResolution?.toAsset(asset.name) ?: asset
        val manifestResult = apkInfoRepository.inspectAsync(
            asset = primaryAsset,
            lookupConfig = directLookupConfig,
            forceRefresh = true
        ).map { manifest ->
            versionedDirectoryResolution?.let { resolution ->
                manifest.withVersionedDirectoryResolution(resolution, primaryAsset)
            } ?: manifest
        }
        val manifest = manifestResult.getOrElse { directError ->
            val originalAsset = asset.takeIf { primaryAsset.downloadUrl != asset.downloadUrl }
            val jsonFallback = jsonFallbackResolver.resolve(asset.downloadUrl).getOrNull()
            val fallbackTargets = buildList {
                originalAsset?.let { fallbackAsset ->
                    add(DirectApkFallbackTarget(asset = fallbackAsset))
                }
                jsonFallback?.let { fallback ->
                    add(
                        DirectApkFallbackTarget(
                            asset = fallback.toAsset(),
                            jsonFallback = fallback
                        )
                    )
                }
            }
            fallbackTargets.forEach { target ->
                apkInfoRepository.inspectAsync(
                    asset = target.asset,
                    lookupConfig = directLookupConfig,
                    forceRefresh = true
                ).getOrNull()?.let { fallbackManifest ->
                    return evaluateManifest(
                        item = item,
                        localVersion = localVersion,
                        localVersionCode = localVersionCode,
                        manifest = target.jsonFallback?.let { fallback ->
                            fallbackManifest.withJsonFallback(fallback)
                        } ?: fallbackManifest,
                        sourceConfigSignature = directApkSourceSignature(item)
                    )
                }
            }
            return failedCheck(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                detail = directError.message.orEmpty().ifBlank { "remote APK manifest read failed" }
            )
        }
        return evaluateManifest(
            item = item,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            manifest = manifest,
            sourceConfigSignature = directApkSourceSignature(item)
        )
    }

    private data class DirectApkFallbackTarget(
        val asset: GitHubReleaseAssetFile,
        val jsonFallback: GitHubDirectApkJsonFallback? = null
    )

    private fun failedCheck(
        item: GitHubTrackedApp,
        localVersion: String,
        localVersionCode: Long,
        detail: String
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = DIRECT_APK_STRATEGY_ID,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            sourceConfigSignature = directApkSourceSignature(item),
            directApkRemoteHealth = GitHubDirectApkRemoteHealth.Degraded,
            directApkRemoteHealthMessage = detail,
            directApkRemoteCheckedAtMillis = System.currentTimeMillis(),
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(detail)
        )
    }

    internal companion object {
        const val DIRECT_APK_STRATEGY_ID = GITHUB_DIRECT_APK_STRATEGY_ID

        fun buildDirectApkAsset(item: GitHubTrackedApp): GitHubReleaseAssetFile? {
            val identity = buildDirectApkTrackIdentity(item.repoUrl) ?: return null
            return GitHubReleaseAssetFile(
                name = identity.assetName,
                downloadUrl = identity.url,
                sizeBytes = 0L,
                downloadCount = 0,
                contentType = "application/vnd.android.package-archive"
            )
        }

        private fun GitHubApkManifestInfo.withJsonFallback(
            fallback: GitHubDirectApkJsonFallback
        ): GitHubApkManifestInfo {
            return copy(
                assetName = assetName.ifBlank { fallback.toAsset().name },
                versionName = versionName.ifBlank { fallback.versionName },
                versionCode = versionCode.ifBlank { fallback.versionCode },
                fetchSource = fallback.fileUrl
            )
        }

        private fun GitHubApkManifestInfo.withVersionedDirectoryResolution(
            resolution: GitHubDirectApkVersionedDirectoryResolution,
            asset: GitHubReleaseAssetFile
        ): GitHubApkManifestInfo {
            return copy(
                assetName = asset.name,
                versionName = versionName.ifBlank { resolution.version },
                fetchSource = resolution.downloadUrl
            )
        }

        fun evaluateManifest(
            item: GitHubTrackedApp,
            localVersion: String,
            localVersionCode: Long,
            manifest: GitHubApkManifestInfo,
            sourceConfigSignature: String = directApkSourceSignature(item)
        ): GitHubTrackedReleaseCheck {
            val trackedPackage = item.packageName.trim()
            val remotePackage = manifest.packageName.trim()
            if (
                trackedPackage.isNotBlank() &&
                remotePackage.isNotBlank() &&
                !trackedPackage.equals(remotePackage, ignoreCase = true)
            ) {
                return GitHubTrackedReleaseCheck(
                    strategyId = DIRECT_APK_STRATEGY_ID,
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    sourceConfigSignature = sourceConfigSignature,
                    directApkRemoteHealth = GitHubDirectApkRemoteHealth.Available,
                    directApkRemoteCheckedAtMillis = System.currentTimeMillis(),
                    status = GitHubTrackedReleaseStatus.Failed,
                    message = GitHubTrackedReleaseStatus.Failed.failureMessage(
                        "remote package $remotePackage does not match $trackedPackage"
                    )
                )
            }
            val preciseInfo = GitHubRemoteApkVersionInfo(
                releaseName = manifest.appLabel.ifBlank { item.appLabel },
                releaseTag = manifest.versionName.ifBlank { manifest.versionCode },
                releaseUrl = item.repoUrl,
                assetName = manifest.assetName,
                packageName = remotePackage,
                versionName = manifest.versionName,
                versionCode = manifest.versionCode,
                fetchSource = manifest.fetchSource.ifBlank { DIRECT_APK_STRATEGY_ID }
            )
            val displayVersion = preciseInfo.versionLabel()
                .ifBlank { preciseInfo.releaseLabel() }
                .ifBlank { manifest.assetName }
            val candidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to manifest.versionName,
                GitHubVersionCandidateSource.Title to manifest.versionCode,
                GitHubVersionCandidateSource.Link to item.repoUrl
            )
            val stableSignal = GitHubReleaseVersionSignals(
                displayVersion = displayVersion,
                rawTag = manifest.versionName.ifBlank { manifest.versionCode },
                rawName = manifest.appLabel.ifBlank { manifest.assetName },
                link = item.repoUrl,
                versionCandidates = candidates,
                source = GitHubReleaseSignalSource.AtomFallback,
                channel = GitHubReleaseChannel.STABLE
            )
            val entry = GitHubAtomReleaseEntry(
                tag = stableSignal.rawTag,
                title = stableSignal.rawName.ifBlank { stableSignal.displayVersion },
                link = item.repoUrl,
                versionCandidates = candidates,
                channel = GitHubReleaseChannel.STABLE,
                isLikelyPreRelease = false
            )
            val snapshot = GitHubRepositoryReleaseSnapshot(
                strategyId = DIRECT_APK_STRATEGY_ID,
                feed = GitHubAtomFeed(
                    title = item.appLabel,
                    feedUrl = item.repoUrl,
                    entries = listOf(entry)
                ),
                latestStable = stableSignal,
                hasStableRelease = true
            )
            return GitHubReleaseCheckService.evaluateSnapshot(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                snapshot = snapshot,
                checkAllTrackedPreReleases = false,
                preciseStableApkVersion = preciseInfo,
                sourceConfigSignature = sourceConfigSignature
            ).copy(
                directApkRemoteHealth = GitHubDirectApkRemoteHealth.Available,
                directApkRemoteCheckedAtMillis = System.currentTimeMillis()
            )
        }

        fun directApkSourceSignature(item: GitHubTrackedApp): String {
            return item.directApkCheckSourceSignature()
        }
    }
}
