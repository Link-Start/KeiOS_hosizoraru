package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileSummaryTest {
    @Test
    fun `summary filters empty profile fields and keeps source availability`() {
        val profile = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = FETCHED_AT,
            lifecycle = GitHubRepositoryLifecycleProfile(
                archived = field(false),
                fork = field(true),
                upstream = GitHubRepositoryUpstreamProfile(fullName = field("upstream/app"))
            ),
            traffic = GitHubRepositoryTrafficProfile(
                viewCount = field(9)
            ),
            security = GitHubRepositorySecurityProfile(
                openDependabotAlertsCount = field(1)
            ),
            sourceAvailability = listOf(
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
                    fetchedAtMillis = FETCHED_AT
                ),
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.CodeScanningAlertsApi,
                    status = GitHubRepositoryProfileAvailabilityStatus.Failed,
                    fetchedAtMillis = FETCHED_AT,
                    message = "forbidden"
                )
            )
        )

        val summary = profile.toSummary()

        assertEquals("demo", summary.owner)
        assertTrue(summary.rows.any { it.key == GitHubRepositoryProfileSummaryKey.ForkState })
        assertTrue(summary.rows.any { it.key == GitHubRepositoryProfileSummaryKey.TrafficViews })
        assertTrue(summary.rows.any { it.key == GitHubRepositoryProfileSummaryKey.SecurityAlerts })
        assertFalse(summary.rows.any { it.key == GitHubRepositoryProfileSummaryKey.TrafficClones })
        assertEquals(2, summary.sourceRows.size)
        assertTrue(summary.rows.any { it.key == GitHubRepositoryProfileSummaryKey.SourceFailed })
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
