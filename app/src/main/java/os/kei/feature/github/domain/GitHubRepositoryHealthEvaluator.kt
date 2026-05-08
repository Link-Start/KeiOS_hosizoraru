package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubRepositoryHealth
import os.kei.feature.github.model.GitHubRepositoryHealthInput
import os.kei.feature.github.model.GitHubRepositoryHealthReason
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot

object GitHubRepositoryHealthEvaluator {
    fun evaluate(
        input: GitHubRepositoryHealthInput,
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubRepositoryHealth {
        val profile = input.profile
        var score = 82
        val reasons = mutableListOf<GitHubRepositoryHealthReason>()

        if (input.repositoryArchived || profile?.lifecycle?.archived.valueOr(false)) {
            score -= 55
            reasons += GitHubRepositoryHealthReason.RepositoryArchived
        }
        if (profile?.lifecycle?.disabled.valueOr(false)) {
            score -= 50
            reasons += GitHubRepositoryHealthReason.RepositoryDisabled
        }
        if (input.repositoryFork || profile?.lifecycle?.fork.valueOr(false)) {
            score -= 6
            reasons += GitHubRepositoryHealthReason.RepositoryFork
            val forkMaintenance = evaluateForkMaintenance(input, profile, nowMillis)
            score += forkMaintenance.scoreDelta
            reasons += forkMaintenance.reasons
        }
        if (input.checkFailed || input.hasUpdate == null && input.latestStableRawTag.isBlank()) {
            score -= 28
            reasons += GitHubRepositoryHealthReason.CheckFailed
        }
        if (input.packageName.isBlank()) {
            score -= 12
            reasons += GitHubRepositoryHealthReason.MissingPackageName
        }
        if (!input.hasStableRelease) {
            score -= 18
            reasons += GitHubRepositoryHealthReason.MissingStableRelease
        }
        if (input.localVersion.isBlank() || input.localVersionCode < 0L) {
            score -= 10
            reasons += GitHubRepositoryHealthReason.LocalMissing
        }
        if (input.hasUpdate == true) {
            score += 7
            reasons += GitHubRepositoryHealthReason.UpdateAvailable
        }
        if (input.recommendsPreRelease) {
            score -= 6
            reasons += GitHubRepositoryHealthReason.PreReleaseRecommended
        }
        if (input.hasStableRelease && input.latestStableRawTag.isNotBlank()) {
            score += 6
            reasons += GitHubRepositoryHealthReason.StableDetected
        }
        val latestReleaseMillis =
            maxOf(input.latestStableUpdatedAtMillis, input.latestPreUpdatedAtMillis)
        if (latestReleaseMillis > 0L && nowMillis - latestReleaseMillis <= FRESH_RELEASE_WINDOW_MS) {
            score += 5
            reasons += GitHubRepositoryHealthReason.FreshRelease
        }

        score += evaluateFreshness(input, profile, nowMillis, reasons)
        score += evaluateDistribution(input, profile, reasons)
        score += evaluateActions(profile, nowMillis, reasons)
        score += evaluateCommunity(profile, reasons)
        score += evaluateLocalFit(profile, reasons)

        val normalizedScore = score.coerceIn(0, 100)
        val level = when {
            normalizedScore >= 78 -> GitHubDecisionLevel.Good
            normalizedScore >= 55 -> GitHubDecisionLevel.Review
            else -> GitHubDecisionLevel.Risk
        }
        return GitHubRepositoryHealth(
            score = normalizedScore,
            level = level,
            reasons = reasons.distinct().take(4)
        )
    }

    fun impactFor(reason: GitHubRepositoryHealthReason): Int {
        return when (reason) {
            GitHubRepositoryHealthReason.RepositoryArchived -> -55
            GitHubRepositoryHealthReason.RepositoryDisabled -> -50
            GitHubRepositoryHealthReason.RepositoryFork -> -6
            GitHubRepositoryHealthReason.ForkUpstreamArchived -> -4
            GitHubRepositoryHealthReason.ForkBehindUpstream -> -16
            GitHubRepositoryHealthReason.ForkMaintainedIndependently -> 8
            GitHubRepositoryHealthReason.ForkTracksUpstream -> -4
            GitHubRepositoryHealthReason.StaleRepositoryActivity -> -14
            GitHubRepositoryHealthReason.StaleRelease -> -10
            GitHubRepositoryHealthReason.ActionsHealthy -> 4
            GitHubRepositoryHealthReason.ActionsFailing -> -8
            GitHubRepositoryHealthReason.AndroidAssetsDetected -> 4
            GitHubRepositoryHealthReason.MissingAndroidAssets -> -8
            GitHubRepositoryHealthReason.CommunityProfileComplete -> 4
            GitHubRepositoryHealthReason.MissingReadme -> -5
            GitHubRepositoryHealthReason.MissingLicense -> -4
            GitHubRepositoryHealthReason.LocalPackageMatched -> 6
            GitHubRepositoryHealthReason.LocalPackageMismatch -> -14
            GitHubRepositoryHealthReason.UpdateAvailable -> 7
            GitHubRepositoryHealthReason.PreReleaseRecommended -> -6
            GitHubRepositoryHealthReason.CheckFailed -> -28
            GitHubRepositoryHealthReason.MissingPackageName -> -12
            GitHubRepositoryHealthReason.MissingStableRelease -> -18
            GitHubRepositoryHealthReason.LocalMissing -> -10
            GitHubRepositoryHealthReason.StableDetected -> 6
            GitHubRepositoryHealthReason.FreshRelease -> 5
        }
    }

    private fun evaluateFreshness(
        input: GitHubRepositoryHealthInput,
        profile: GitHubRepositoryProfileSnapshot?,
        nowMillis: Long,
        reasons: MutableList<GitHubRepositoryHealthReason>
    ): Int {
        var scoreDelta = 0
        val archived = input.repositoryArchived || profile?.lifecycle?.archived.valueOr(false)
        val repoPushedAt = profile?.activity?.pushedAtMillis.valueOr(input.repositoryPushedAtMillis)
        if (!archived && repoPushedAt > 0L && nowMillis - repoPushedAt >= STALE_REPOSITORY_ACTIVITY_WINDOW_MS) {
            scoreDelta -= 14
            reasons += GitHubRepositoryHealthReason.StaleRepositoryActivity
        }
        val latestReleaseMillis = maxOf(
            profile?.releases?.latestStablePublishedAtMillis.valueOr(input.latestStableUpdatedAtMillis),
            profile?.releases?.latestPreReleasePublishedAtMillis.valueOr(input.latestPreUpdatedAtMillis)
        )
        if (!archived && latestReleaseMillis > 0L && nowMillis - latestReleaseMillis >= STALE_RELEASE_WINDOW_MS) {
            scoreDelta -= 10
            reasons += GitHubRepositoryHealthReason.StaleRelease
        }
        return scoreDelta
    }

    private fun evaluateDistribution(
        input: GitHubRepositoryHealthInput,
        profile: GitHubRepositoryProfileSnapshot?,
        reasons: MutableList<GitHubRepositoryHealthReason>
    ): Int {
        profile ?: return 0
        var scoreDelta = 0
        val hasInstallableAsset = profile.distribution.hasInstallableAndroidAsset.valueOr(false)
        val apkLikeCount = profile.distribution.apkLikeAssetCount.valueOr(0)
        val latestAssetCount = profile.distribution.latestAssetCount.valueOr(0)
        if (hasInstallableAsset || apkLikeCount > 0) {
            scoreDelta += 4
            reasons += GitHubRepositoryHealthReason.AndroidAssetsDetected
        } else if (input.packageName.isNotBlank() && latestAssetCount > 0) {
            scoreDelta -= 8
            reasons += GitHubRepositoryHealthReason.MissingAndroidAssets
        }
        return scoreDelta
    }

    private fun evaluateActions(
        profile: GitHubRepositoryProfileSnapshot?,
        nowMillis: Long,
        reasons: MutableList<GitHubRepositoryHealthReason>
    ): Int {
        profile ?: return 0
        val latestUpdatedAt = profile.actions.latestRunUpdatedAtMillis.valueOr(-1L)
        if (latestUpdatedAt <= 0L) return 0
        val conclusion = profile.actions.latestRunConclusion.valueOr("").lowercase()
        val recent = nowMillis - latestUpdatedAt <= RECENT_ACTIONS_WINDOW_MS
        val moderatelyRecent = nowMillis - latestUpdatedAt <= ACTIONS_FAILURE_SIGNAL_WINDOW_MS
        return when {
            recent && conclusion == "success" -> {
                reasons += GitHubRepositoryHealthReason.ActionsHealthy
                4
            }

            moderatelyRecent && conclusion in failingActionConclusions -> {
                reasons += GitHubRepositoryHealthReason.ActionsFailing
                -8
            }

            else -> 0
        }
    }

    private fun evaluateCommunity(
        profile: GitHubRepositoryProfileSnapshot?,
        reasons: MutableList<GitHubRepositoryHealthReason>
    ): Int {
        profile ?: return 0
        var scoreDelta = 0
        val hasReadme = profile.community.hasReadme?.value
        val hasLicense = profile.community.hasLicense?.value
        if (hasReadme == true && hasLicense == true) {
            scoreDelta += 4
            reasons += GitHubRepositoryHealthReason.CommunityProfileComplete
        } else {
            if (hasReadme == false) {
                scoreDelta -= 5
                reasons += GitHubRepositoryHealthReason.MissingReadme
            }
            if (hasLicense == false) {
                scoreDelta -= 4
                reasons += GitHubRepositoryHealthReason.MissingLicense
            }
        }
        return scoreDelta
    }

    private fun evaluateLocalFit(
        profile: GitHubRepositoryProfileSnapshot?,
        reasons: MutableList<GitHubRepositoryHealthReason>
    ): Int {
        profile ?: return 0
        val packageNameMatched = profile.localFit.packageNameMatched?.value ?: return 0
        return if (packageNameMatched) {
            reasons += GitHubRepositoryHealthReason.LocalPackageMatched
            6
        } else {
            reasons += GitHubRepositoryHealthReason.LocalPackageMismatch
            -14
        }
    }

    private fun evaluateForkMaintenance(
        input: GitHubRepositoryHealthInput,
        profile: GitHubRepositoryProfileSnapshot?,
        nowMillis: Long
    ): ForkMaintenanceImpact {
        val upstream = profile?.lifecycle?.upstream
        val upstreamFullName = upstream?.fullName.valueOr(input.upstreamFullName)
        if (upstreamFullName.isBlank()) return ForkMaintenanceImpact(0, emptyList())
        val reasons = mutableListOf<GitHubRepositoryHealthReason>()
        var scoreDelta = 0
        val forkPushedAt = profile?.activity?.pushedAtMillis.valueOr(input.repositoryPushedAtMillis)
        val upstreamPushedAt = upstream?.pushedAtMillis.valueOr(input.upstreamPushedAtMillis)
        val upstreamArchived = upstream?.archived.valueOr(input.upstreamArchived)
        val forkFresh =
            forkPushedAt > 0L && nowMillis - forkPushedAt <= FORK_RECENT_ACTIVITY_WINDOW_MS
        val upstreamFresh =
            upstreamPushedAt > 0L && nowMillis - upstreamPushedAt <= FORK_RECENT_ACTIVITY_WINDOW_MS

        if (upstreamArchived) {
            scoreDelta -= 4
            reasons += GitHubRepositoryHealthReason.ForkUpstreamArchived
            if (forkFresh) {
                scoreDelta += 8
                reasons += GitHubRepositoryHealthReason.ForkMaintainedIndependently
            }
            return ForkMaintenanceImpact(scoreDelta, reasons)
        }

        val upstreamMuchNewer = forkPushedAt > 0L &&
                upstreamPushedAt > 0L &&
                upstreamPushedAt - forkPushedAt >= FORK_UPSTREAM_DRIFT_WINDOW_MS
        if (upstreamMuchNewer) {
            scoreDelta -= 16
            reasons += GitHubRepositoryHealthReason.ForkBehindUpstream
        } else if (forkFresh && upstreamFresh) {
            scoreDelta -= 4
            reasons += GitHubRepositoryHealthReason.ForkTracksUpstream
        } else if (forkFresh && !upstreamFresh) {
            scoreDelta += 8
            reasons += GitHubRepositoryHealthReason.ForkMaintainedIndependently
        }
        return ForkMaintenanceImpact(scoreDelta, reasons)
    }

    private fun <T> os.kei.feature.github.model.GitHubProfileField<T>?.valueOr(
        fallback: T
    ): T {
        return this?.value ?: fallback
    }

    private data class ForkMaintenanceImpact(
        val scoreDelta: Int,
        val reasons: List<GitHubRepositoryHealthReason>
    )

    private val failingActionConclusions = setOf(
        "failure",
        "timed_out",
        "cancelled",
        "startup_failure",
        "action_required"
    )

    private const val FRESH_RELEASE_WINDOW_MS = 1000L * 60L * 60L * 24L * 14L
    private const val FORK_RECENT_ACTIVITY_WINDOW_MS = 1000L * 60L * 60L * 24L * 90L
    private const val FORK_UPSTREAM_DRIFT_WINDOW_MS = 1000L * 60L * 60L * 24L * 60L
    private const val STALE_REPOSITORY_ACTIVITY_WINDOW_MS = 1000L * 60L * 60L * 24L * 365L
    private const val STALE_RELEASE_WINDOW_MS = 1000L * 60L * 60L * 24L * 365L
    private const val RECENT_ACTIONS_WINDOW_MS = 1000L * 60L * 60L * 24L * 30L
    private const val ACTIONS_FAILURE_SIGNAL_WINDOW_MS = 1000L * 60L * 60L * 24L * 90L
}
