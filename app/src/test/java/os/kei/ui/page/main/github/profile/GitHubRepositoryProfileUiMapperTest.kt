package os.kei.ui.page.main.github.profile

import org.junit.Test
import os.kei.R
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryProfileSummaryKey
import os.kei.feature.github.model.GitHubRepositoryProfileSummarySection
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubRepositoryProfileUiMapperTest {
    @Test
    fun `mapper exposes archived fork actions community and source trace rows`() {
        val profile = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "profile-v1|fixture",
            fetchedAtMillis = FETCHED_AT,
            lifecycle = GitHubRepositoryLifecycleProfile(
                archived = field(true),
                disabled = field(false),
                fork = field(true),
                upstream = GitHubRepositoryUpstreamProfile(
                    fullName = field("upstream/app")
                )
            ),
            actions = GitHubRepositoryActionsProfile(
                latestRunConclusion = field("failure")
            ),
            community = GitHubRepositoryCommunityProfile(
                hasReadme = field(false),
                hasLicense = field(false)
            ),
            sourceAvailability = listOf(
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
                    fetchedAtMillis = FETCHED_AT,
                    elapsedMs = 12L,
                    required = true
                ),
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.CodeScanningAlertsApi,
                    status = GitHubRepositoryProfileAvailabilityStatus.Failed,
                    fetchedAtMillis = FETCHED_AT,
                    message = "forbidden",
                    elapsedMs = 27L,
                    fromCache = true,
                    required = true
                )
            )
        )

        val ui = assertNotNull(GitHubRepositoryProfileUiMapper.build(profile))
        val lifecycle = ui.section(GitHubRepositoryProfileSummarySection.Lifecycle)
        val repositoryState = lifecycle.row(GitHubRepositoryProfileSummaryKey.RepositoryState)
        val forkState = lifecycle.row(GitHubRepositoryProfileSummaryKey.ForkState)
        val actions = ui.section(GitHubRepositoryProfileSummarySection.Actions)
            .row(GitHubRepositoryProfileSummaryKey.ActionsStatus)
        val community = ui.section(GitHubRepositoryProfileSummarySection.Community)
            .row(GitHubRepositoryProfileSummaryKey.CommunityFiles)
        val failedSource = ui.sourceRows.first {
            it.source == GitHubRepositoryProfileSource.CodeScanningAlertsApi
        }

        assertEquals(GitHubDecisionLevel.Risk, repositoryState.level)
        assertEquals(R.string.github_profile_value_archived, repositoryState.value.resId)
        assertEquals(GitHubDecisionLevel.Review, forkState.level)
        assertEquals(R.string.github_profile_value_fork_with_upstream, forkState.value.resId)
        assertEquals("upstream/app", forkState.value.args.single().raw)
        assertEquals(GitHubDecisionLevel.Risk, actions.level)
        assertEquals(R.string.github_profile_value_actions_failure, actions.value.resId)
        assertEquals(GitHubDecisionLevel.Review, community.level)
        assertEquals(R.string.github_profile_value_community_files, community.value.resId)
        assertTrue(community.value.args.all { it.resId == R.string.github_profile_value_missing })
        assertEquals(R.string.github_profile_source_code_scanning, failedSource.sourceLabelRes)
        assertEquals(R.string.github_profile_source_status_failed, failedSource.statusLabelRes)
        assertEquals(GitHubDecisionLevel.Review, failedSource.level)
        assertEquals("forbidden", failedSource.message)
        assertTrue(failedSource.fromCache)
        assertTrue(failedSource.required)
        assertEquals(R.string.github_profile_source_elapsed, failedSource.elapsed?.resId)
    }

    private fun GitHubRepositoryProfileUiSummary.section(
        section: GitHubRepositoryProfileSummarySection
    ): GitHubRepositoryProfileUiSection {
        return sections.first { it.section == section }
    }

    private fun GitHubRepositoryProfileUiSection.row(
        key: GitHubRepositoryProfileSummaryKey
    ): GitHubRepositoryProfileUiRow {
        return rows.first { it.key == key }
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
