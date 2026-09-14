package os.kei.feature.github.domain

import android.content.Context
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubInstalledAppRepository
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubAtomReleaseStrategy
import os.kei.feature.github.data.remote.GitHubReleaseLookupStrategy
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.data.remote.GitHubRepositoryProfileRepository
import os.kei.feature.github.data.remote.GitHubRepositoryProfileRequest
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.data.remote.GitRepositoryReleaseStrategy
import os.kei.feature.github.domain.fdroid.FdroidReleaseCheckEvaluator
import os.kei.feature.github.domain.fdroid.FdroidReleaseCheckSource
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationEngine
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationPolicy
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubReleaseCheckDiagnostics
import os.kei.feature.github.model.GitHubRefreshFailureDiagnostics
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPreciseApkOutcome
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.GitRepositoryTrackIdentity
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.checkSourceSignature
import os.kei.feature.github.model.defaultRepositoryProfilePurpose
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.githubReleaseLookupItemOrNull
import os.kei.feature.github.model.githubProfileSourceSignature
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isFdroidRepositoryTrack
import os.kei.feature.github.model.isGitRepositoryTrack
import os.kei.feature.github.model.requiredCapabilities
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

object GitHubReleaseCheckService {
    private const val transientRetryCount = 1

    suspend fun evaluateTrackedApp(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy? = null,
        lookupConfigOverride: GitHubLookupConfig? = null,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        existingRepositoryProfile: GitHubRepositoryProfileSnapshot? = null,
        fdroidReleaseCheckSource: FdroidReleaseCheckEvaluator = FdroidReleaseCheckSource()
    ): GitHubTrackedReleaseCheck {
        return evaluateTrackedAppInternal(
            context = context,
            item = item,
            strategy = strategy,
            preciseApkVersionResolver = GitHubPreciseApkVersionResolver(),
            fdroidReleaseCheckSource = fdroidReleaseCheckSource,
            lookupConfigOverride = lookupConfigOverride,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh,
            existingRepositoryProfile = existingRepositoryProfile
        )
    }


    internal suspend fun evaluateTrackedAppForTest(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy? = null,
        preciseApkVersionResolver: GitHubPreciseApkVersionResolver = GitHubPreciseApkVersionResolver(),
        fdroidReleaseCheckSource: FdroidReleaseCheckEvaluator = FdroidReleaseCheckSource(),
        lookupConfigOverride: GitHubLookupConfig? = null,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        existingRepositoryProfile: GitHubRepositoryProfileSnapshot? = null
    ): GitHubTrackedReleaseCheck {
        return evaluateTrackedAppInternal(
            context = context,
            item = item,
            strategy = strategy,
            preciseApkVersionResolver = preciseApkVersionResolver,
            fdroidReleaseCheckSource = fdroidReleaseCheckSource,
            lookupConfigOverride = lookupConfigOverride,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh,
            existingRepositoryProfile = existingRepositoryProfile
        )
    }

