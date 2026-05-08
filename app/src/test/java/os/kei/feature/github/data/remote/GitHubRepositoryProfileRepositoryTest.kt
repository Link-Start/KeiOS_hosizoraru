package os.kei.feature.github.data.remote

import org.junit.Test
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileRepositoryTest {
    private val repository = GitHubRepositoryProfileRepository()

    @Test
    fun `api repository profile parses lifecycle fork upstream and community basics`() {
        val profile = repository.parseApiRepositoryProfile(
            json = apiRepositoryJson(archived = true, fork = true),
            fallbackOwner = "demo",
            fallbackRepo = "app",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture"
        )

        assertEquals("demo/app", profile.identity.fullName?.value)
        assertEquals("main", profile.identity.defaultBranch?.value)
        assertTrue(profile.lifecycle.archived?.value == true)
        assertTrue(profile.lifecycle.fork?.value == true)
        assertEquals("upstream/app", profile.lifecycle.upstream?.fullName?.value)
        assertTrue(profile.lifecycle.upstream?.archived?.value == true)
        assertEquals(1_696_118_400_000L, profile.activity.pushedAtMillis?.value)
        assertEquals(listOf("android", "compose"), profile.identity.topics?.value)
        assertTrue(profile.community.hasLicense?.value == true)
    }

    @Test
    fun `api repository profile handles disabled repo without license or topics`() {
        val profile = repository.parseApiRepositoryProfile(
            json = apiRepositoryJson(
                archived = false,
                fork = false,
                disabled = true,
                includeLicense = false,
                topics = emptyList()
            ),
            fallbackOwner = "demo",
            fallbackRepo = "app",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture"
        )

        assertTrue(profile.lifecycle.disabled?.value == true)
        assertFalse(profile.lifecycle.archived?.value == true)
        assertEquals(null, profile.identity.topics)
        assertFalse(profile.community.hasLicense?.value == true)
    }

    @Test
    fun `html repository profile marks archived banner as low confidence`() {
        val html = """
            <html>
              <body>
                <div class="flash-warn">This repository has been archived by the owner on Jan 1, 2025. It is now read-only.</div>
              </body>
            </html>
        """.trimIndent()

        val profile = repository.parseHtmlRepositoryProfile(
            html = html,
            owner = "venera-app",
            repo = "venera",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture"
        )

        val archivedField = profile.lifecycle.archived ?: error("archived field should be present")
        assertTrue(archivedField.value)
        assertEquals(GitHubRepositoryProfileSource.HtmlRepositoryPage, archivedField.source)
        assertEquals(GitHubRepositoryProfileConfidence.Low, archivedField.confidence)
    }

    @Test
    fun `release profile keeps atom and api signals in the same shape`() {
        val atomProfile = repository.buildReleasesProfileFromSnapshot(
            snapshot = releaseSnapshot(
                strategyId = "atom_feed",
                source = GitHubReleaseSignalSource.AtomEntry
            ),
            fetchedAtMillis = FETCHED_AT
        )
        val apiProfile = repository.buildReleasesProfileFromSnapshot(
            snapshot = releaseSnapshot(
                strategyId = "github_api_token",
                source = GitHubReleaseSignalSource.GitHubApi
            ),
            fetchedAtMillis = FETCHED_AT
        )

        assertEquals(atomProfile.hasStableRelease?.value, apiProfile.hasStableRelease?.value)
        assertEquals(atomProfile.latestStableTag?.value, apiProfile.latestStableTag?.value)
        assertEquals(atomProfile.latestPreReleaseTag?.value, apiProfile.latestPreReleaseTag?.value)
        assertEquals(
            GitHubRepositoryProfileSource.AtomReleaseFeed,
            atomProfile.latestStableTag?.source
        )
        assertEquals(
            GitHubRepositoryProfileSource.GitHubApiReleases,
            apiProfile.latestStableTag?.source
        )
    }

    @Test
    fun `community profile parses required files`() {
        val profile = repository.parseCommunityProfile(
            json = """
                {
                  "health_percentage": 87,
                  "files": {
                    "readme": {"name": "README.md"},
                    "license": {"name": "MIT License", "spdx_id": "MIT"},
                    "contributing": {"name": "CONTRIBUTING.md"},
                    "code_of_conduct": {"name": "CODE_OF_CONDUCT.md"},
                    "issue_template": {"name": "bug_report.md"},
                    "pull_request_template": {"name": "pull_request_template.md"}
                  }
                }
            """.trimIndent(),
            owner = "demo",
            repo = "app",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture"
        )

        assertEquals(87, profile.community.healthPercentage?.value)
        assertTrue(profile.community.hasReadme?.value == true)
        assertTrue(profile.community.hasLicense?.value == true)
        assertEquals("MIT", profile.community.licenseSpdxId?.value)
        assertTrue(profile.community.hasPullRequestTemplate?.value == true)
    }

    private fun releaseSnapshot(
        strategyId: String,
        source: GitHubReleaseSignalSource
    ): GitHubRepositoryReleaseSnapshot {
        return GitHubRepositoryReleaseSnapshot(
            strategyId = strategyId,
            feed = GitHubAtomFeed(title = "demo/app releases"),
            latestStable = releaseSignal("v1.2.0", source),
            hasStableRelease = true,
            latestPreRelease = releaseSignal("v1.3.0-beta1", source)
        )
    }

    private fun releaseSignal(
        tag: String,
        source: GitHubReleaseSignalSource
    ): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = tag,
            rawTag = tag,
            rawName = tag,
            updatedAtMillis = 1_700_000_000_000L,
            source = source,
            authorName = "maintainer"
        )
    }

    private fun apiRepositoryJson(
        archived: Boolean,
        fork: Boolean,
        disabled: Boolean = false,
        includeLicense: Boolean = true,
        topics: List<String> = listOf("android", "compose")
    ): String {
        val topicsJson = topics.joinToString(",") { "\"$it\"" }
        val licenseJson = if (includeLicense) {
            """"license": {"name": "Apache License 2.0", "spdx_id": "Apache-2.0"},"""
        } else {
            """"license": null,"""
        }
        return """
            {
              "name": "app",
              "full_name": "demo/app",
              "html_url": "https://github.com/demo/app",
              "default_branch": "main",
              "visibility": "public",
              "private": false,
              "archived": $archived,
              "disabled": $disabled,
              "fork": $fork,
              "mirror_url": null,
              "created_at": "2020-01-01T00:00:00Z",
              "updated_at": "2024-01-01T00:00:00Z",
              "pushed_at": "2023-10-01T00:00:00Z",
              "stargazers_count": 1200,
              "forks_count": 45,
              "watchers_count": 1200,
              "subscribers_count": 80,
              "open_issues_count": 12,
              "size": 2048,
              "topics": [$topicsJson],
              $licenseJson
              "owner": {"login": "demo", "type": "Organization"},
              "parent": {
                "full_name": "upstream/app",
                "html_url": "https://github.com/upstream/app",
                "archived": true,
                "disabled": false,
                "pushed_at": "2022-01-01T00:00:00Z",
                "default_branch": "main"
              }
            }
        """.trimIndent()
    }

    private companion object {
        const val FETCHED_AT = 1_700_000_100_000L
    }
}
