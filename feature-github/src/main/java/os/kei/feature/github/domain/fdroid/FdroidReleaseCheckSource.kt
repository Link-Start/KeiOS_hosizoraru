package os.kei.feature.github.domain.fdroid

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecarStore
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecarWriter
import os.kei.feature.github.data.local.fdroid.buildFdroidMetadataSidecar
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.data.remote.fdroid.FdroidPackageApiClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.domain.GitHubRefreshFailureClassifier
import os.kei.feature.github.model.GITHUB_FDROID_STRATEGY_ID
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubRefreshFailureDiagnostics
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity
import os.kei.feature.github.model.fdroidRepositoryCheckSourceSignature

fun interface FdroidReleaseCheckEvaluator {
    suspend fun evaluate(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        localVersion: String,
        localVersionCode: Long,
        forceRefresh: Boolean
    ): GitHubTrackedReleaseCheck
}

fun interface FdroidPackageSnapshotProvider {
    suspend fun loadPackageSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageSnapshot>
}

data class FdroidPackageLookupSnapshot(
    val packageSnapshot: FdroidPackageSnapshot,
    val repositorySnapshot: FdroidRepositorySnapshot? = null
)

fun interface FdroidPackageLookupSnapshotProvider {
    suspend fun loadPackageLookupSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot>
}

class FdroidPackageApiSnapshotProvider(
    private val client: FdroidPackageApiClient = FdroidPackageApiClient()
) : FdroidPackageSnapshotProvider {
    override suspend fun loadPackageSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageSnapshot> {
        val identity = buildFdroidRepositoryTrackIdentity(item.repoUrl, item.packageName)
            ?: return Result.failure(IllegalArgumentException("invalid F-Droid repository URL or package"))
        return client.fetchPackage(
            repoBaseUrl = identity.normalizedRepoUrl,
            packageName = identity.packageName
        )
    }
}

