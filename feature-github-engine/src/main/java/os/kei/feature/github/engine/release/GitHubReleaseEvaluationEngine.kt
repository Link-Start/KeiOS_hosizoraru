package os.kei.feature.github.engine.release

import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.buildGitHubReleaseIgnoreKey
import os.kei.feature.github.model.githubReleaseIgnoreKeyMatches
import os.kei.feature.github.model.suppressesAllReleaseUpdates

data class GitHubReleaseEvaluationPolicy(
    val preferPreRelease: Boolean = false,
    val checkAllTrackedPreReleases: Boolean = false,
    val ignoreMode: GitHubTrackedIgnoreMode = GitHubTrackedIgnoreMode.None,
    val ignoredStableReleaseKey: String = "",
    val ignoredPreReleaseKey: String = "",
)

data class GitHubReleaseEvaluationResult(
    val matchedRelease: GitHubAtomReleaseEntry? = null,
    val stableRelease: GitHubReleaseVersionSignals? = null,
    val preRelease: GitHubReleaseVersionSignals? = null,
    val hasStableRelease: Boolean = true,
    val hasUpdate: Boolean = false,
    val hasPreReleaseUpdate: Boolean = false,
    val recommendsPreRelease: Boolean = false,
    val isPreReleaseInstalled: Boolean = false,
    val preReleaseInfo: String = "",
    val showPreReleaseInfo: Boolean = false,
    val releaseHint: String = "",
    val status: GitHubTrackedReleaseStatus = GitHubTrackedReleaseStatus.ComparisonUncertain,
)

