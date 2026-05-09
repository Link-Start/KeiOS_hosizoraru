package os.kei.feature.github.domain

import android.content.Context
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubAtomReleaseStrategy
import os.kei.feature.github.data.remote.GitHubReleaseLookupStrategy
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.data.remote.GitHubRepositoryProfileRepository
import os.kei.feature.github.data.remote.GitHubRepositoryProfileRequest
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.defaultRepositoryProfilePurpose
import os.kei.feature.github.model.githubCheckSourceSignature
import java.io.IOException

object GitHubReleaseCheckService {
    private const val transientRetryCount = 1

    suspend fun evaluateTrackedApp(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy? = null,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): GitHubTrackedReleaseCheck {
        return evaluateTrackedAppInternal(
            context = context,
            item = item,
            strategy = strategy,
            preciseApkVersionResolver = GitHubPreciseApkVersionResolver(),
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh
        )
    }

    fun evaluateTrackedAppBlocking(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy? = null,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): GitHubTrackedReleaseCheck {
        return GitHubExecution.runBlockingIo {
            evaluateTrackedApp(
                context = context,
                item = item,
                strategy = strategy,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh
            )
        }
    }

    internal suspend fun evaluateTrackedAppForTest(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy? = null,
        preciseApkVersionResolver: GitHubPreciseApkVersionResolver = GitHubPreciseApkVersionResolver(),
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): GitHubTrackedReleaseCheck {
        return evaluateTrackedAppInternal(
            context = context,
            item = item,
            strategy = strategy,
            preciseApkVersionResolver = preciseApkVersionResolver,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh
        )
    }

    private suspend fun evaluateTrackedAppInternal(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy?,
        preciseApkVersionResolver: GitHubPreciseApkVersionResolver,
        profilePurposeOverride: GitHubRepositoryProfilePurpose?,
        forceRefresh: Boolean
    ): GitHubTrackedReleaseCheck {
        val lookupConfig = GitHubReleaseStrategyRegistry.loadLookupConfig()
        val sourceConfigSignature = lookupConfig.githubCheckSourceSignature()
        val localVersionInfo = runCatching {
            GitHubVersionUtils.localVersionInfoOrNull(context, item.packageName)
        }.getOrNull()
        val localVersion = localVersionInfo?.versionName.orEmpty()
        val localVersionCode = localVersionInfo?.versionCode ?: -1L
        val profileRepository = GitHubRepositoryProfileRepository()
        val effectiveStrategy = strategy ?: GitHubReleaseStrategyRegistry.resolveConfiguredStrategy().getOrElse { error ->
            val profile = loadRepositoryProfile(
                profileRepository = profileRepository,
                item = item,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                purpose = profilePurposeOverride ?: GitHubRepositoryProfilePurpose.VersionCheckFast
            )
            return GitHubTrackedReleaseCheck(
                strategyId = lookupConfig.selectedStrategy.storageId,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                repositoryArchived = profile.repositoryArchivedOr(item.repositoryArchived),
                repositoryFork = profile.repositoryForkOr(item.repositoryFork),
                repositoryPushedAtMillis = profile.repositoryPushedAtOr(-1L),
                upstreamFullName = profile.upstreamFullNameOr(""),
                upstreamArchived = profile.upstreamArchivedOr(false),
                upstreamPushedAtMillis = profile.upstreamPushedAtOr(-1L),
                repositoryProfile = profile,
                sourceConfigSignature = sourceConfigSignature,
                status = GitHubTrackedReleaseStatus.Failed,
                message = GitHubTrackedReleaseStatus.Failed.failureMessage(error.message ?: "unknown")
            )
        }

        val snapshot = loadSnapshotWithFallback(
            owner = item.owner,
            repo = item.repo,
            strategy = effectiveStrategy,
            lookupConfig = lookupConfig,
            allowFallback = strategy == null,
            forceRefresh = forceRefresh
        ).getOrElse { error ->
            val profile = loadRepositoryProfile(
                profileRepository = profileRepository,
                item = item,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                purpose = profilePurposeOverride ?: GitHubRepositoryProfilePurpose.VersionCheckFast
            )
            return GitHubTrackedReleaseCheck(
                strategyId = effectiveStrategy.id,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                repositoryArchived = profile.repositoryArchivedOr(item.repositoryArchived),
                repositoryFork = profile.repositoryForkOr(item.repositoryFork),
                repositoryPushedAtMillis = profile.repositoryPushedAtOr(-1L),
                upstreamFullName = profile.upstreamFullNameOr(""),
                upstreamArchived = profile.upstreamArchivedOr(false),
                upstreamPushedAtMillis = profile.upstreamPushedAtOr(-1L),
                repositoryProfile = profile,
                sourceConfigSignature = sourceConfigSignature,
                status = GitHubTrackedReleaseStatus.Failed,
                message = GitHubTrackedReleaseStatus.Failed.failureMessage(error.message ?: "unknown")
            )
        }
        val preciseVersions = resolvePreciseApkVersions(
            item = item,
            localVersion = localVersion,
            snapshot = snapshot,
            lookupConfig = lookupConfig,
            resolver = preciseApkVersionResolver
        )
        val profile = loadRepositoryProfile(
            profileRepository = profileRepository,
            item = item,
            lookupConfig = lookupConfig,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            purpose = profilePurposeOverride ?: lookupConfig.defaultRepositoryProfilePurpose(),
            releaseSnapshot = snapshot,
            preciseStableApkVersion = preciseVersions.stable,
            precisePreReleaseApkVersion = preciseVersions.preRelease
        )

        return evaluateSnapshot(
            item = item,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            snapshot = snapshot.copy(
                repositoryArchived = profile.repositoryArchivedOr(item.repositoryArchived),
                repositoryFork = profile.repositoryForkOr(item.repositoryFork),
                repositoryPushedAtMillis = profile.repositoryPushedAtOr(-1L),
                upstreamFullName = profile.upstreamFullNameOr(""),
                upstreamArchived = profile.upstreamArchivedOr(false),
                upstreamPushedAtMillis = profile.upstreamPushedAtOr(-1L)
            ),
            checkAllTrackedPreReleases = lookupConfig.checkAllTrackedPreReleases,
            preciseStableApkVersion = preciseVersions.stable,
            precisePreReleaseApkVersion = preciseVersions.preRelease,
            sourceConfigSignature = sourceConfigSignature,
            repositoryProfile = profile
        )
    }

