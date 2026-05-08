package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileCacheJsonTest {
    @Test
    fun `profile cache round trip preserves lifecycle and source availability`() {
        val snapshot = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = FETCHED_AT,
            lifecycle = GitHubRepositoryLifecycleProfile(
                archived = field(true),
                fork = field(true),
                upstream = GitHubRepositoryUpstreamProfile(
                    fullName = field("upstream/app"),
                    archived = field(false),
                    pushedAtMillis = field(1_699_000_000_000L)
                )
            ),
            activity = GitHubRepositoryActivityProfile(
                pushedAtMillis = field(1_700_000_000_000L)
            ),
            sourceAvailability = listOf(
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
                    fetchedAtMillis = FETCHED_AT
                )
            )
        )

        val restored = parseGitHubRepositoryProfileSnapshot(snapshot.toCacheJson())
            ?: error("profile cache should restore")

        assertEquals("demo", restored.owner)
        assertEquals("check-v2|fixture", restored.sourceConfigSignature)
        assertTrue(restored.lifecycle.archived?.value == true)
        assertEquals("upstream/app", restored.lifecycle.upstream?.fullName?.value)
        assertFalse(restored.lifecycle.upstream?.archived?.value == true)
        assertEquals(
            GitHubRepositoryProfileSource.GitHubApiRepository,
            restored.sourceAvailability.single().source
        )
    }

    @Test
    fun `profile freshness follows source signature and ttl`() {
        val snapshot = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = FETCHED_AT
        )

        assertTrue(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|fixture",
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L
            )
        )
        assertFalse(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|other",
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L
            )
        )
        assertFalse(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|fixture",
                nowMillis = FETCHED_AT + 20_000L,
                ttlMillis = 10_000L
            )
        )
    }

    private fun <T> field(value: T): GitHubProfileField<T> {
        return GitHubProfileField(
            value = value,
            source = GitHubRepositoryProfileSource.GitHubApiRepository,
            fetchedAtMillis = FETCHED_AT,
            confidence = GitHubRepositoryProfileConfidence.High
        )
    }

    private companion object {
        const val FETCHED_AT = 1_700_000_000_000L
    }
}
