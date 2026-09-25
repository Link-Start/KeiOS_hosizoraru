package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileSourcesTest {
    private val http = GitHubRepositoryProfileHttpClient(
        apiBaseUrl = "https://api.github.test",
        htmlBaseUrl = "https://github.test",
    )
    private val apiSource = GitHubApiRepositoryProfileSource(http)
    private val htmlSource = GitHubHtmlRepositoryProfileSource(http)
    private val communitySource = GitHubCommunityProfileSource(http)
    private val deepSource = GitHubDeepRepositoryProfileSource(http)

    @Test
    fun `api repository profile parses lifecycle fork upstream and community basics`() {
        val profile = apiSource.parse(
            json = apiRepositoryJson(archived = true, fork = true),
            fallbackOwner = "demo",
            fallbackRepo = "app",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture",
        )

        assertEquals("demo/app", profile.identity.fullName?.value)
        assertEquals("main", profile.identity.defaultBranch?.value)
        assertEquals(
            "https://avatars.githubusercontent.com/u/42?v=4",
            profile.identity.ownerAvatarUrl?.value,
        )
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
        val profile = apiSource.parse(
            json = apiRepositoryJson(
                archived = false,
                fork = false,
                disabled = true,
                includeLicense = false,
                topics = emptyList(),
            ),
            fallbackOwner = "demo",
            fallbackRepo = "app",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture",
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
                <a href="/topics/android">Android</a>
                <a href="/venera-app/venera/tree/main">main</a>
                <a href="/venera-app/venera/stargazers">1.2k stars</a>
                <relative-time datetime="2025-01-02T00:00:00Z"></relative-time>
                <div id="readme">README.md</div>
              </body>
            </html>
        """.trimIndent()

        val profile = htmlSource.parse(
            html = html,
            owner = "venera-app",
            repo = "venera",
            fetchedAtMillis = FETCHED_AT,
            sourceConfigSignature = "check-v2|fixture",
        )

        val archivedField = profile.lifecycle.archived ?: error("archived field should be present")
        assertTrue(archivedField.value)
        assertEquals(GitHubRepositoryProfileSource.HtmlRepositoryPage, archivedField.source)
        assertEquals(GitHubRepositoryProfileConfidence.Low, archivedField.confidence)
        assertEquals(listOf("android"), profile.identity.topics?.value)
        assertEquals("main", profile.identity.defaultBranch?.value)
        assertEquals(
            "https://github.test/venera-app.png?size=96",
            profile.identity.ownerAvatarUrl?.value,
        )
        assertEquals(1200, profile.activity.stargazersCount?.value)
        assertTrue(profile.community.hasReadme?.value == true)
    }

    @Test
    fun `community profile parses required files`() {
        val profile = communitySource.parse(
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
            sourceConfigSignature = "check-v2|fixture",
        )

        assertEquals(87, profile.community.healthPercentage?.value)
        assertTrue(profile.community.hasReadme?.value == true)
        assertTrue(profile.community.hasLicense?.value == true)
        assertEquals("MIT", profile.community.licenseSpdxId?.value)
        assertTrue(profile.community.hasPullRequestTemplate?.value == true)
    }

    @Test
    fun `deep profile source parses traffic compare and security signals`() {
        val views = deepSource.parseTrafficViews(
            json = """
                {
                  "count": 18,
                  "uniques": 9,
                  "views": [
                    {"timestamp": "2024-01-01T00:00:00Z", "count": 8, "uniques": 4},
                    {"timestamp": "2024-01-02T00:00:00Z", "count": 10, "uniques": 5}
                  ]
                }
            """.trimIndent(),
            fetchedAtMillis = FETCHED_AT,
        )
        val clones = deepSource.parseTrafficClones(
            json = """
                {
                  "count": 4,
                  "uniques": 2,
                  "clones": [{"timestamp": "2024-01-03T00:00:00Z", "count": 4, "uniques": 2}]
                }
            """.trimIndent(),
            fetchedAtMillis = FETCHED_AT,
        )
        val compare = deepSource.parseForkCompare(
            json = """{"ahead_by": 2, "behind_by": 7, "status": "behind", "total_commits": 9}""",
            owner = "demo",
            repo = "app",
            upstreamFullName = "upstream/app",
            fetchedAtMillis = FETCHED_AT,
        )
        val dependabot = deepSource.parseDependabotAlerts(
            json = """[{"number": 1}, {"number": 2}]""",
            fetchedAtMillis = FETCHED_AT,
        )
        val codeScanning = deepSource.parseCodeScanningAlerts(
            json = """[{"number": 3}]""",
            fetchedAtMillis = FETCHED_AT,
        )

        assertEquals(18, views.viewCount?.value)
        assertEquals(1_704_153_600_000L, views.latestViewBucketAtMillis?.value)
        assertEquals(4, clones.cloneCount?.value)
        assertEquals(7, compare.behindBy?.value)
        assertEquals("upstream/app", compare.baseFullName?.value)
        assertEquals(2, dependabot.openDependabotAlertsCount?.value)
        assertEquals(1, codeScanning.openCodeScanningAlertsCount?.value)
        assertEquals(GitHubRepositoryProfileConfidence.Medium, compare.behindBy?.confidence)
    }

    @Test
    fun `deep profile skips permission gated requests without token`() = runBlocking {
        MockWebServer().use { server ->
            repeat(5) {
                server.enqueue(MockResponse().setResponseCode(403))
            }
            val source = GitHubDeepRepositoryProfileSource(
                GitHubRepositoryProfileHttpClient(
                    apiBaseUrl = server.url("/").toString(),
                    htmlBaseUrl = server.url("/").toString(),
                ),
            )

            val result = source.fetch(
                owner = "demo",
                repo = "app",
                apiToken = "",
                identity = GitHubRepositoryIdentityProfile(),
                lifecycle = GitHubRepositoryLifecycleProfile(),
                fetchedAtMillis = FETCHED_AT,
            )

            assertEquals(0, server.requestCount)
            val gatedStates = result.availability.filter { state ->
                state.source in setOf(
                    GitHubRepositoryProfileSource.TrafficViewsApi,
                    GitHubRepositoryProfileSource.TrafficClonesApi,
                    GitHubRepositoryProfileSource.DependabotAlertsApi,
                    GitHubRepositoryProfileSource.CodeScanningAlertsApi,
                )
            }
            assertEquals(4, gatedStates.size)
            assertTrue(gatedStates.all { state ->
                state.status == GitHubRepositoryProfileAvailabilityStatus.Skipped
            })
            assertTrue(result.security.dependabotAlertsAvailable?.value == false)
            assertTrue(result.security.codeScanningAvailable?.value == false)
        }
    }

    @Test
    fun `local fit compares package and version evidence`() {
        val matched = GitHubLocalFitProfileSource.build(
            localPackageName = "os.kei.demo",
            localVersionName = "1.0.0",
            localVersionCode = 100L,
            preciseStableApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "os.kei.demo",
                versionName = "1.1.0",
                versionCode = "110",
            ),
            precisePreReleaseApkVersion = null,
            fetchedAtMillis = FETCHED_AT,
        )
        val mismatched = GitHubLocalFitProfileSource.build(
            localPackageName = "os.kei.demo",
            localVersionName = "1.0.0",
            localVersionCode = 100L,
            preciseStableApkVersion = null,
            precisePreReleaseApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "os.kei.other",
                versionName = "2.0.0-beta1",
                versionCode = "200",
            ),
            fetchedAtMillis = FETCHED_AT,
        )

        assertTrue(matched.packageNameMatched?.value == true)
        assertEquals("1.1.0", matched.remoteVersionName?.value)
        assertEquals(110L, matched.remoteVersionCode?.value)
        assertFalse(mismatched.packageNameMatched?.value == true)
        assertEquals(GitHubRepositoryProfileSource.LocalInstall, mismatched.packageNameMatched?.source)
    }

    private fun apiRepositoryJson(
        archived: Boolean,
        fork: Boolean,
        disabled: Boolean = false,
        includeLicense: Boolean = true,
        topics: List<String> = listOf("android", "compose"),
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
              "owner": {
                "login": "demo",
                "type": "Organization",
                "avatar_url": "https://avatars.githubusercontent.com/u/42?v=4"
              },
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
        private const val FETCHED_AT = 1_735_689_600_000L
    }
}