    internal fun evaluateSnapshot(
        item: GitHubTrackedApp,
        localVersion: String,
        localVersionCode: Long,
        snapshot: GitHubRepositoryReleaseSnapshot,
        checkAllTrackedPreReleases: Boolean = false,
        preciseStableApkVersion: GitHubRemoteApkVersionInfo? = null,
        precisePreReleaseApkVersion: GitHubRemoteApkVersionInfo? = null,
        sourceConfigSignature: String = "",
        repositoryProfile: GitHubRepositoryProfileSnapshot? = snapshot.repositoryProfile
    ): GitHubTrackedReleaseCheck {
        val matchedEntry = snapshot.feed.entries.firstOrNull { entry ->
            GitHubVersionUtils.compareVersionToStructuredCandidates(localVersion, entry.versionCandidates) == 0
        }
        val matchedCurrentStable = snapshot.hasStableRelease &&
            matchedEntry != null &&
            GitHubVersionUtils.compareCandidateSetsWithSources(
                matchedEntry.versionCandidates.map { it.value },
                snapshot.latestStable.versionCandidates
            ) == 0
        val latestStable = snapshot.latestStable.takeIf { snapshot.hasStableRelease }
        val latestPre = snapshot.latestPreRelease
        val hasOnlyPreReleases = !snapshot.hasStableRelease && latestPre != null
        val localChannel = when {
            matchedCurrentStable -> GitHubReleaseChannel.STABLE
            else -> matchedEntry?.channel
        }
            ?: GitHubVersionUtils.classifyVersionChannel(localVersion)
            ?: GitHubReleaseChannel.UNKNOWN
        val isLocalPreReleaseInstalled =
            (matchedEntry?.isLikelyPreRelease == true && !matchedCurrentStable) || localChannel.isPreRelease
        val inspectPreRelease = checkAllTrackedPreReleases || item.preferPreRelease || isLocalPreReleaseInstalled

        val stableCmp = latestStable?.let {
            GitHubVersionUtils.compareVersionToStructuredCandidates(localVersion, it.versionCandidates)
        }
        val stableTagMatchesLocalNameAndCode = latestStable?.let {
            GitHubVersionUtils.remoteCandidateMatchesLocalVersionNameAndCode(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                remoteCandidates = it.versionCandidates
            )
        } == true
        val latestPreIsRelevant = when {
            latestPre == null -> false
            latestStable == null -> true
            else -> GitHubVersionUtils.isRelevantPreRelease(
                preReleaseCandidates = latestPre.versionCandidates,
                stableCandidates = latestStable.versionCandidates,
                preReleaseUpdatedAtMillis = latestPre.updatedAtMillis,
                stableUpdatedAtMillis = latestStable.updatedAtMillis
            )
        }
        val latestPreCmp = if (latestPre != null) {
            GitHubVersionUtils.compareVersionToStructuredCandidates(localVersion, latestPre.versionCandidates)
        } else {
            null
        }
        val preTagMatchesLocalNameAndCode = latestPre?.let {
            GitHubVersionUtils.remoteCandidateMatchesLocalVersionNameAndCode(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                remoteCandidates = it.versionCandidates
            )
        } == true

        val preciseStableCmp = preciseStableApkVersion
            ?.versionCodeLong
            ?.takeIf { localVersionCode >= 0L }
            ?.compareTo(localVersionCode)
        val precisePreCmp = precisePreReleaseApkVersion
            ?.versionCodeLong
            ?.takeIf { localVersionCode >= 0L }
            ?.compareTo(localVersionCode)
        val hasPreReleaseUpdate = inspectPreRelease &&
            latestPreIsRelevant &&
                (precisePreCmp?.let { it > 0 }
                    ?: (!preTagMatchesLocalNameAndCode && latestPreCmp?.let { it < 0 } == true))
        val stableHasUpdate = preciseStableCmp?.let { it > 0 }
            ?: (!stableTagMatchesLocalNameAndCode && stableCmp?.let { it < 0 } == true)
        val recommendsPreRelease = hasPreReleaseUpdate &&
            (item.preferPreRelease || (isLocalPreReleaseInstalled && !stableHasUpdate))
        val hasUpdate = stableHasUpdate || recommendsPreRelease

        val preReleaseInfo = when {
            inspectPreRelease && latestPre != null -> latestPre.displayVersion
            inspectPreRelease && isLocalPreReleaseInstalled && matchedEntry != null -> matchedEntry.displayVersion
            else -> ""
        }
        val showPreReleaseInfo = inspectPreRelease && preReleaseInfo.isNotBlank()
        val releaseHint = when {
            hasOnlyPreReleases && !inspectPreRelease -> GitHubTrackedReleaseStatus.ONLY_PRERELEASES_HINT_MESSAGE
            else -> ""
        }

        val stableCompared = stableCmp != null || preciseStableCmp != null
        val status = when {
            recommendsPreRelease -> GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable
            stableHasUpdate -> GitHubTrackedReleaseStatus.UpdateAvailable
            hasPreReleaseUpdate -> GitHubTrackedReleaseStatus.PreReleaseOptional
            inspectPreRelease && isLocalPreReleaseInstalled -> GitHubTrackedReleaseStatus.PreReleaseTracked
            stableCompared && hasUpdate == false -> GitHubTrackedReleaseStatus.UpToDate
            matchedEntry != null -> GitHubTrackedReleaseStatus.MatchedRelease
            else -> GitHubTrackedReleaseStatus.ComparisonUncertain
        }

        return GitHubTrackedReleaseCheck(
            strategyId = snapshot.strategyId,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            matchedRelease = matchedEntry,
            stableRelease = latestStable,
            preRelease = latestPre,
            hasStableRelease = snapshot.hasStableRelease,
            hasUpdate = hasUpdate,
            hasPreReleaseUpdate = hasPreReleaseUpdate,
            recommendsPreRelease = recommendsPreRelease,
            isPreReleaseInstalled = isLocalPreReleaseInstalled,
            preReleaseInfo = preReleaseInfo,
            showPreReleaseInfo = showPreReleaseInfo,
            releaseHint = releaseHint,
            preciseStableApkVersion = preciseStableApkVersion,
            precisePreApkVersion = precisePreReleaseApkVersion,
            repositoryArchived = snapshot.repositoryArchived,
            repositoryFork = snapshot.repositoryFork,
            repositoryPushedAtMillis = snapshot.repositoryPushedAtMillis,
            upstreamFullName = snapshot.upstreamFullName,
            upstreamArchived = snapshot.upstreamArchived,
            upstreamPushedAtMillis = snapshot.upstreamPushedAtMillis,
            repositoryProfile = repositoryProfile,
            sourceConfigSignature = sourceConfigSignature,
            status = status,
            message = status.defaultMessage
        )
    }

