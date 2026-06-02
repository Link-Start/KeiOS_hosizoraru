package os.kei.feature.github.data.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import os.kei.feature.github.model.GitRepositoryPlatform
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitRepositoryReleaseStrategyTest {
    @After
    fun tearDown() {
        buildGitRepositoryTrackIdentity("https://example.com/demo/app")
            ?.let { GitRepositoryReleaseStrategy(it).clearCaches() }
    }

    @Test
    fun `gitlab release api builds stable and prerelease snapshot`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(gitLabReleasesJson())
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]")
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitlab.com/group/subgroup/app.git")
            )
            val strategy = GitRepositoryReleaseStrategy(
                identity = identity,
                gitLabApiBaseUrl = server.url("/api/v4").toString()
            )

            val snapshot = strategy.loadSnapshot(identity.owner, identity.repo).getOrThrow()

            assertEquals("git_repository_gitlab", snapshot.strategyId)
            assertEquals(GitRepositoryPlatform.GitLab, identity.platform)
            assertEquals(true, snapshot.hasStableRelease)
            assertEquals("v1.2.0", snapshot.latestStable.rawTag)
            assertEquals("v1.3.0-beta.1", snapshot.latestPreRelease?.rawTag)
            assertEquals(2, snapshot.feed.entries.size)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v4/projects/group%2Fsubgroup%2Fapp/releases")
            )
        }
    }

    @Test
    fun `gitee strategy merges releases and newer tags`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeProntoReleasesJson())
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeProntoTagsJson())
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitee.com/hugedog233/Pronto")
            )
            val strategy = GitRepositoryReleaseStrategy(
                identity = identity,
                giteeApiBaseUrl = server.url("/api/v5").toString()
            )

            val snapshot = strategy.loadSnapshot(identity.owner, identity.repo).getOrThrow()

            assertEquals("git_repository_gitee", snapshot.strategyId)
            assertEquals(true, snapshot.hasStableRelease)
            assertEquals("2.5.1", snapshot.latestStable.rawTag)
            assertEquals("v2.0.7", snapshot.latestPreRelease?.rawTag)
            assertEquals(3, snapshot.feed.entries.size)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/releases?per_page=30&direction=desc")
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/tags?per_page=30&direction=desc")
            )
        }
    }

    @Test
    fun `gitee strategy falls back to tags when releases are empty`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeTagsJson())
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitee.com/demo/app.git")
            )
            val strategy = GitRepositoryReleaseStrategy(
                identity = identity,
                giteeApiBaseUrl = server.url("/api/v5").toString()
            )

            val snapshot = strategy.loadSnapshot(identity.owner, identity.repo).getOrThrow()

            assertEquals("git_repository_gitee", snapshot.strategyId)
            assertEquals(true, snapshot.hasStableRelease)
            assertEquals("v2.0.0", snapshot.latestStable.rawTag)
            assertEquals(2, snapshot.feed.entries.size)
            assertTrue(server.takeRequest().path.orEmpty().contains("/api/v5/repos/demo/app/releases"))
            assertTrue(server.takeRequest().path.orEmpty().contains("/api/v5/repos/demo/app/tags"))
        }
    }

    @Test
    fun `gitea release api builds stable and prerelease snapshot`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteaReleasesJson())
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]")
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitea.com/gitea/tea")
            )
            val strategy = GitRepositoryReleaseStrategy(
                identity = identity,
                giteaApiBaseUrl = server.url("/api/v1").toString()
            )

            val snapshot = strategy.loadSnapshot(identity.owner, identity.repo).getOrThrow()

            assertEquals("git_repository_gitea", snapshot.strategyId)
            assertEquals(GitRepositoryPlatform.Gitea, identity.platform)
            assertEquals(true, snapshot.hasStableRelease)
            assertEquals("v0.14.1", snapshot.latestStable.rawTag)
            assertEquals("v0.15.0-rc1", snapshot.latestPreRelease?.rawTag)
            assertEquals(2, snapshot.feed.entries.size)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v1/repos/gitea/tea/releases?limit=30")
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v1/repos/gitea/tea/tags?limit=30")
            )
        }
    }

    @Test
    fun `generic strategy parses smart http tag refs`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        001e# service=git-upload-pack
                        0000003fb31 refs/tags/v1.0.0
                        0040b32 refs/tags/v1.1.0-beta.1
                        003fc33 refs/tags/v1.2.0
                        0044c33 refs/tags/v1.2.0^{}
                        0000
                        """.trimIndent()
                    )
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://git.example.com/demo/app.git")
            )
            val strategy = GitRepositoryReleaseStrategy(
                identity = identity,
                genericRepositoryBaseUrl = server.url("/demo/app.git").toString()
            )

            val snapshot = strategy.loadSnapshot(identity.owner, identity.repo).getOrThrow()

            assertEquals("git_repository_generic", snapshot.strategyId)
            assertEquals(true, snapshot.hasStableRelease)
            assertEquals("v1.2.0", snapshot.latestStable.rawTag)
            assertEquals("v1.1.0-beta.1", snapshot.latestPreRelease?.rawTag)
            assertEquals(3, snapshot.feed.entries.size)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/demo/app.git/info/refs?service=git-upload-pack")
            )
        }
    }

    private fun gitLabReleasesJson(): String {
        return """
            [
              {
                "name": "Version 1.3.0 Beta 1",
                "tag_name": "v1.3.0-beta.1",
                "description": "Preview build",
                "released_at": "2026-05-21T08:00:00Z",
                "_links": {
                  "self": "https://gitlab.com/group/subgroup/app/-/releases/v1.3.0-beta.1"
                },
                "author": {
                  "username": "demo",
                  "avatar_url": "https://gitlab.com/uploads/avatar.png"
                }
              },
              {
                "name": "Version 1.2.0",
                "tag_name": "v1.2.0",
                "description": "Stable build",
                "released_at": "2026-05-20T08:00:00Z",
                "_links": {
                  "self": "https://gitlab.com/group/subgroup/app/-/releases/v1.2.0"
                },
                "author": {
                  "username": "demo",
                  "avatar_url": "https://gitlab.com/uploads/avatar.png"
                }
              }
            ]
        """.trimIndent()
    }

    private fun giteeProntoReleasesJson(): String {
        return """
            [
              {
                "name": "大狗记 v2.0.8",
                "tag_name": "v2.0.8",
                "body": "Stable build",
                "prerelease": false,
                "created_at": "2026-03-25T15:41:12+08:00",
                "author": {
                  "login": "hugedog233",
                  "avatar_url": "https://gitee.com/assets/no_portrait.png"
                }
              },
              {
                "name": "大狗记 v2.0.7 Beta",
                "tag_name": "v2.0.7",
                "body": "Beta build",
                "prerelease": true,
                "created_at": "2026-03-24T17:37:12+08:00",
                "author": {
                  "login": "hugedog233",
                  "avatar_url": "https://gitee.com/assets/no_portrait.png"
                }
              }
            ]
        """.trimIndent()
    }

    private fun giteeProntoTagsJson(): String {
        return """
            [
              {
                "name": "2.5.1",
                "message": "v2.5.1 stable update",
                "commit": {
                  "sha": "bf9eb3b60cd0f9aa64a9bf9ea694c806a7bb2252",
                  "date": "2026-03-07T18:51:13+08:00"
                },
                "tagger": {
                  "name": "mt",
                  "date": "2026-05-07T15:14:57+00:00"
                }
              }
            ]
        """.trimIndent()
    }

    private fun giteeTagsJson(): String {
        return """
            [
              {
                "name": "v2.0.0",
                "message": "Stable build",
                "commit": {
                  "id": "abc",
                  "committed_date": "2026-05-22T08:00:00Z"
                }
              },
              {
                "name": "v2.1.0-rc1",
                "message": "Release candidate",
                "commit": {
                  "id": "def",
                  "committed_date": "2026-05-21T08:00:00Z"
                }
              }
            ]
        """.trimIndent()
    }

    private fun giteaReleasesJson(): String {
        return """
            [
              {
                "id": 869127,
                "name": "v0.15.0-rc1",
                "tag_name": "v0.15.0-rc1",
                "body": "Release candidate build",
                "html_url": "https://gitea.com/gitea/tea/releases/tag/v0.15.0-rc1",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-05-23T09:00:00Z"
              },
              {
                "id": 869126,
                "name": "v0.14.1",
                "tag_name": "v0.14.1",
                "body": "Stable release notes",
                "html_url": "https://gitea.com/gitea/tea/releases/tag/v0.14.1",
                "prerelease": false,
                "draft": false,
                "published_at": "2026-05-22T09:00:00Z"
              }
            ]
        """.trimIndent()
    }
}