    private suspend fun evaluateTrackedAppInternal(
        context: Context,
        item: GitHubTrackedApp,
        strategy: GitHubReleaseLookupStrategy?,
        preciseApkVersionResolver: GitHubPreciseApkVersionResolver,
        fdroidReleaseCheckSource: FdroidReleaseCheckEvaluator,
        lookupConfigOverride: GitHubLookupConfig?,
        profilePurposeOverride: GitHubRepositoryProfilePurpose?,
        forceRefresh: Boolean,
        existingRepositoryProfile: GitHubRepositoryProfileSnapshot?
    ): GitHubTrackedReleaseCheck {
        val lookupConfig = (lookupConfigOverride ?: GitHubReleaseStrategyRegistry.loadLookupConfig())
            .forTrackedItem(item)
        val sourceConfigSignature = item.checkSourceSignature(lookupConfig)
        val localVersionStartNs = System.nanoTime()
        val localVersionInfo = runCatching {
            GitHubInstalledAppRepository.localVersionInfoOrNull(context, item.packageName)
        }.getOrNull()
        val localVersionElapsedMs = elapsedMsSince(localVersionStartNs)
        val localVersion = localVersionInfo?.versionName.orEmpty()
        val localVersionCode = localVersionInfo?.versionCode ?: -1L
        if (item.isDirectApkTrack()) {
            return GitHubDirectApkReleaseCheckSource().evaluate(
                item = item,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                forceRefresh = forceRefresh
            ).withLocalVersionDiagnostics(localVersionElapsedMs)
        }
        if (item.isFdroidRepositoryTrack()) {
            return fdroidReleaseCheckSource.evaluate(
                item = item,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                forceRefresh = forceRefresh
            ).withLocalVersionDiagnostics(localVersionElapsedMs)
        }
        val releaseLookupItem = item.githubReleaseLookupItemOrNull()
        if (item.isGitRepositoryTrack() && releaseLookupItem == null) {
            val gitIdentity = buildGitRepositoryTrackIdentity(item.repoUrl)
                ?: return failedGitRepositoryCheck(
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    sourceConfigSignature = sourceConfigSignature,
                    detail = "invalid Git repository URL"
                ).withLocalVersionDiagnostics(localVersionElapsedMs)
            val gitStrategy = GitRepositoryReleaseStrategy(gitIdentity)
            val gitSnapshotResult = loadSnapshotWithTransientRetry(
                strategy = gitStrategy,
                owner = gitIdentity.owner,
                repo = gitIdentity.repo,
                forceRefresh = forceRefresh
            ).getOrElse { error ->
                return failedGitRepositoryCheck(
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    sourceConfigSignature = sourceConfigSignature,
                    detail = error.message ?: "unknown",
                    error = error,
                ).copy(
                    diagnostics = error.snapshotDiagnostics()
                ).withLocalVersionDiagnostics(localVersionElapsedMs)
            }
            val gitSnapshot = gitSnapshotResult.snapshot
            val preciseStartNs = System.nanoTime()
            val preciseVersions = gitRepositoryPreciseApkVersionResolver(gitIdentity)
                ?.let { gitResolver ->
                    resolvePreciseApkVersions(
                        item = item.copy(owner = gitIdentity.owner, repo = gitIdentity.repo),
                        localVersion = localVersion,
                        snapshot = gitSnapshot,
                        lookupConfig = lookupConfig,
                        resolver = gitResolver
                    )
                }
                ?: PreciseApkVersionPair()
            val preciseElapsedMs = elapsedMsSince(preciseStartNs)
            val comparisonStartNs = System.nanoTime()
            val check = evaluateSnapshot(
                item = item,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                snapshot = gitSnapshot,
                checkAllTrackedPreReleases = lookupConfig.checkAllTrackedPreReleases,
                preciseStableApkVersion = preciseVersions.stable,
                precisePreReleaseApkVersion = preciseVersions.preRelease,
                preciseStableOutcome = preciseVersions.stableOutcome,
                precisePreReleaseOutcome = preciseVersions.preReleaseOutcome,
                sourceConfigSignature = sourceConfigSignature
            ).copy(
                diagnostics = gitSnapshotResult.diagnostics.copy(
                    localVersionElapsedMs = localVersionElapsedMs,
                    preciseApkElapsedMs = preciseElapsedMs,
                    preciseApkRequested = preciseVersions.requested
                )
            )
            return check.copy(
                diagnostics = check.diagnostics.copy(
                    comparisonElapsedMs = elapsedMsSince(comparisonStartNs)
                )
            )
        }
        val repositoryItem = releaseLookupItem ?: item
        val profileRepository = GitHubRepositoryProfileRepository()
        val effectiveStrategy = strategy ?: GitHubReleaseStrategyRegistry.resolveConfiguredStrategy().getOrElse { error ->
            val profileResult = loadRepositoryProfile(
                profileRepository = profileRepository,
                item = repositoryItem,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                purpose = profilePurposeOverride ?: GitHubRepositoryProfilePurpose.VersionCheckFast,
                existingRepositoryProfile = existingRepositoryProfile
            )
            val profile = profileResult.profile
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
                message = GitHubTrackedReleaseStatus.Failed.failureMessage(error.message ?: "unknown"),
                failureDiagnostics = GitHubRefreshFailureClassifier.from(
                    error = error,
                    responseType = "release_strategy",
                ),
                diagnostics = GitHubReleaseCheckDiagnostics(
                    localVersionElapsedMs = localVersionElapsedMs,
                    profileElapsedMs = profileResult.elapsedMs,
                    profileFromCache = profileResult.fromCache
                )
            )
        }