object GitHubReleaseEvaluationEngine {
    fun evaluate(
        localVersion: String,
        localVersionCode: Long,
        snapshot: GitHubRepositoryReleaseSnapshot,
        policy: GitHubReleaseEvaluationPolicy = GitHubReleaseEvaluationPolicy(),
        preciseStableApkVersion: GitHubRemoteApkVersionInfo? = null,
        precisePreReleaseApkVersion: GitHubRemoteApkVersionInfo? = null,
        /** Injected so the staleness rule below can be pinned by a test rather than drift with the day. */
        nowMillis: Long = System.currentTimeMillis(),
    ): GitHubReleaseEvaluationResult {
        val matchedEntry = snapshot.feed.entries.firstOrNull { entry ->
            GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                candidates = entry.versionCandidates,
                remoteChannel = entry.channel,
            ) == 0
        }
        val matchedCurrentStable = snapshot.hasStableRelease &&
            matchedEntry != null &&
            GitHubVersionUtils.compareStructuredCandidateSets(
                leftCandidates = matchedEntry.versionCandidates,
                rightCandidates = snapshot.latestStable.versionCandidates,
                leftChannel = matchedEntry.channel,
                rightChannel = snapshot.latestStable.channel,
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
            (matchedEntry?.isLikelyPreRelease == true && !matchedCurrentStable) ||
                localChannel.isPreRelease
        val inspectPreRelease = policy.checkAllTrackedPreReleases ||
            policy.preferPreRelease ||
            isLocalPreReleaseInstalled

        val stableCmp = latestStable?.let {
            GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                candidates = it.versionCandidates,
                remoteChannel = it.channel,
            )
        }
        val latestPreIsRelevant = when {
            latestPre == null -> false
            latestStable == null -> true
            else -> GitHubVersionUtils.isRelevantPreRelease(
                preReleaseCandidates = latestPre.versionCandidates,
                stableCandidates = latestStable.versionCandidates,
                preReleaseUpdatedAtMillis = latestPre.updatedAtMillis,
                stableUpdatedAtMillis = latestStable.updatedAtMillis,
                preReleaseChannel = latestPre.channel,
                stableChannel = latestStable.channel,
            )
        }
        val latestPreCmp = latestPre?.let {
            GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                localVersion = localVersion,
                localVersionCode = localVersionCode,
                candidates = it.versionCandidates,
                remoteChannel = it.channel,
            )
        }

        val preciseStableCmp = preciseStableApkVersion
            ?.versionCodeLong
            ?.takeIf { localVersionCode >= 0L }
            ?.compareTo(localVersionCode)
        val precisePreCmp = precisePreReleaseApkVersion
            ?.versionCodeLong
            ?.takeIf { localVersionCode >= 0L }
            ?.compareTo(localVersionCode)
        // A preview line nobody has fed for a fortnight stops being offered, and stops taking a row.
        //
        // The clock is the *asset* clock: a pre-release used to host CI builds is published once and
        // then only its artifacts move, so judging by `published_at` would retire a line that is
        // still producing builds newer than the stable. `effectiveFreshnessMillis` is the later of
        // the two.
        //
        // Only when a stable exists to fall back on. A repository whose newest release *is* its
        // pre-release has not abandoned anything — it simply has not shipped — and hiding it would
        // leave the card with nothing to say.
        val preReleaseAbandoned = latestStable != null &&
            latestPre != null &&
            GitHubVersionUtils.isAbandonedPreRelease(
                preReleaseFreshnessMillis = latestPre.effectiveFreshnessMillis,
                nowMillis = nowMillis,
            )
        // An update you cannot install is not an update. `false` here is a positive statement from a
        // source that can see the release's assets and found none attached — not merely the absence
        // of evidence, which is `null` and changes nothing. Without this the comparison falls back to
        // the release *name*, and a rolling tag whose name keeps moving while its asset list stays
        // empty reports an update on every refresh, forever.
        val stableIsInstallable = latestStable?.hasDownloadableAsset != false
        val preReleaseIsInstallable = latestPre?.hasDownloadableAsset != false
        val rawHasPreReleaseUpdate = inspectPreRelease &&
            latestPreIsRelevant &&
            preReleaseIsInstallable &&
            // Offering an update for a row that is not shown would be incoherent.
            !preReleaseAbandoned &&
            (
                precisePreCmp?.let { it > 0 }
                    ?: (latestPreCmp?.let { it < 0 } == true)
            )
        val rawStableHasUpdate = stableIsInstallable &&
            (
                preciseStableCmp?.let { it > 0 }
                    ?: (stableCmp?.let { it < 0 } == true)
            )
        val suppressAllReleaseUpdates = policy.ignoreMode.suppressesAllReleaseUpdates()
        val stableReleaseIgnoreKey = buildGitHubReleaseIgnoreKey(
            release = latestStable,
            preciseApkVersion = preciseStableApkVersion,
        )
        val preReleaseIgnoreKey = buildGitHubReleaseIgnoreKey(
            release = latestPre,
            preciseApkVersion = precisePreReleaseApkVersion,
        )
        val stableUpdateIgnored = rawStableHasUpdate &&
            (
                suppressAllReleaseUpdates ||
                    policy.ignoreMode == GitHubTrackedIgnoreMode.CurrentStable &&
                    githubReleaseIgnoreKeyMatches(
                        storedKey = policy.ignoredStableReleaseKey,
                        releaseKey = stableReleaseIgnoreKey,
                    )
            )
        val preReleaseUpdateIgnored = rawHasPreReleaseUpdate &&
            (
                suppressAllReleaseUpdates ||
                    policy.ignoreMode == GitHubTrackedIgnoreMode.CurrentPreRelease &&
                    githubReleaseIgnoreKeyMatches(
                        storedKey = policy.ignoredPreReleaseKey,
                        releaseKey = preReleaseIgnoreKey,
                    )
            )
        val stableHasUpdate = rawStableHasUpdate && !stableUpdateIgnored
        val hasPreReleaseUpdate = rawHasPreReleaseUpdate && !preReleaseUpdateIgnored
        val recommendsPreRelease = hasPreReleaseUpdate &&
            (policy.preferPreRelease || (isLocalPreReleaseInstalled && !stableHasUpdate))
        val hasUpdate = stableHasUpdate || recommendsPreRelease
        val showIgnoredStatus = suppressAllReleaseUpdates ||
            stableUpdateIgnored ||
            preReleaseUpdateIgnored

        // Hidden only when the project has closed its preview line, not merely when the newest
        // preview is behind the stable. That second case is ordinary and this project's corpus pins
        // it: a `5.4.0-beta05` beside a `5.4.3` is the last preview of the current line and reads as
        // useful history. A 2023 beta beside a 2026 stable is a dead row, and leaving it there is
        // what makes it look like a choice the reader has.
        val surfacedPreRelease = latestPre?.takeUnless { preReleaseAbandoned }
        val preReleaseInfo = when {
            inspectPreRelease && surfacedPreRelease != null -> surfacedPreRelease.displayVersion
            inspectPreRelease && isLocalPreReleaseInstalled && matchedEntry != null ->
                matchedEntry.displayVersion
            else -> ""
        }
        val showPreReleaseInfo = inspectPreRelease && preReleaseInfo.isNotBlank()
        val releaseHint = when {
            hasOnlyPreReleases && !inspectPreRelease ->
                GitHubTrackedReleaseStatus.ONLY_PRERELEASES_HINT_MESSAGE
            else -> ""
        }

        val stableCompared = stableCmp != null || preciseStableCmp != null
        val status = when {
            recommendsPreRelease -> GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable
            stableHasUpdate -> GitHubTrackedReleaseStatus.UpdateAvailable
            hasPreReleaseUpdate -> GitHubTrackedReleaseStatus.PreReleaseOptional
            showIgnoredStatus -> GitHubTrackedReleaseStatus.Ignored
            inspectPreRelease && isLocalPreReleaseInstalled ->
                GitHubTrackedReleaseStatus.PreReleaseTracked
            stableCompared && !hasUpdate -> GitHubTrackedReleaseStatus.UpToDate
            matchedEntry != null -> GitHubTrackedReleaseStatus.MatchedRelease
            else -> GitHubTrackedReleaseStatus.ComparisonUncertain
        }

        return GitHubReleaseEvaluationResult(
            matchedRelease = matchedEntry,
            stableRelease = latestStable,
            preRelease = surfacedPreRelease,
            hasStableRelease = snapshot.hasStableRelease,
            hasUpdate = hasUpdate,
            hasPreReleaseUpdate = hasPreReleaseUpdate,
            recommendsPreRelease = recommendsPreRelease,
            isPreReleaseInstalled = isLocalPreReleaseInstalled,
            preReleaseInfo = preReleaseInfo,
            showPreReleaseInfo = showPreReleaseInfo,
            releaseHint = releaseHint,
            status = status,
        )
    }
}