    private suspend fun resolvePreciseApkVersions(
        item: GitHubTrackedApp,
        localVersion: String,
        snapshot: GitHubRepositoryReleaseSnapshot,
        lookupConfig: GitHubLookupConfig,
        resolver: GitHubPreciseApkVersionResolver
    ): PreciseApkVersionPair {
        if (!lookupConfig.preciseApkVersionEnabled) return PreciseApkVersionPair()
        val localChannel = GitHubVersionUtils.classifyVersionChannel(localVersion)
        val targets = buildList {
            snapshot.latestStable.takeIf { snapshot.hasStableRelease }?.let { release ->
                add(PreciseApkVersionTarget(PreciseApkVersionChannel.Stable, release))
            }
            val shouldInspectPreRelease = lookupConfig.checkAllTrackedPreReleases ||
                    item.preferPreRelease ||
                    localChannel?.isPreRelease == true ||
                    !snapshot.hasStableRelease
            if (shouldInspectPreRelease) {
                snapshot.latestPreRelease?.let { release ->
                    add(PreciseApkVersionTarget(PreciseApkVersionChannel.PreRelease, release))
                }
            }
        }
        if (targets.isEmpty()) return PreciseApkVersionPair()
        val results = GitHubExecution.mapOrderedBounded(
            items = targets,
            maxConcurrency = 2
        ) { target ->
            target.channel to resolver.resolve(
                GitHubPreciseApkVersionRequest(
                    owner = item.owner,
                    repo = item.repo,
                    release = target.release,
                    packageName = item.packageName,
                    lookupConfig = lookupConfig
                )
            ).getOrNull()
        }
        return PreciseApkVersionPair(
            stable = results.firstOrNull { it.first == PreciseApkVersionChannel.Stable }?.second,
            preRelease = results.firstOrNull { it.first == PreciseApkVersionChannel.PreRelease }?.second
        )
    }