        val snapshotResult = loadSnapshotWithFallback(
            owner = repositoryItem.owner,
            repo = repositoryItem.repo,
            strategy = effectiveStrategy,
            lookupConfig = lookupConfig,
            allowFallback = strategy == null,
            forceRefresh = forceRefresh
        ).getOrElse { error ->
            val snapshotDiagnostics = error.snapshotDiagnostics()
            if (item.shouldWaitForFirstRelease(error)) {
                return GitHubTrackedReleaseCheck(
                    strategyId = effectiveStrategy.id,
                    localVersion = localVersion,
                    localVersionCode = localVersionCode,
                    hasStableRelease = false,
                    hasUpdate = false,
                    sourceConfigSignature = sourceConfigSignature,
                    status = GitHubTrackedReleaseStatus.UpToDate,
                    message = EXTERNAL_BUILD_WAITING_RELEASE_MESSAGE,
                    diagnostics = snapshotDiagnostics.copy(
                        localVersionElapsedMs = localVersionElapsedMs,
                    ),
                )
            }
            val profileResult = loadRepositoryProfile(
                profileRepository = profileRepository,
                item = repositoryItem,
                lookupConfig = lookupConfig,
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                purpose = profilePurposeOverride ?: GitHubRepositoryProfilePurpose.VersionCheckFast,
                existingRepositoryProfile = existingRepositoryProfile
            )
            val profile = profileResult.profile
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
                message = GitHubTrackedReleaseStatus.Failed.failureMessage(error.message ?: "unknown"),
                failureDiagnostics = GitHubRefreshFailureClassifier.from(
                    error = error,
                    responseType = effectiveStrategy.id,
                ),
                diagnostics = snapshotDiagnostics.copy(
                    localVersionElapsedMs = localVersionElapsedMs,
                    profileElapsedMs = profileResult.elapsedMs,
                    profileFromCache = profileResult.fromCache
                )
            )
        }
        val snapshot = snapshotResult.snapshot
        val preciseStartNs = System.nanoTime()
        val preciseVersions = resolvePreciseApkVersions(
            item = item,
            localVersion = localVersion,
            snapshot = snapshot,
            lookupConfig = lookupConfig,
            resolver = preciseApkVersionResolver
        )
        val preciseElapsedMs = elapsedMsSince(preciseStartNs)
        val profileResult = loadRepositoryProfile(
            profileRepository = profileRepository,
            item = repositoryItem,
            lookupConfig = lookupConfig,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            purpose = profilePurposeOverride ?: lookupConfig.defaultRepositoryProfilePurpose(),
            releaseSnapshot = snapshot,
            preciseStableApkVersion = preciseVersions.stable,
            precisePreReleaseApkVersion = preciseVersions.preRelease,
            existingRepositoryProfile = existingRepositoryProfile
        )
        val profile = profileResult.profile

        val comparisonStartNs = System.nanoTime()
        val check = evaluateSnapshot(
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
            preciseStableOutcome = preciseVersions.stableOutcome,
            precisePreReleaseOutcome = preciseVersions.preReleaseOutcome,
            sourceConfigSignature = sourceConfigSignature,
            repositoryProfile = profile
        ).copy(
            diagnostics = snapshotResult.diagnostics.copy(
                localVersionElapsedMs = localVersionElapsedMs,
                preciseApkElapsedMs = preciseElapsedMs,
                preciseApkRequested = preciseVersions.requested,
                profileElapsedMs = profileResult.elapsedMs,
                profileFromCache = profileResult.fromCache
            )
        )
        return check.copy(
            diagnostics = check.diagnostics.copy(
                comparisonElapsedMs = elapsedMsSince(comparisonStartNs)
            )
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
        preciseStableOutcome: GitHubPreciseApkOutcome = GitHubPreciseApkOutcome.Disabled,
        precisePreReleaseOutcome: GitHubPreciseApkOutcome = GitHubPreciseApkOutcome.Disabled,
        sourceConfigSignature: String = "",
        repositoryProfile: GitHubRepositoryProfileSnapshot? = snapshot.repositoryProfile,
        /**
         * Injected only so a frozen corpus can be judged at the moment it was captured.
         *
         * The staleness rule for pre-releases reads a clock, which makes every recorded fixture
         * depend on the day the test runs -- a corpus captured in July starts failing in September
         * for no reason but the calendar. Tests pin this; nothing else passes it.
         */
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubTrackedReleaseCheck {
        val evaluation = GitHubReleaseEvaluationEngine.evaluate(
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            nowMillis = nowMillis,
            snapshot = snapshot,
            policy = GitHubReleaseEvaluationPolicy(
                preferPreRelease = item.preferPreRelease,
                checkAllTrackedPreReleases = checkAllTrackedPreReleases,
                ignoreMode = item.ignoreMode,
                ignoredStableReleaseKey = item.ignoredStableReleaseKey,
                ignoredPreReleaseKey = item.ignoredPreReleaseKey,
            ),
            preciseStableApkVersion = preciseStableApkVersion,
            precisePreReleaseApkVersion = precisePreReleaseApkVersion,
            preciseStableOutcome = preciseStableOutcome,
            precisePreReleaseOutcome = precisePreReleaseOutcome,
        )
        val waitingForFirstRelease =
            item.externalBuildUntilRelease &&
                !evaluation.hasStableRelease &&
                evaluation.preRelease == null
        return GitHubTrackedReleaseCheck(
            strategyId = snapshot.strategyId,
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            matchedRelease = evaluation.matchedRelease,
            stableRelease = evaluation.stableRelease,
            preRelease = evaluation.preRelease,
            hasStableRelease = evaluation.hasStableRelease,
            hasUpdate = evaluation.hasUpdate,
            hasPreReleaseUpdate = evaluation.hasPreReleaseUpdate,
            recommendsPreRelease = evaluation.recommendsPreRelease,
            isPreReleaseInstalled = evaluation.isPreReleaseInstalled,
            preReleaseInfo = evaluation.preReleaseInfo,
            showPreReleaseInfo = evaluation.showPreReleaseInfo,
            releaseHint = evaluation.releaseHint,
            preciseStableApkVersion = preciseStableApkVersion,
            precisePreApkVersion = precisePreReleaseApkVersion,
            repositoryArchived = snapshot.repositoryArchived,
            repositoryFork = snapshot.repositoryFork,
            repositoryPushedAtMillis = snapshot.repositoryPushedAtMillis,
            upstreamFullName = snapshot.upstreamFullName,
            upstreamArchived = snapshot.upstreamArchived,
            upstreamPushedAtMillis = snapshot.upstreamPushedAtMillis,
            repositoryProfile = repositoryProfile,
            releaseSelection = snapshot.selection,
            preReleaseRejection = evaluation.preReleaseRejection,
            sourceConfigSignature = sourceConfigSignature,
            status = if (waitingForFirstRelease) {
                GitHubTrackedReleaseStatus.UpToDate
            } else {
                evaluation.status
            },
            message = if (waitingForFirstRelease) {
                EXTERNAL_BUILD_WAITING_RELEASE_MESSAGE
            } else {
                evaluation.status.defaultMessage
            },
        )
    }

    const val EXTERNAL_BUILD_WAITING_RELEASE_MESSAGE =
        "github.status.external_build_waiting_release"

    internal fun GitHubTrackedApp.shouldWaitForFirstRelease(error: Throwable): Boolean {
        return externalBuildUntilRelease &&
            error.message.orEmpty().trim().equals(NO_RELEASE_ENTRIES_ERROR, ignoreCase = true)
    }

    private const val NO_RELEASE_ENTRIES_ERROR = "no release entries"

    private fun failedGitRepositoryCheck(
        localVersion: String,
        localVersionCode: Long,
        sourceConfigSignature: String,
        detail: String,
        error: Throwable? = null,
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = "git_repository",
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            sourceConfigSignature = sourceConfigSignature,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(detail),
            failureDiagnostics = error?.let { failure ->
                GitHubRefreshFailureClassifier.from(
                    error = failure,
                    responseType = "git_repository",
                )
            } ?: GitHubRefreshFailureDiagnostics(),
        )
    }

    private fun GitHubTrackedReleaseCheck.withLocalVersionDiagnostics(
        localVersionElapsedMs: Long
    ): GitHubTrackedReleaseCheck {
        return copy(
            diagnostics = diagnostics.copy(
                localVersionElapsedMs = localVersionElapsedMs
            )
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
        if (targets.isEmpty()) {
            return PreciseApkVersionPair(
                requested = true,
                stableOutcome = GitHubPreciseApkOutcome.NoTarget,
                preReleaseOutcome = GitHubPreciseApkOutcome.NoTarget
            )
        }
        val results = GitHubExecution.mapOrderedBounded(
            items = targets,
            maxConcurrency = 2
        ) { target ->
            // Kept as a Result rather than flattened to null. A version we could not read and a
            // version that does not exist both leave the comparison resting on release names, but
            // only one of them is a failure, and the reader is owed the difference.
            target.channel to resolver.resolve(
                GitHubPreciseApkVersionRequest(
                    owner = item.owner,
                    repo = item.repo,
                    release = target.release,
                    packageName = item.packageName,
                    lookupConfig = lookupConfig
                )
            )
        }
        fun outcomeFor(channel: PreciseApkVersionChannel): Pair<GitHubRemoteApkVersionInfo?, GitHubPreciseApkOutcome> {
            val entry = results.firstOrNull { it.first == channel }
                ?: return null to GitHubPreciseApkOutcome.NoTarget
            val info = entry.second.getOrNull()?.takeIf { it.versionCodeLong != null }
            return info to when (info) {
                null -> GitHubPreciseApkOutcome.Unresolved
                else -> GitHubPreciseApkOutcome.Resolved
            }
        }
        val (stable, stableOutcome) = outcomeFor(PreciseApkVersionChannel.Stable)
        val (preRelease, preReleaseOutcome) = outcomeFor(PreciseApkVersionChannel.PreRelease)
        return PreciseApkVersionPair(
            stable = stable,
            preRelease = preRelease,
            requested = true,
            stableOutcome = stableOutcome,
            preReleaseOutcome = preReleaseOutcome
        )
    }

    private fun gitRepositoryPreciseApkVersionResolver(
        identity: GitRepositoryTrackIdentity
    ): GitHubPreciseApkVersionResolver? {
        return when (identity.platform) {
            GitRepositoryPlatform.Gitee,
            GitRepositoryPlatform.GitLab,
            GitRepositoryPlatform.Gitea -> {
                GitHubPreciseApkVersionResolver(
                    GitRepositoryPreciseApkVersionSource(identity = identity)
                )
            }

            GitRepositoryPlatform.GitHub,
            GitRepositoryPlatform.Generic -> null
        }
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
        precisePreReleaseApkVersion: GitHubRemoteApkVersionInfo? = null,
        existingRepositoryProfile: GitHubRepositoryProfileSnapshot? = null
    ): RepositoryProfileLoadResult {
        val startedNs = System.nanoTime()
        reusableRepositoryProfile(
            existingRepositoryProfile = existingRepositoryProfile,
            item = item,
            lookupConfig = lookupConfig,
            purpose = purpose,
            localVersion = localVersion,
            localVersionCode = localVersionCode
        )?.let { profile ->
            return RepositoryProfileLoadResult(
                profile = profile,
                elapsedMs = elapsedMsSince(startedNs),
                fromCache = true
            )
        }
        val profile = try {
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
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }
        return RepositoryProfileLoadResult(
            profile = profile,
            elapsedMs = elapsedMsSince(startedNs),
            fromCache = false
        )
    }

    private suspend fun loadSnapshotWithFallback(
        owner: String,
        repo: String,
        strategy: GitHubReleaseLookupStrategy,
        lookupConfig: GitHubLookupConfig,
        allowFallback: Boolean,
        forceRefresh: Boolean = false
    ): Result<SnapshotLoadResult> {
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
        if (fallbackResult.isSuccess) {
            return fallbackResult.map { result ->
                result.copy(
                    diagnostics = result.diagnostics.copy(
                        fallbackStrategyId = fallbackStrategy.id
                    )
                )
            }
        }

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
            SnapshotLoadException(
                diagnostics =
                    (fallbackError ?: primaryError)
                        .snapshotDiagnostics()
                        .copy(fallbackStrategyId = fallbackStrategy.id),
                cause = IllegalStateException(message, fallbackError ?: primaryError)
            )
        )
    }

    private suspend fun loadSnapshotWithTransientRetry(
        strategy: GitHubReleaseLookupStrategy,
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<SnapshotLoadResult> {
        if (forceRefresh) {
            strategy.clearCaches()
        }
        var latestResult = strategy.loadSnapshotTraceCompat(owner, repo)
        if (latestResult.isSuccess) return latestResult

        repeat(transientRetryCount) {
            val error = latestResult.exceptionOrNull() ?: return latestResult
            if (!error.shouldTryStrategyFallback()) {
                return latestResult
            }
            strategy.clearCaches()
            latestResult = strategy.loadSnapshotTraceCompat(owner, repo)
            if (latestResult.isSuccess) return latestResult
        }
        return latestResult
    }

    private suspend fun GitHubReleaseLookupStrategy.loadSnapshotTraceCompat(
        owner: String,
        repo: String
    ): Result<SnapshotLoadResult> {
        val trace =
            when (this) {
                GitHubAtomReleaseStrategy -> GitHubAtomReleaseStrategy.loadSnapshotTrace(owner, repo)
                is GitHubApiTokenReleaseStrategy -> loadSnapshotTrace(owner, repo)
                else -> {
                    val startedNs = System.nanoTime()
                    val result =
                        try {
                            loadSnapshot(owner, repo)
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            Result.failure(error)
                        }
                    GitHubStrategyLoadTrace(
                        result = result,
                        fromCache = false,
                        elapsedMs = elapsedMsSince(startedNs)
                    )
                }
            }
        val diagnostics =
            GitHubReleaseCheckDiagnostics(
                snapshotElapsedMs = trace.elapsedMs,
                snapshotFromCache = trace.fromCache
            )
        return trace.result.fold(
            onSuccess = { snapshot ->
                Result.success(
                    SnapshotLoadResult(
                        snapshot = snapshot,
                        diagnostics = diagnostics
                    )
                )
            },
            onFailure = { error ->
                Result.failure(
                    SnapshotLoadException(
                        diagnostics = diagnostics,
                        cause = error
                    )
                )
            }
        )
    }

    private fun Throwable.snapshotDiagnostics(): GitHubReleaseCheckDiagnostics {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < 8) {
            if (current is SnapshotLoadException) return current.diagnostics
            current = current.cause
            depth += 1
        }
        return GitHubReleaseCheckDiagnostics()
    }

    private fun reusableRepositoryProfile(
        existingRepositoryProfile: GitHubRepositoryProfileSnapshot?,
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        purpose: GitHubRepositoryProfilePurpose,
        localVersion: String,
        localVersionCode: Long
    ): GitHubRepositoryProfileSnapshot? {
        val profile = existingRepositoryProfile ?: return null
        if (!profile.owner.equals(item.owner, ignoreCase = true)) return null
        if (!profile.repo.equals(item.repo, ignoreCase = true)) return null
        val requiredCapabilities = purpose.requiredCapabilities(lookupConfig.profileDepth)
        val activeSignature = lookupConfig.githubProfileSourceSignature(requiredCapabilities)
        if (
            !profile.isFreshFor(
                activeSourceConfigSignature = activeSignature,
                requiredCapabilities = requiredCapabilities
            )
        ) {
            return null
        }
        val profileLocalVersion = profile.localFit.localVersionName?.value.orEmpty()
        if (
            localVersion.isNotBlank() &&
            profileLocalVersion.isNotBlank() &&
            profileLocalVersion != localVersion
        ) {
            return null
        }
        val profileLocalVersionCode = profile.localFit.localVersionCode?.value ?: -1L
        if (
            localVersionCode > 0L &&
            profileLocalVersionCode > 0L &&
            profileLocalVersionCode != localVersionCode
        ) {
            return null
        }
        return profile
    }

    private fun elapsedMsSince(startNs: Long): Long =
        ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

    private data class SnapshotLoadResult(
        val snapshot: GitHubRepositoryReleaseSnapshot,
        val diagnostics: GitHubReleaseCheckDiagnostics
    )

    private class SnapshotLoadException(
        val diagnostics: GitHubReleaseCheckDiagnostics,
        cause: Throwable
    ) : IllegalStateException(cause.message ?: cause.javaClass.simpleName, cause)

    private data class RepositoryProfileLoadResult(
        val profile: GitHubRepositoryProfileSnapshot?,
        val elapsedMs: Long,
        val fromCache: Boolean
    )

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
            latestStableAuthorAvatarUrl = stableRelease?.authorAvatarUrl.orEmpty(),
            latestStableUpdatedAtMillis = stableRelease?.updatedAtMillis ?: -1L,
            latestPreName = preRelease?.rawName.orEmpty(),
            latestPreRawTag = preRelease?.rawTag.orEmpty(),
            latestPreUrl = preRelease?.link.orEmpty(),
            latestPreAuthorAvatarUrl = preRelease?.authorAvatarUrl.orEmpty(),
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
                        updatedAtMillis = entry.latestStableUpdatedAtMillis.takeIf { ts -> ts > 0L },
                        authorAvatarUrl = entry.latestStableAuthorAvatarUrl
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
                        updatedAtMillis = entry.latestPreUpdatedAtMillis.takeIf { ts -> ts > 0L },
                        authorAvatarUrl = entry.latestPreAuthorAvatarUrl
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
        val preRelease: GitHubRemoteApkVersionInfo? = null,
        val requested: Boolean = false,
        val stableOutcome: GitHubPreciseApkOutcome = GitHubPreciseApkOutcome.Disabled,
        val preReleaseOutcome: GitHubPreciseApkOutcome = GitHubPreciseApkOutcome.Disabled
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
