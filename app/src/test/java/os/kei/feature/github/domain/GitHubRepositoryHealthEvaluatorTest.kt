package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryHealthInput
import os.kei.feature.github.model.GitHubRepositoryHealthReason
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
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

        assertEquals(GitHubDecisionLevel.Good, health.level)
        assertTrue(GitHubRepositoryHealthReason.ForkMaintainedIndependently in health.reasons)
    }

    @Test
    fun `stale fork behind active upstream enters review`() {
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

        assertEquals(GitHubDecisionLevel.Review, health.level)
        assertTrue(GitHubRepositoryHealthReason.ForkBehindUpstream in health.reasons)
    }

    private fun baseInput(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryHealthInput {
        return GitHubRepositoryHealthInput(
            packageName = "demo.app",
            localVersion = "1.0.0",
            localVersionCode = 100L,
            hasStableRelease = true,
            hasUpdate = false,
            latestStableRawTag = "v1.0.0",
            latestStableUpdatedAtMillis = NOW - 30L * DAY_MS,
            profile = profile
        )
    }

    private fun profile(
        archived: Boolean = false,
        fork: Boolean = false,
        pushedAtMillis: Long,
        upstreamArchived: Boolean = false,
        upstreamPushedAtMillis: Long = -1L
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
