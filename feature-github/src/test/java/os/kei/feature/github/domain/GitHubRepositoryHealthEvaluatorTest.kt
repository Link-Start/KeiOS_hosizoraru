package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryForkSyncProfile
import os.kei.feature.github.model.GitHubRepositoryHealthInput
import os.kei.feature.github.model.GitHubRepositoryHealthReason
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryLocalFitProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryTrafficProfile
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubRepositoryHealthEvaluatorTest {
    @Test
    fun `archived repository enters risk even with stable release`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(
                    archived = true,
                    pushedAtMillis = NOW - 24L * DAY_MS
                )
            ),
            nowMillis = NOW
        )

        assertEquals(GitHubDecisionLevel.Risk, health.level)
        assertTrue(health.score < 55)
        assertTrue(GitHubRepositoryHealthReason.RepositoryArchived in health.reasons)
    }

    @Test
    fun `active fork with archived upstream keeps independent maintenance signal`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(
                    fork = true,
                    pushedAtMillis = NOW - 5L * DAY_MS,
                    upstreamArchived = true,
                    upstreamPushedAtMillis = NOW - 240L * DAY_MS
                )
            ),
            nowMillis = NOW
        )

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(GitHubRepositoryHealthReason.ForkMaintainedIndependently in health.reasons)
    }

    @Test
    fun `stale fork behind active upstream enters risk`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(
                    fork = true,
                    pushedAtMillis = NOW - 130L * DAY_MS,
                    upstreamArchived = false,
                    upstreamPushedAtMillis = NOW - 5L * DAY_MS
                )
            ),
            nowMillis = NOW
        )

        assertEquals(GitHubDecisionLevel.Risk, health.level)
        assertTrue(GitHubRepositoryHealthReason.ForkBehindUpstream in health.reasons)
    }

    @Test
    fun `deep fork compare and security signals adjust score weakly`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(
                    fork = false,
                    pushedAtMillis = NOW - 15L * DAY_MS,
                    upstreamArchived = false,
                    upstreamPushedAtMillis = NOW - 10L * DAY_MS,
                    forkSyncBehindBy = 4,
                    openSecurityAlerts = 2,
                    trafficLatestAtMillis = NOW - DAY_MS
                )
            ),
            nowMillis = NOW
        )

        assertTrue(GitHubRepositoryHealthReason.ForkCompareBehind in health.reasons)
        assertTrue(GitHubRepositoryHealthReason.OpenSecurityAlerts in health.reasons)
        assertEquals(GitHubDecisionLevel.Review, health.level)
    }

    @Test
    fun `full healthy evidence reaches good without saturating score`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(
                    pushedAtMillis = NOW - DAY_MS,
                    latestRunUpdatedAtMillis = NOW - DAY_MS,
                    latestRunConclusion = "success",
                    hasInstallableAsset = true,
                    hasCommunityFiles = true,
                    packageNameMatched = true
                ),
                latestStableUpdatedAtMillis = NOW - DAY_MS
            ),
            nowMillis = NOW
        )

        assertEquals(GitHubDecisionLevel.Good, health.level)
        assertTrue(health.score in 80..94)
        assertTrue(GitHubRepositoryHealthReason.LocalPackageMatched in health.reasons)
    }

    @Test
    fun `minimal fresh stable release stays in review`() {
        val health = GitHubRepositoryHealthEvaluator.evaluate(
            input = baseInput(
                profile = profile(pushedAtMillis = NOW - DAY_MS),
                latestStableUpdatedAtMillis = NOW - DAY_MS
            ),
            nowMillis = NOW
        )

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(health.score < 80)
        assertTrue(GitHubRepositoryHealthReason.FreshRelease in health.reasons)
    }

    private fun baseInput(
        profile: GitHubRepositoryProfileSnapshot,
        latestStableUpdatedAtMillis: Long = NOW - 30L * DAY_MS
    ): GitHubRepositoryHealthInput {
        return GitHubRepositoryHealthInput(
            packageName = "demo.app",
            localVersion = "1.0.0",
            localVersionCode = 100L,
            hasStableRelease = true,
            hasUpdate = false,
            latestStableRawTag = "v1.0.0",
            latestStableUpdatedAtMillis = latestStableUpdatedAtMillis,
            profile = profile
        )
    }

    private fun profile(
        archived: Boolean = false,
        fork: Boolean = false,
        pushedAtMillis: Long,
        upstreamArchived: Boolean = false,
        upstreamPushedAtMillis: Long = -1L,
        forkSyncBehindBy: Int = 0,
        openSecurityAlerts: Int = 0,
        trafficLatestAtMillis: Long = -1L,
        latestRunUpdatedAtMillis: Long = -1L,
        latestRunConclusion: String = "",
        hasInstallableAsset: Boolean = false,
        hasCommunityFiles: Boolean = false,
        packageNameMatched: Boolean? = null
    ): GitHubRepositoryProfileSnapshot {
        return GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = NOW,
            lifecycle = GitHubRepositoryLifecycleProfile(
                archived = field(archived),
                fork = field(fork),
                upstream = if (fork) {
                    GitHubRepositoryUpstreamProfile(
                        fullName = field("upstream/app"),
                        archived = field(upstreamArchived),
                        pushedAtMillis = field(upstreamPushedAtMillis)
                    )
                } else {
                    null
                }
            ),
            activity = GitHubRepositoryActivityProfile(
                pushedAtMillis = field(pushedAtMillis)
            ),
            traffic = GitHubRepositoryTrafficProfile(
                viewCount = field(if (trafficLatestAtMillis > 0L) 10 else 0),
                latestViewBucketAtMillis = field(trafficLatestAtMillis)
            ),
            forkSync = GitHubRepositoryForkSyncProfile(
                behindBy = field(forkSyncBehindBy),
                comparedAtMillis = field(if (fork) NOW else -1L)
            ),
            security = GitHubRepositorySecurityProfile(
                dependabotAlertsAvailable = field(true),
                openDependabotAlertsCount = field(openSecurityAlerts)
            ),
            distribution = GitHubRepositoryDistributionProfile(
                hasInstallableAndroidAsset = field(hasInstallableAsset),
                apkLikeAssetCount = field(if (hasInstallableAsset) 1 else 0)
            ),
            actions = GitHubRepositoryActionsProfile(
                latestRunUpdatedAtMillis = field(latestRunUpdatedAtMillis),
                latestRunConclusion = field(latestRunConclusion)
            ),
            community = GitHubRepositoryCommunityProfile(
                hasReadme = field(hasCommunityFiles),
                hasLicense = field(hasCommunityFiles)
            ),
            localFit = GitHubRepositoryLocalFitProfile(
                packageNameMatched = packageNameMatched?.let(::field)
            )
        )
    }

    private fun <T> field(value: T): GitHubProfileField<T> {
        return GitHubProfileField(
            value = value,
            source = GitHubRepositoryProfileSource.GitHubApiRepository,
            fetchedAtMillis = NOW,
            confidence = GitHubRepositoryProfileConfidence.High
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val NOW = 1_700_000_000_000L
    }
}