class FdroidReleaseCheckSource(
    private val snapshotProvider: FdroidPackageSnapshotProvider = FdroidPackageApiSnapshotProvider(),
    private val metadataWriter: FdroidMetadataSidecarWriter = FdroidMetadataSidecarStore,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val deviceSdkProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : FdroidReleaseCheckEvaluator {
    override suspend fun evaluate(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        localVersion: String,
        localVersionCode: Long,
        forceRefresh: Boolean
    ): GitHubTrackedReleaseCheck = withContext(ioDispatcher) {
        val sourceConfigSignature = item.fdroidRepositoryCheckSourceSignature()
        val identity = buildFdroidRepositoryTrackIdentity(item.repoUrl, item.packageName)
            ?: return@withContext failedCheck(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                sourceConfigSignature = sourceConfigSignature,
                detail = "invalid F-Droid repository URL or package"
            )
        val lookupSnapshot = snapshotProvider
            .loadLookupSnapshot(item, forceRefresh)
            .getOrElse { error ->
                return@withContext failedCheck(
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    sourceConfigSignature = sourceConfigSignature,
                    detail = error.message.orEmpty().ifBlank { "F-Droid package lookup failed" },
                    error = error,
                )
            }
        val repositorySnapshot = lookupSnapshot.repositorySnapshot
        val packageSnapshot = lookupSnapshot.packageSnapshot
        val remotePackage = packageSnapshot.packageName.trim()
        if (remotePackage.isNotBlank() && !remotePackage.equals(identity.packageName, ignoreCase = true)) {
            return@withContext failedCheck(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                sourceConfigSignature = sourceConfigSignature,
                detail = "remote package $remotePackage does not match ${identity.packageName}"
            )
        }
        val deviceSdk = deviceSdkProvider()
        val stableVersion = packageSnapshot
            .withVersions(packageSnapshot.versions.filter { !it.releaseChannel().isPreRelease })
            .selectCandidate(item, deviceSdk)
        val shouldInspectPreRelease = lookupConfig.checkAllTrackedPreReleases ||
            item.preferPreRelease ||
            GitHubVersionUtils.classifyVersionChannel(localVersion)?.isPreRelease == true
        val preReleaseVersion = if (shouldInspectPreRelease) {
            packageSnapshot
                .withVersions(packageSnapshot.versions.filter { it.releaseChannel().isPreRelease })
                .selectCandidate(item, deviceSdk)
        } else {
            null
        }
        if (stableVersion == null && preReleaseVersion == null) {
            return@withContext failedCheck(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                sourceConfigSignature = sourceConfigSignature,
                detail = "no compatible F-Droid version found"
            )
        }
        val link = item.fdroidConfig.packagePageUrl
            .ifBlank { identity.packagePageUrl }
            .ifBlank { item.repoUrl }
        val stablePreciseInfo = stableVersion?.toRemoteApkVersionInfo(item, packageSnapshot, link)
        val preReleasePreciseInfo = preReleaseVersion?.toRemoteApkVersionInfo(item, packageSnapshot, link)
        val stableSignal = stableVersion?.toReleaseSignal(
            item = item,
            packageSnapshot = packageSnapshot,
            preciseInfo = requireNotNull(stablePreciseInfo),
            link = link,
            channel = GitHubReleaseChannel.STABLE
        )
        val preReleaseSignal = preReleaseVersion?.toReleaseSignal(
            item = item,
            packageSnapshot = packageSnapshot,
            preciseInfo = requireNotNull(preReleasePreciseInfo),
            link = link,
            channel = preReleaseVersion.releaseChannel()
        )
        val fallbackSignal = stableSignal ?: requireNotNull(preReleaseSignal)
        val selectedVersion = stableVersion ?: preReleaseVersion
        selectedVersion?.let { version ->
            metadataWriter.save(
                buildFdroidMetadataSidecar(
                    trackId = item.id,
                    sourceConfigSignature = sourceConfigSignature,
                    fetchedAtMillis = clock(),
                    repositorySnapshot = repositorySnapshot,
                    packageSnapshot = packageSnapshot,
                    selectedVersion = version,
                    trustPolicy = item.fdroidConfig.trustPolicy,
                    repoFingerprint = item.fdroidConfig.repoFingerprint
                )
            )
        }
        selectedVersion?.trustFailureDetail(item.fdroidConfig.trustPolicy, item.fdroidConfig.repoFingerprint)?.let { detail ->
            return@withContext failedCheck(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                sourceConfigSignature = sourceConfigSignature,
                detail = detail
            )
        }
        val snapshot = GitHubRepositoryReleaseSnapshot(
            strategyId = GITHUB_FDROID_STRATEGY_ID,
            feed = GitHubAtomFeed(
                title = packageSnapshot.appName.ifBlank { item.appLabel },
                feedUrl = item.repoUrl,
                updatedAtMillis = packageSnapshot.versions.firstNotNullOfOrNull { it.addedAtMillis },
                entries = listOfNotNull(
                    stableSignal?.toAtomEntry(stableSignal.channel),
                    preReleaseSignal?.toAtomEntry(preReleaseSignal.channel)
                )
            ),
            latestStable = fallbackSignal,
            hasStableRelease = stableSignal != null,
            latestPreRelease = preReleaseSignal
        )
        GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            snapshot = snapshot,
            checkAllTrackedPreReleases = shouldInspectPreRelease,
            preciseStableApkVersion = stablePreciseInfo,
            precisePreReleaseApkVersion = preReleasePreciseInfo,
            sourceConfigSignature = sourceConfigSignature
        )
    }

    private suspend fun FdroidPackageSnapshotProvider.loadLookupSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot> {
        return if (this is FdroidPackageLookupSnapshotProvider) {
            loadPackageLookupSnapshot(item, forceRefresh)
        } else {
            loadPackageSnapshot(item, forceRefresh).map { snapshot ->
                FdroidPackageLookupSnapshot(packageSnapshot = snapshot)
            }
        }
    }

    private fun FdroidPackageSnapshot.selectCandidate(
        item: GitHubTrackedApp,
        deviceSdk: Int
    ): FdroidVersionSnapshot? {
        if (versions.isEmpty()) return null
        return FdroidCandidateSelector.select(
            snapshot = this,
            config = item.fdroidConfig,
            deviceSdk = deviceSdk
        )
    }

    private fun FdroidPackageSnapshot.withVersions(
        filteredVersions: List<FdroidVersionSnapshot>
    ): FdroidPackageSnapshot {
        return copy(versions = filteredVersions)
    }

    private fun FdroidVersionSnapshot.toRemoteApkVersionInfo(
        item: GitHubTrackedApp,
        packageSnapshot: FdroidPackageSnapshot,
        link: String
    ): GitHubRemoteApkVersionInfo {
        return GitHubRemoteApkVersionInfo(
            releaseName = packageSnapshot.appName.ifBlank { item.appLabel },
            releaseTag = versionName.ifBlank { versionCode.toString() },
            releaseUrl = link,
            assetName = apkName,
            packageName = packageSnapshot.packageName.ifBlank { item.packageName },
            versionName = versionName,
            versionCode = versionCode.toString(),
            fetchSource = apkPath.ifBlank { link },
            releaseNotes = whatsNew
        )
    }

    private fun FdroidVersionSnapshot.toReleaseSignal(
        item: GitHubTrackedApp,
        packageSnapshot: FdroidPackageSnapshot,
        preciseInfo: GitHubRemoteApkVersionInfo,
        link: String,
        channel: GitHubReleaseChannel
    ): GitHubReleaseVersionSignals {
        val displayVersion = preciseInfo.versionLabel()
            .ifBlank { preciseInfo.releaseLabel() }
            .ifBlank { apkName }
        return GitHubReleaseVersionSignals(
            displayVersion = displayVersion,
            rawTag = versionName.ifBlank { versionCode.toString() },
            rawName = packageSnapshot.appName.ifBlank { item.appLabel },
            link = link,
            updatedAtMillis = addedAtMillis,
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to versionName,
                GitHubVersionCandidateSource.Title to versionCode.toString(),
                GitHubVersionCandidateSource.Link to apkPath
            ),
            source = GitHubReleaseSignalSource.AtomFallback,
            channel = channel
        )
    }

    private fun GitHubReleaseVersionSignals.toAtomEntry(
        channel: GitHubReleaseChannel
    ): GitHubAtomReleaseEntry {
        return GitHubAtomReleaseEntry(
            entryId = link.ifBlank { rawTag },
            tag = rawTag,
            title = displayVersion,
            link = link,
            updatedAtMillis = updatedAtMillis,
            versionCandidates = versionCandidates,
            channel = channel,
            isLikelyPreRelease = channel.isPreRelease
        )
    }

    private fun FdroidVersionSnapshot.releaseChannel(): GitHubReleaseChannel =
        fdroidReleaseChannelOf(releaseChannels = releaseChannels, versionName = versionName)

    private fun FdroidVersionSnapshot.trustFailureDetail(
        trustPolicy: FdroidTrustPolicy,
        repoFingerprint: String
    ): String? {
        return when (trustPolicy) {
            FdroidTrustPolicy.TrackOnlyWarn -> null
            FdroidTrustPolicy.RequireRepoFingerprint ->
                if (repoFingerprint.trim().isBlank()) {
                    "F-Droid repository fingerprint is required but missing"
                } else {
                    null
                }

            FdroidTrustPolicy.RequireApkHash ->
                if (apkSha256.trim().isBlank()) {
                    "F-Droid APK hash is required but missing"
                } else {
                    null
                }

            FdroidTrustPolicy.RequireOfficialSignerIndex ->
                if (signerSha256.isEmpty()) {
                    "F-Droid signer index is required but missing"
                } else {
                    null
                }
        }
    }

    private fun failedCheck(
        localVersion: String,
        localVersionCode: Long,
        sourceConfigSignature: String,
        detail: String,
        error: Throwable? = null,
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = GITHUB_FDROID_STRATEGY_ID,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            sourceConfigSignature = sourceConfigSignature,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(detail),
            failureDiagnostics = error?.let { failure ->
                GitHubRefreshFailureClassifier.from(
                    error = failure,
                    responseType = "fdroid_package_api",
                )
            } ?: GitHubRefreshFailureDiagnostics(),
        )
    }
}