    private suspend fun loadRepositoryProfile(
        profileRepository: GitHubRepositoryProfileRepository,
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        localVersion: String,
        localVersionCode: Long,
        purpose: GitHubRepositoryProfilePurpose = GitHubRepositoryProfilePurpose.VersionCheckFast,
        releaseSnapshot: GitHubRepositoryReleaseSnapshot? = null,
        preciseStableApkVersion: GitHubRemoteApkVersionInfo? = null,
        precisePreReleaseApkVersion: GitHubRemoteApkVersionInfo? = null
    ): GitHubRepositoryProfileSnapshot? {
        return runCatching {
            profileRepository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = item.owner,
                    repo = item.repo,
                    lookupConfig = lookupConfig,
                    purpose = purpose,
                    releaseSnapshot = releaseSnapshot,
                    localPackageName = item.packageName,
                    localVersionName = localVersion,
                    localVersionCode = localVersionCode,
                    preciseStableApkVersion = preciseStableApkVersion,
                    precisePreReleaseApkVersion = precisePreReleaseApkVersion
                )
            )
        }.getOrNull()
    }

    private suspend fun loadSnapshotWithFallback(
        owner: String,
        repo: String,
        strategy: GitHubReleaseLookupStrategy,
        lookupConfig: GitHubLookupConfig,
        allowFallback: Boolean,
        forceRefresh: Boolean = false
    ): Result<GitHubRepositoryReleaseSnapshot> {
        val primaryResult = loadSnapshotWithTransientRetry(
            strategy = strategy,
            owner = owner,
            repo = repo,
            forceRefresh = forceRefresh
        )
        if (primaryResult.isSuccess) return primaryResult

        val primaryError = primaryResult.exceptionOrNull() ?: IllegalStateException("unknown")
        val fallbackStrategy = if (allowFallback && primaryError.shouldTryStrategyFallback()) {
            resolveFallbackStrategy(
                primaryStrategyId = strategy.id,
                lookupConfig = lookupConfig
            )
        } else {
            null
        } ?: return primaryResult

        val fallbackResult = loadSnapshotWithTransientRetry(
            strategy = fallbackStrategy,
            owner = owner,
            repo = repo,
            forceRefresh = forceRefresh
        )
        if (fallbackResult.isSuccess) return fallbackResult

        val fallbackError = fallbackResult.exceptionOrNull()
        val message = buildString {
            append("Primary strategy failed(")
            append(strategy.id)
            append("): ")
            append(primaryError.message ?: "unknown")
            append("; fallback strategy failed(")
            append(fallbackStrategy.id)
            append("): ")
            append(fallbackError?.message ?: "unknown")
        }
        return Result.failure(
            IllegalStateException(message, fallbackError ?: primaryError)
        )
    }

    private suspend fun loadSnapshotWithTransientRetry(
        strategy: GitHubReleaseLookupStrategy,
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<GitHubRepositoryReleaseSnapshot> {
        if (forceRefresh) {
            strategy.clearCaches()
        }
        var latestResult = strategy.loadSnapshot(owner, repo)
        if (latestResult.isSuccess) return latestResult

        repeat(transientRetryCount) {
            val error = latestResult.exceptionOrNull() ?: return latestResult
            if (!error.shouldTryStrategyFallback()) {
                return latestResult
            }
            strategy.clearCaches()
            latestResult = strategy.loadSnapshot(owner, repo)
            if (latestResult.isSuccess) return latestResult
        }
        return latestResult
    }

    private fun resolveFallbackStrategy(
        primaryStrategyId: String,
        lookupConfig: GitHubLookupConfig
    ): GitHubReleaseLookupStrategy? {
        return when (primaryStrategyId) {
            GitHubLookupStrategyOption.AtomFeed.storageId -> {
                val token = lookupConfig.apiToken.trim()
                if (token.isBlank()) {
                    null
                } else {
                    GitHubApiTokenReleaseStrategy(apiToken = token)
                }
            }

            GitHubLookupStrategyOption.GitHubApiToken.storageId -> {
                GitHubAtomReleaseStrategy
            }

            else -> null
        }
    }

    private fun Throwable.shouldTryStrategyFallback(): Boolean {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < 8) {
            val message = current.message.orEmpty().lowercase()
            if (
                message.contains("http 5") ||
                message.contains("http 429") ||
                message.contains("timeout") ||
                message.contains("timed out") ||
                message.contains("connection reset") ||
                message.contains("connection closed") ||
                message.contains("failed to connect") ||
                message.contains("unable to resolve host") ||
                message.contains("network")
            ) {
                return true
            }
            if (current is IOException) return true
            current = current.cause
            depth += 1
        }
        return false
    }

    fun GitHubTrackedReleaseCheck.toCacheEntry(): GitHubCheckCacheEntry {
        return GitHubCheckCacheEntry(
            loading = false,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            latestTag = stableRelease?.displayVersion.orEmpty(),
            latestStableName = stableRelease?.rawName.orEmpty(),
            latestStableRawTag = stableRelease?.rawTag.orEmpty(),
            latestStableUrl = stableRelease?.link.orEmpty(),
            latestStableUpdatedAtMillis = stableRelease?.updatedAtMillis ?: -1L,
            latestPreName = preRelease?.rawName.orEmpty(),
            latestPreRawTag = preRelease?.rawTag.orEmpty(),
            latestPreUrl = preRelease?.link.orEmpty(),
            latestPreUpdatedAtMillis = preRelease?.updatedAtMillis ?: -1L,
            hasStableRelease = hasStableRelease,
            hasUpdate = hasUpdate,
            message = message,
            isPreRelease = isPreReleaseInstalled,
            preReleaseInfo = preReleaseInfo,
            showPreReleaseInfo = showPreReleaseInfo,
            hasPreReleaseUpdate = hasPreReleaseUpdate,
            recommendsPreRelease = recommendsPreRelease,
            releaseHint = releaseHint,
            latestStableApkVersion = preciseStableApkVersion,
            latestPreApkVersion = precisePreApkVersion,
            repositoryArchived = repositoryArchived,
            repositoryFork = repositoryFork,
            repositoryPushedAtMillis = repositoryPushedAtMillis,
            upstreamFullName = upstreamFullName,
            upstreamArchived = upstreamArchived,
            upstreamPushedAtMillis = upstreamPushedAtMillis,
            repositoryProfile = repositoryProfile,
            sourceConfigSignature = sourceConfigSignature,
            sourceStrategyId = strategyId
        )
    }

    fun fromCacheEntry(entry: GitHubCheckCacheEntry): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = entry.sourceStrategyId.ifBlank { GitHubAtomReleaseStrategy.id },
            localVersion = entry.localVersion,
            localVersionCode = entry.localVersionCode,
            stableRelease = entry
                .takeIf {
                    it.hasStableRelease &&
                        (it.latestStableRawTag.isNotBlank() || it.latestStableName.isNotBlank() || it.latestTag.isNotBlank())
                }
                ?.let {
                    GitHubReleaseVersionSignals(
                        displayVersion = it.latestStableName.ifBlank { it.latestTag.ifBlank { it.latestStableRawTag } },
                        rawTag = it.latestStableRawTag.ifBlank { it.latestTag },
                        rawName = it.latestStableName.ifBlank { it.latestTag.ifBlank { it.latestStableRawTag } },
                        link = entry.latestStableUrl,
                        updatedAtMillis = entry.latestStableUpdatedAtMillis.takeIf { ts -> ts > 0L }
                    )
            },
            preRelease = entry
                .takeIf { it.latestPreRawTag.isNotBlank() || it.latestPreName.isNotBlank() || it.preReleaseInfo.isNotBlank() }
                ?.let {
                    GitHubReleaseVersionSignals(
                        displayVersion = it.latestPreName.ifBlank { it.preReleaseInfo.ifBlank { it.latestPreRawTag } },
                        rawTag = it.latestPreRawTag.ifBlank { it.preReleaseInfo },
                        rawName = it.latestPreName.ifBlank { it.preReleaseInfo.ifBlank { it.latestPreRawTag } },
                        link = entry.latestPreUrl,
                        updatedAtMillis = entry.latestPreUpdatedAtMillis.takeIf { ts -> ts > 0L }
                    )
            },
            hasStableRelease = entry.hasStableRelease,
            hasUpdate = entry.hasUpdate,
            hasPreReleaseUpdate = entry.hasPreReleaseUpdate,
            recommendsPreRelease = entry.recommendsPreRelease ||
                GitHubTrackedReleaseStatus.fromMessage(entry.message) ==
                GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable,
            isPreReleaseInstalled = entry.isPreRelease,
            preReleaseInfo = entry.preReleaseInfo,
            showPreReleaseInfo = entry.showPreReleaseInfo,
            releaseHint = entry.releaseHint,
            preciseStableApkVersion = entry.latestStableApkVersion,
            precisePreApkVersion = entry.latestPreApkVersion,
            repositoryArchived = entry.repositoryArchived,
            repositoryFork = entry.repositoryFork,
            repositoryPushedAtMillis = entry.repositoryPushedAtMillis,
            upstreamFullName = entry.upstreamFullName,
            upstreamArchived = entry.upstreamArchived,
            upstreamPushedAtMillis = entry.upstreamPushedAtMillis,
            repositoryProfile = entry.repositoryProfile,
            sourceConfigSignature = entry.sourceConfigSignature,
            status = GitHubTrackedReleaseStatus.fromMessage(entry.message)
                ?: GitHubTrackedReleaseStatus.ComparisonUncertain,
            message = entry.message
        )
    }

    private data class PreciseApkVersionPair(
        val stable: GitHubRemoteApkVersionInfo? = null,
        val preRelease: GitHubRemoteApkVersionInfo? = null
    )

    private enum class PreciseApkVersionChannel {
        Stable,
        PreRelease
    }

    private data class PreciseApkVersionTarget(
        val channel: PreciseApkVersionChannel,
        val release: GitHubReleaseVersionSignals
    )

    private fun GitHubRepositoryProfileSnapshot?.repositoryArchivedOr(fallback: Boolean): Boolean {
        return this?.lifecycle?.archived?.value ?: fallback
    }

    private fun GitHubRepositoryProfileSnapshot?.repositoryForkOr(fallback: Boolean): Boolean {
        return this?.lifecycle?.fork?.value ?: fallback
    }

    private fun GitHubRepositoryProfileSnapshot?.repositoryPushedAtOr(fallback: Long): Long {
        return this?.activity?.pushedAtMillis?.value ?: fallback
    }

    private fun GitHubRepositoryProfileSnapshot?.upstreamFullNameOr(fallback: String): String {
        return this?.lifecycle?.upstream?.fullName?.value ?: fallback
    }

    private fun GitHubRepositoryProfileSnapshot?.upstreamArchivedOr(fallback: Boolean): Boolean {
        return this?.lifecycle?.upstream?.archived?.value ?: fallback
    }

    private fun GitHubRepositoryProfileSnapshot?.upstreamPushedAtOr(fallback: Long): Long {
        return this?.lifecycle?.upstream?.pushedAtMillis?.value ?: fallback
    }
}
