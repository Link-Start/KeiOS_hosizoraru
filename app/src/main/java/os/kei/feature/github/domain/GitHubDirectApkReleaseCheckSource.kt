package os.kei.feature.github.domain

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubDirectApkDirectoryIndexResolution
import os.kei.feature.github.data.remote.GitHubDirectApkDirectoryIndexResolver
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
        GitHubDirectApkVersionedDirectoryResolver(),
    private val directoryIndexResolver: GitHubDirectApkDirectoryIndexResolver =
        GitHubDirectApkDirectoryIndexResolver()
) {
    suspend fun evaluate(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        localVersion: String,
        localVersionCode: Long,
        forceRefresh: Boolean
    ): GitHubTrackedReleaseCheck {
        val sourceConfigSignature = directApkSourceSignature(item, lookupConfig)
        val asset = buildDirectApkAsset(item)
            ?: return failedCheck(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                detail = "invalid direct APK URL",
                sourceConfigSignature = sourceConfigSignature
            )
        val directLookupConfig = lookupConfig.copy(
            selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
            apiToken = "",
            checkAllTrackedPreReleases = false,
            preciseApkVersionEnabled = true
        )
        val localChannel = GitHubVersionUtils.classifyVersionChannel(localVersion)
        val shouldInspectPreRelease = lookupConfig.checkAllTrackedPreReleases ||
                item.preferPreRelease ||
                localChannel?.isPreRelease == true
        // Direct APK URLs often keep the same URL and filename while serving a newer APK.
        val targets = resolveDirectApkTargets(
            asset = asset,
            localVersion = localVersion,
            inspectPreRelease = shouldInspectPreRelease
        )
        val manifestResults = inspectDirectApkTargets(
            targets = targets,
            lookupConfig = directLookupConfig
        )
        val stableManifest = manifestResults.stable?.getOrNull()
        val preReleaseManifest = manifestResults.preRelease?.getOrNull()
        if (stableManifest != null || preReleaseManifest != null) {
            return evaluateManifests(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                stableManifest = stableManifest,
                preReleaseManifest = preReleaseManifest,
                checkAllTrackedPreReleases = shouldInspectPreRelease,
                sourceConfigSignature = sourceConfigSignature
            )
        }

        val directError = manifestResults.stable?.exceptionOrNull()
            ?: manifestResults.preRelease?.exceptionOrNull()
            ?: IllegalStateException("remote APK manifest read failed")
        val attemptedUrls = listOfNotNull(
            targets.stable?.asset?.downloadUrl,
            targets.preRelease?.asset?.downloadUrl
        ).toSet()
        val originalAsset = asset.takeIf { asset.downloadUrl !in attemptedUrls }
        val jsonFallback = jsonFallbackResolver.resolve(asset.downloadUrl).getOrNull()
        val fallbackTargets = buildList {
            originalAsset?.let { fallbackAsset ->
                add(
                    DirectApkResolvedTarget(
                        asset = fallbackAsset,
                        channel = fallbackAsset.releaseChannel()
                    )
                )
            }
            jsonFallback?.let { fallback ->
                add(
                    DirectApkResolvedTarget(
                        asset = fallback.toAsset(),
                        channel = fallback.releaseChannel(),
                        jsonFallback = fallback
                    )
                )
            }
        }
        fallbackTargets.forEach { target ->
            inspectDirectApkTarget(
                target = target,
                lookupConfig = directLookupConfig,
                forceRefresh = true
            ).getOrNull()?.let { fallbackManifest ->
                return evaluateManifest(
                    item = item,
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    manifest = fallbackManifest,
                    releaseChannel = target.channel,
                    checkAllTrackedPreReleases = shouldInspectPreRelease,
                    sourceConfigSignature = sourceConfigSignature
                )
            }
        }
        return failedCheck(
            item = item,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            detail = directError.message.orEmpty().ifBlank { "remote APK manifest read failed" },
            sourceConfigSignature = sourceConfigSignature
        )
    }

    private fun resolveDirectApkTargets(
        asset: GitHubReleaseAssetFile,
        localVersion: String,
        inspectPreRelease: Boolean
    ): DirectApkResolvedTargets {
        val jsonPrimaryResolution = if (asset.downloadUrl.endsWith(".json", ignoreCase = true)) {
            jsonFallbackResolver.resolve(asset.downloadUrl).getOrNull()
        } else {
            null
        }
        if (jsonPrimaryResolution != null) {
            val target = DirectApkResolvedTarget(
                asset = jsonPrimaryResolution.toAsset(),
                channel = jsonPrimaryResolution.releaseChannel(),
                jsonFallback = jsonPrimaryResolution
            )
            return DirectApkResolvedTargets.fromSingle(target)
        }

        val versionedTargets = versionedDirectoryResolver
            .resolveTargets(
                directApkUrl = asset.downloadUrl,
                includePreRelease = inspectPreRelease
            )
            .getOrNull()
        if (versionedTargets != null) {
            return DirectApkResolvedTargets(
                stable = versionedTargets.stable?.let { resolution ->
                    DirectApkResolvedTarget(
                        asset = resolution.toAsset(asset.name),
                        channel = resolution.channel,
                        versionedDirectoryResolution = resolution
                    )
                },
                preRelease = versionedTargets.preRelease?.let { resolution ->
                    DirectApkResolvedTarget(
                        asset = resolution.toAsset(asset.name),
                        channel = resolution.channel,
                        versionedDirectoryResolution = resolution
                    )
                }
            )
        }

        val directoryTargets = directoryIndexResolver
            .resolveTargets(
                rawUrl = asset.downloadUrl,
                localVersion = localVersion,
                includePreRelease = inspectPreRelease
            )
            .getOrNull()
        if (directoryTargets != null) {
            return DirectApkResolvedTargets(
                stable = directoryTargets.stable?.let { resolution ->
                    DirectApkResolvedTarget(
                        asset = resolution.toAsset(asset.name),
                        channel = resolution.channel,
                        directoryIndexResolution = resolution
                    )
                },
                preRelease = directoryTargets.preRelease?.let { resolution ->
                    DirectApkResolvedTarget(
                        asset = resolution.toAsset(asset.name),
                        channel = resolution.channel,
                        directoryIndexResolution = resolution
                    )
                }
            )
        }

        return DirectApkResolvedTargets.fromSingle(
            DirectApkResolvedTarget(
                asset = asset,
                channel = asset.releaseChannel()
            )
        )
    }

    private suspend fun inspectDirectApkTargets(
        targets: DirectApkResolvedTargets,
        lookupConfig: GitHubLookupConfig
    ): DirectApkManifestResults {
        val inspectTargets = listOfNotNull(
            targets.stable?.let { DirectApkInspectTargetSlot(stable = true, target = it) },
            targets.preRelease?.let { DirectApkInspectTargetSlot(stable = false, target = it) }
        )
        if (inspectTargets.isEmpty()) return DirectApkManifestResults()
        val inspected = GitHubExecution.mapOrderedBounded(
            items = inspectTargets,
            maxConcurrency = 2
        ) { slot ->
            slot to inspectDirectApkTarget(
                target = slot.target,
                lookupConfig = lookupConfig,
                forceRefresh = true
            )
        }
        val stable = inspected.firstOrNull { (slot, _) -> slot.stable }?.second
        val preRelease = inspected.firstOrNull { (slot, _) -> !slot.stable }?.second
        return DirectApkManifestResults(
            stable = stable,
            preRelease = preRelease
        )
    }

    private suspend fun inspectDirectApkTarget(
        target: DirectApkResolvedTarget,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean
    ): Result<GitHubApkManifestInfo> {
        return apkInfoRepository.inspect(
            asset = target.asset,
            lookupConfig = lookupConfig,
            forceRefresh = forceRefresh
        ).map { manifest ->
            target.jsonFallback?.let { resolution ->
                manifest.withJsonFallback(resolution)
            } ?: target.versionedDirectoryResolution?.let { resolution ->
                manifest.withVersionedDirectoryResolution(resolution, target.asset)
            } ?: target.directoryIndexResolution?.let { resolution ->
                manifest.withDirectoryIndexResolution(resolution, target.asset)
            } ?: manifest.copy(
                assetName = manifest.assetName.ifBlank { target.asset.name },
                fetchSource = manifest.fetchSource.ifBlank { target.asset.downloadUrl }
            )
        }
    }

    private data class DirectApkManifestResults(
        val stable: Result<GitHubApkManifestInfo>? = null,
        val preRelease: Result<GitHubApkManifestInfo>? = null
    )

    private data class DirectApkInspectTargetSlot(
        val stable: Boolean,
        val target: DirectApkResolvedTarget
    )

    private data class DirectApkResolvedTargets(
        val stable: DirectApkResolvedTarget?,
        val preRelease: DirectApkResolvedTarget?
    ) {
        companion object {
            fun fromSingle(target: DirectApkResolvedTarget): DirectApkResolvedTargets {
                return if (target.channel.isPreRelease) {
                    DirectApkResolvedTargets(stable = null, preRelease = target)
                } else {
                    DirectApkResolvedTargets(stable = target, preRelease = null)
                }
            }
        }
    }

    private data class DirectApkResolvedTarget(
        val asset: GitHubReleaseAssetFile,
        val channel: GitHubReleaseChannel,
        val jsonFallback: GitHubDirectApkJsonFallback? = null,
        val versionedDirectoryResolution: GitHubDirectApkVersionedDirectoryResolution? = null,
        val directoryIndexResolution: GitHubDirectApkDirectoryIndexResolution? = null
    )

    private fun failedCheck(
        item: GitHubTrackedApp,
        localVersion: String,
        localVersionCode: Long,
        detail: String,
        sourceConfigSignature: String = directApkSourceSignature(item)
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = DIRECT_APK_STRATEGY_ID,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            sourceConfigSignature = sourceConfigSignature,
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
                fetchSource = fallback.fileUrl,
                releaseNotes = releaseNotes.ifBlank { fallback.changelog }
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

        private fun GitHubApkManifestInfo.withDirectoryIndexResolution(
            resolution: GitHubDirectApkDirectoryIndexResolution,
            asset: GitHubReleaseAssetFile
        ): GitHubApkManifestInfo {
            return copy(
                assetName = asset.name,
                versionName = versionName.ifBlank { resolution.version },
                fetchSource = resolution.downloadUrl,
                releaseNotes = releaseNotes.ifBlank { resolution.releaseNotes }
            )
        }

        fun evaluateManifest(
            item: GitHubTrackedApp,
            localVersion: String,
            localVersionCode: Long,
            manifest: GitHubApkManifestInfo,
            releaseChannel: GitHubReleaseChannel = GitHubReleaseChannel.STABLE,
            checkAllTrackedPreReleases: Boolean = false,
            sourceConfigSignature: String = directApkSourceSignature(item)
        ): GitHubTrackedReleaseCheck {
            return evaluateManifests(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                stableManifest = manifest.takeUnless { releaseChannel.isPreRelease },
                preReleaseManifest = manifest.takeIf { releaseChannel.isPreRelease },
                checkAllTrackedPreReleases = checkAllTrackedPreReleases,
                sourceConfigSignature = sourceConfigSignature
            )
        }

        fun evaluateManifests(
            item: GitHubTrackedApp,
            localVersion: String,
            localVersionCode: Long,
            stableManifest: GitHubApkManifestInfo?,
            preReleaseManifest: GitHubApkManifestInfo?,
            checkAllTrackedPreReleases: Boolean = false,
            sourceConfigSignature: String = directApkSourceSignature(item)
        ): GitHubTrackedReleaseCheck {
            val trackedPackage = item.packageName.trim()
            listOfNotNull(stableManifest, preReleaseManifest)
                .firstOrNull { manifest ->
                    val remotePackage = manifest.packageName.trim()
                    trackedPackage.isNotBlank() &&
                            remotePackage.isNotBlank() &&
                            !trackedPackage.equals(remotePackage, ignoreCase = true)
                }
                ?.let { mismatch ->
                    val remotePackage = mismatch.packageName.trim()
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
            val stablePreciseInfo = stableManifest?.toRemoteApkVersionInfo(item)
            val preReleasePreciseInfo = preReleaseManifest?.toRemoteApkVersionInfo(item)
            val stableSignal = stableManifest?.toReleaseSignal(
                item = item,
                preciseInfo = requireNotNull(stablePreciseInfo),
                channel = GitHubReleaseChannel.STABLE
            )
            val preReleaseSignal = preReleaseManifest?.toReleaseSignal(
                item = item,
                preciseInfo = requireNotNull(preReleasePreciseInfo),
                channel = preReleaseManifest.releaseChannel()
            )
            val hasStableRelease = stableSignal != null
            val fallbackSignal = stableSignal
                ?: preReleaseSignal
                ?: GitHubReleaseVersionSignals(
                    displayVersion = item.repoUrl,
                    rawTag = item.repoUrl,
                    rawName = item.appLabel,
                    link = item.repoUrl,
                    source = GitHubReleaseSignalSource.AtomFallback,
                    channel = GitHubReleaseChannel.UNKNOWN
                )
            val entries = listOfNotNull(
                stableSignal?.toAtomEntry(GitHubReleaseChannel.STABLE),
                preReleaseSignal?.toAtomEntry(preReleaseSignal.channel)
            )
            val snapshot = GitHubRepositoryReleaseSnapshot(
                strategyId = DIRECT_APK_STRATEGY_ID,
                feed = GitHubAtomFeed(
                    title = item.appLabel,
                    feedUrl = item.repoUrl,
                    entries = entries
                ),
                latestStable = stableSignal ?: fallbackSignal,
                hasStableRelease = hasStableRelease,
                latestPreRelease = preReleaseSignal
            )
            return GitHubReleaseCheckService.evaluateSnapshot(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                snapshot = snapshot,
                checkAllTrackedPreReleases = checkAllTrackedPreReleases,
                preciseStableApkVersion = stablePreciseInfo,
                precisePreReleaseApkVersion = preReleasePreciseInfo,
                sourceConfigSignature = sourceConfigSignature
            ).copy(
                directApkRemoteHealth = GitHubDirectApkRemoteHealth.Available,
                directApkRemoteCheckedAtMillis = System.currentTimeMillis()
            )
        }

        private fun GitHubApkManifestInfo.toRemoteApkVersionInfo(
            item: GitHubTrackedApp
        ): GitHubRemoteApkVersionInfo {
            return GitHubRemoteApkVersionInfo(
                releaseName = appLabel.ifBlank { item.appLabel },
                releaseTag = versionName.ifBlank { versionCode },
                releaseUrl = fetchSource.ifBlank { item.repoUrl },
                assetName = assetName,
                packageName = packageName.trim(),
                versionName = versionName,
                versionCode = versionCode,
                fetchSource = fetchSource.ifBlank { DIRECT_APK_STRATEGY_ID },
                releaseNotes = releaseNotes
            )
        }

        private fun GitHubApkManifestInfo.toReleaseSignal(
            item: GitHubTrackedApp,
            preciseInfo: GitHubRemoteApkVersionInfo,
            channel: GitHubReleaseChannel
        ): GitHubReleaseVersionSignals {
            val displayVersion = preciseInfo.versionLabel()
                .ifBlank { preciseInfo.releaseLabel() }
                .ifBlank { assetName }
            val link = fetchSource.ifBlank { item.repoUrl }
            return GitHubReleaseVersionSignals(
                displayVersion = displayVersion,
                rawTag = versionName.ifBlank { versionCode },
                rawName = appLabel.ifBlank { assetName },
                link = link,
                versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                    GitHubVersionCandidateSource.Tag to versionName,
                    GitHubVersionCandidateSource.Title to versionCode,
                    GitHubVersionCandidateSource.Link to link
                ),
                source = GitHubReleaseSignalSource.AtomFallback,
                channel = channel
            )
        }

        private fun GitHubApkManifestInfo.releaseChannel(): GitHubReleaseChannel {
            return GitHubVersionUtils.classifyVersionChannel(
                listOf(versionName, assetName, fetchSource).joinToString(" ")
            ) ?: GitHubReleaseChannel.PREVIEW
        }

        private fun GitHubReleaseVersionSignals.toAtomEntry(
            channel: GitHubReleaseChannel
        ): GitHubAtomReleaseEntry {
            return GitHubAtomReleaseEntry(
                tag = rawTag,
                title = rawName.ifBlank { displayVersion },
                link = link,
                versionCandidates = versionCandidates,
                channel = channel,
                isLikelyPreRelease = channel.isPreRelease
            )
        }

        fun directApkSourceSignature(
            item: GitHubTrackedApp,
            lookupConfig: GitHubLookupConfig = GitHubLookupConfig()
        ): String {
            return item.directApkCheckSourceSignature(lookupConfig.checkAllTrackedPreReleases)
        }
    }
}

private fun GitHubDirectApkJsonFallback.releaseChannel(): GitHubReleaseChannel {
    return GitHubVersionUtils.classifyVersionChannel(
        listOf(versionName, fileUrl, sourceUrl).joinToString(" ")
    ) ?: GitHubReleaseChannel.STABLE
}

private fun GitHubReleaseAssetFile.releaseChannel(): GitHubReleaseChannel {
    return GitHubVersionUtils.classifyVersionChannel(
        listOf(name, downloadUrl).joinToString(" ")
    ) ?: GitHubReleaseChannel.STABLE
}
