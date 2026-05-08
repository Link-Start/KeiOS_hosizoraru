package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileCapability
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileRepositoryTest {
    private val http = GitHubRepositoryProfileHttpClient(
        apiBaseUrl = "https://api.github.test",
        htmlBaseUrl = "https://github.test"
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
        val profile = apiSource.parse(
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
            sourceConfigSignature = "check-v2|fixture"
        )

        val archivedField = profile.lifecycle.archived ?: error("archived field should be present")
        assertTrue(archivedField.value)
        assertEquals(GitHubRepositoryProfileSource.HtmlRepositoryPage, archivedField.source)
        assertEquals(GitHubRepositoryProfileConfidence.Low, archivedField.confidence)
        assertEquals(listOf("android"), profile.identity.topics?.value)
        assertEquals("main", profile.identity.defaultBranch?.value)
        assertEquals(1200, profile.activity.stargazersCount?.value)
        assertTrue(profile.community.hasReadme?.value == true)
    }

    @Test
    fun `release profile keeps atom and api signals in the same shape`() {
        val atomProfile = GitHubReleaseProfileSource.build(
            snapshot = releaseSnapshot(
                strategyId = "atom_feed",
                source = GitHubReleaseSignalSource.AtomEntry
            ),
            fetchedAtMillis = FETCHED_AT
        )
        val apiProfile = GitHubReleaseProfileSource.build(
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
        val redirectProfile = GitHubReleaseProfileSource.build(
            snapshot = releaseSnapshot(
                strategyId = "atom_feed",
                source = GitHubReleaseSignalSource.LatestRedirect
            ),
            fetchedAtMillis = FETCHED_AT
        )
        assertEquals(
            GitHubRepositoryProfileSource.HtmlLatestReleaseRedirect,
            redirectProfile.latestStableTag?.source
        )
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
            sourceConfigSignature = "check-v2|fixture"
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
            fetchedAtMillis = FETCHED_AT
        )
        val clones = deepSource.parseTrafficClones(
            json = """
                {
                  "count": 4,
                  "uniques": 2,
                  "clones": [{"timestamp": "2024-01-03T00:00:00Z", "count": 4, "uniques": 2}]
                }
            """.trimIndent(),
            fetchedAtMillis = FETCHED_AT
        )
        val compare = deepSource.parseForkCompare(
            json = """{"ahead_by": 2, "behind_by": 7, "status": "behind", "total_commits": 9}""",
            owner = "demo",
            repo = "app",
            upstreamFullName = "upstream/app",
            fetchedAtMillis = FETCHED_AT
        )
        val dependabot = deepSource.parseDependabotAlerts(
            json = """[{"number": 1}, {"number": 2}]""",
            fetchedAtMillis = FETCHED_AT
        )
        val codeScanning = deepSource.parseCodeScanningAlerts(
            json = """[{"number": 3}]""",
            fetchedAtMillis = FETCHED_AT
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
    fun `version check fast requests only core repository sources`() {
        MockWebServer().use { server ->
            server.dispatcher = profileDispatcher()
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = "https://github.test"
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Basic),
                    releaseSnapshot = releaseSnapshot(
                        strategyId = "atom_feed",
                        source = GitHubReleaseSignalSource.LatestRedirect
                    )
                )
            )

            val paths = server.takeRequestPaths()
            assertContains(paths, "/repos/demo/app")
            assertFalse(paths.any { it.contains("/actions/") })
            assertFalse(paths.any { it.contains("/community/profile") })
            assertFalse(paths.any { it.contains("/traffic/") })
            assertFalse(paths.any { it.contains("/dependabot/") })
            assertFalse(paths.any { it.contains("/code-scanning/") })
            assertEquals(GitHubRepositoryProfilePurpose.VersionCheckFast, profile.purpose)
            assertTrue(
                profile.capabilities.containsAll(
                    setOf(
                        GitHubRepositoryProfileCapability.RepositoryCore,
                        GitHubRepositoryProfileCapability.ReleaseSignals,
                        GitHubRepositoryProfileCapability.LocalFit
                    )
                )
            )
            assertFalse(GitHubRepositoryProfileCapability.Actions in profile.capabilities)
            assertTrue(
                profile.sourceAvailability.any {
                    it.source == GitHubRepositoryProfileSource.HtmlLatestReleaseRedirect
                }
            )
        }
    }

    @Test
    fun `health card requests actions and community while skipping deep endpoints`() {
        MockWebServer().use { server ->
            server.dispatcher = profileDispatcher()
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = server.url("/").toString().trimEnd('/')
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep),
                    purpose = GitHubRepositoryProfilePurpose.HealthCard
                )
            )

            val paths = server.takeRequestPaths()
            assertContains(paths, "/repos/demo/app/actions/runs?per_page=12")
            assertContains(paths, "/repos/demo/app/actions/artifacts?per_page=30")
            assertContains(paths, "/repos/demo/app/community/profile")
            assertFalse(paths.any { it.contains("/traffic/") })
            assertFalse(paths.any { it.contains("/dependabot/") })
            assertTrue(GitHubRepositoryProfileCapability.Actions in profile.capabilities)
            assertTrue(GitHubRepositoryProfileCapability.Community in profile.capabilities)
            assertFalse(GitHubRepositoryProfileCapability.Security in profile.capabilities)
        }
    }

    @Test
    fun `api failure falls back to html repository page with low confidence fields`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/repos/demo/app" -> MockResponse()
                            .setResponseCode(500)
                            .setBody("""{"message":"temporary"}""")

                        "/demo/app" -> htmlResponse(
                            """
                                <html>
                                  <body>
                                    <div>This repository has been archived by the owner.</div>
                                    <a href="/topics/android">Android</a>
                                    <div id="readme">README.md</div>
                                  </body>
                                </html>
                            """.trimIndent()
                        )

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = server.url("/").toString().trimEnd('/')
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig()
                )
            )

            val archived = profile.lifecycle.archived ?: error("archived field should exist")
            assertTrue(archived.value)
            assertEquals(
                GitHubRepositoryProfileConfidence.Low,
                archived.confidence
            )
            assertEquals(listOf("android"), profile.identity.topics?.value)
            assertTrue(
                profile.sourceAvailability.any {
                    it.source == GitHubRepositoryProfileSource.GitHubApiRepository &&
                            it.status == GitHubRepositoryProfileAvailabilityStatus.Failed &&
                            it.required
                }
            )
        }
    }

    @Test
    fun `detail full deep profile collects enhanced endpoints and keeps partial failures`() {
        MockWebServer().use { server ->
            server.dispatcher = profileDispatcher(fork = true, codeScanningCode = 403)
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = server.url("/").toString().trimEnd('/')
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep),
                    purpose = GitHubRepositoryProfilePurpose.DetailFull
                )
            )

            val paths = server.takeRequestPaths()
            assertContains(paths, "/demo/app")
            assertContains(paths, "/repos/demo/app/traffic/views")
            assertContains(paths, "/repos/demo/app/traffic/clones")
            assertContains(paths, "/repos/upstream/app/compare/main...demo:main")
            assertContains(paths, "/repos/demo/app/dependabot/alerts?state=open&per_page=100")
            assertContains(paths, "/repos/demo/app/code-scanning/alerts?state=open&per_page=100")
            assertEquals(11, profile.traffic.viewCount?.value)
            assertEquals(1, profile.security.openDependabotAlertsCount?.value)
            assertFalse(profile.security.codeScanningAvailable?.value == true)
            assertTrue(
                profile.sourceAvailability.any {
                    it.source == GitHubRepositoryProfileSource.CodeScanningAlertsApi &&
                            it.status == GitHubRepositoryProfileAvailabilityStatus.Failed
                }
            )
            assertTrue(GitHubRepositoryProfileCapability.Security in profile.capabilities)
        }
    }

    @Test
    fun `manual deep refresh follows basic or deep profile depth`() {
        MockWebServer().use { server ->
            server.dispatcher = profileDispatcher(fork = true)
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = server.url("/").toString().trimEnd('/')
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Basic),
                    purpose = GitHubRepositoryProfilePurpose.ManualDeepRefresh
                )
            )

            val paths = server.takeRequestPaths()
            assertContains(paths, "/demo/app")
            assertFalse(paths.any { it.contains("/traffic/") })
            assertFalse(paths.any { it.contains("/dependabot/") })
            assertEquals(GitHubRepositoryProfilePurpose.ManualDeepRefresh, profile.purpose)
            assertFalse(GitHubRepositoryProfileCapability.Security in profile.capabilities)
        }

        MockWebServer().use { server ->
            server.dispatcher = profileDispatcher(fork = true)
            val repository = GitHubRepositoryProfileRepository(
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
                htmlBaseUrl = server.url("/").toString().trimEnd('/')
            )

            val profile = repository.fetchProfile(
                GitHubRepositoryProfileRequest(
                    owner = "demo",
                    repo = "app",
                    lookupConfig = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep),
                    purpose = GitHubRepositoryProfilePurpose.ManualDeepRefresh
                )
            )

            val paths = server.takeRequestPaths()
            assertContains(paths, "/repos/demo/app/traffic/views")
            assertContains(paths, "/repos/demo/app/dependabot/alerts?state=open&per_page=100")
            assertTrue(GitHubRepositoryProfileCapability.Security in profile.capabilities)
        }
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

    private fun profileDispatcher(
        fork: Boolean = false,
        codeScanningCode: Int = 200
    ): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/repos/demo/app" -> jsonResponse(
                        apiRepositoryJson(
                            archived = false,
                            fork = fork
                        )
                    )

                    "/demo/app" -> htmlResponse(
                        """
                            <html>
                              <body>
                                <a href="/topics/android">Android</a>
                                <a href="/demo/app/tree/main">main</a>
                                <div id="readme">README.md</div>
                              </body>
                            </html>
                        """.trimIndent()
                    )

                    "/repos/demo/app/actions/runs?per_page=12" -> jsonResponse("""{"workflow_runs": []}""")
                    "/repos/demo/app/actions/artifacts?per_page=30" -> jsonResponse("""{"artifacts": []}""")
                    "/repos/demo/app/community/profile" -> jsonResponse(
                        """{"health_percentage": 80, "files": {"readme": {"name": "README.md"}}}"""
                    )

                    "/repos/demo/app/traffic/views" -> jsonResponse("""{"count": 11, "uniques": 5, "views": []}""")
                    "/repos/demo/app/traffic/clones" -> jsonResponse("""{"count": 3, "uniques": 2, "clones": []}""")
                    "/repos/upstream/app/compare/main...demo:main" -> jsonResponse(
                        """{"ahead_by": 1, "behind_by": 2, "status": "behind", "total_commits": 3}"""
                    )
                    "/repos/demo/app/dependabot/alerts?state=open&per_page=100" -> jsonResponse("""[{"number": 1}]""")
                    "/repos/demo/app/code-scanning/alerts?state=open&per_page=100" -> if (codeScanningCode == 200) {
                        jsonResponse("[]")
                    } else {
                        MockResponse().setResponseCode(codeScanningCode)
                            .setBody("""{"message":"forbidden"}""")
                    }

                    else -> MockResponse().setResponseCode(404).setBody("""{"message":"missing"}""")
                }
            }
        }
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun htmlResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html")
            .setBody(body)
    }

    private fun MockWebServer.takeRequestPaths(): List<String> {
        return buildList {
            repeat(requestCount) {
                add(takeRequest().path.orEmpty())
            }
        }
    }

    private companion object {
        const val FETCHED_AT = 1_700_000_100_000L
    }
}
