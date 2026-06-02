package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitRepositoryPreciseApkVersionSourceTest {
    @Test
    fun `gitee source falls back to release list and matches tag without leading version prefix`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"message":"not found"}""")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeProntoReleaseListJson(server))
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitee.com/hugedog233/Pronto")
            )
            val source = GitRepositoryPreciseApkVersionSource(
                identity = identity,
                giteeApiBaseUrl = server.url("/api/v5").toString()
            )

            val bundle = runBlocking {
                source.loadReleaseAssetBundle(
                    owner = identity.owner,
                    repo = identity.repo,
                    rawTag = "2.5.1",
                    releaseUrl = "https://gitee.com/hugedog233/Pronto/releases/tag/v2.5.1",
                    lookupConfig = GitHubLookupConfig(preciseApkVersionEnabled = true)
                )
            }.getOrThrow()

            assertEquals("大狗记 v2.5.1", bundle.releaseName)
            assertEquals("v2.5.1", bundle.tagName)
            assertEquals("gitee-api", bundle.fetchSource)
            assertEquals(1, bundle.assets.size)
            assertEquals("Pronto_release_v2.5.1.apk", bundle.assets.single().name)
            assertEquals(
                server.url("/downloads/Pronto_release_v2.5.1.apk").toString(),
                bundle.assets.single().downloadUrl
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/releases/tags/2.5.1")
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/releases?per_page=100&direction=desc")
            )
        }
    }

    @Test
    fun `gitlab source reads release asset links`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(gitLabReleaseJson(server))
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitlab.com/group/subgroup/app.git")
            )
            val source = GitRepositoryPreciseApkVersionSource(
                identity = identity,
                gitLabApiBaseUrl = server.url("/api/v4").toString()
            )

            val bundle = runBlocking {
                source.loadReleaseAssetBundle(
                    owner = identity.owner,
                    repo = identity.repo,
                    rawTag = "v1.2.0",
                    releaseUrl = "https://gitlab.com/group/subgroup/app/-/releases/v1.2.0",
                    lookupConfig = GitHubLookupConfig(preciseApkVersionEnabled = true)
                )
            }.getOrThrow()

            assertEquals("Version 1.2.0", bundle.releaseName)
            assertEquals("v1.2.0", bundle.tagName)
            assertEquals("gitlab-api", bundle.fetchSource)
            assertEquals(1, bundle.assets.size)
            assertEquals("demo-release.apk", bundle.assets.first().name)
            assertEquals(server.url("/downloads/demo-release.apk").toString(), bundle.assets.first().downloadUrl)
            assertEquals("package", bundle.assets.first().contentType)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v4/projects/group%2Fsubgroup%2Fapp/releases/v1.2.0")
            )
        }
    }

    @Test
    fun `gitee release asset source exposes release notes targets and body`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeProntoReleaseTargetsJson(server))
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteeProntoReleaseDetailJson(server))
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitee.com/hugedog233/Pronto")
            )
            val source = GitRepositoryReleaseAssetSource(
                identity = identity,
                giteeApiBaseUrl = server.url("/api/v5").toString()
            )

            val targets = source.fetchReleaseNotesTargets().getOrThrow()
            val bundle = runBlocking {
                source.loadReleaseAssetBundle(
                    rawTag = targets.first().tagName,
                    releaseUrl = targets.first().htmlUrl,
                    lookupConfig = GitHubLookupConfig(preciseApkVersionEnabled = true),
                    includeAllAssets = true
                )
            }.getOrThrow()

            assertEquals("v2.7.2", targets.first().tagName)
            assertEquals("大狗记 v2.7.2", targets.first().releaseName)
            assertEquals(true, targets.first().latestInChannel)
            assertEquals(false, targets.first().prerelease)
            assertEquals("gitee-api", bundle.fetchSource)
            assertTrue("AgentChat图片关闭按钮显示异常" in bundle.releaseNotesBody)
            assertEquals(1, bundle.assets.size)
            assertEquals("Pronto_release_v2.7.2.apk", bundle.assets.single().name)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/releases?per_page=30&direction=desc")
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v5/repos/hugedog233/Pronto/releases/tags/v2.7.2")
            )
        }
    }

    @Test
    fun `gitea release asset source reads release notes and assets`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteaReleaseListJson(server))
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(giteaReleaseDetailJson(server))
            )
            val identity = assertNotNull(
                buildGitRepositoryTrackIdentity("https://gitea.com/gitea/tea")
            )
            val source = GitRepositoryReleaseAssetSource(
                identity = identity,
                giteaApiBaseUrl = server.url("/api/v1").toString()
            )

            val targets = source.fetchReleaseNotesTargets().getOrThrow()
            val bundle = runBlocking {
                source.loadReleaseAssetBundle(
                    rawTag = "v0.14.1",
                    releaseUrl = targets.first().htmlUrl,
                    lookupConfig = GitHubLookupConfig(preciseApkVersionEnabled = true),
                    includeAllAssets = true
                )
            }.getOrThrow()

            assertEquals("v0.14.1", targets.first().tagName)
            assertEquals("v0.15.0-rc1", targets.last().tagName)
            assertEquals(false, targets.first().prerelease)
            assertEquals(true, targets.last().prerelease)
            assertEquals("gitea-api", bundle.fetchSource)
            assertTrue("What's Changed" in bundle.releaseNotesBody)
            assertEquals(2, bundle.assets.size)
            assertEquals("tea-v0.14.1-android.apk", bundle.assets.first().name)
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v1/repos/gitea/tea/releases?limit=30")
            )
            assertTrue(
                server.takeRequest().path.orEmpty()
                    .contains("/api/v1/repos/gitea/tea/releases/tags/v0.14.1")
            )
        }
    }

    private fun giteeProntoReleaseListJson(server: MockWebServer): String {
        return """
            [
              {
                "name": "大狗记 v2.5.1",
                "tag_name": "v2.5.1",
                "body": "Stable build",
                "created_at": "2026-05-29T15:41:12Z",
                "html_url": "https://gitee.com/hugedog233/Pronto/releases/tag/v2.5.1",
                "assets": [
                  {
                    "name": "Pronto_release_v2.5.1.apk",
                    "browser_download_url": "${server.url("/downloads/Pronto_release_v2.5.1.apk")}",
                    "size": 7340032,
                    "download_count": 12,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              }
            ]
        """.trimIndent()
    }

    private fun giteeProntoReleaseTargetsJson(server: MockWebServer): String {
        return """
            [
              {
                "name": "大狗记 v2.7.2",
                "tag_name": "v2.7.2",
                "body": "欢迎使用大狗记，本次更新新增了超长图的保存与读取。\n\n**修复** AgentChat图片关闭按钮显示异常的问题",
                "prerelease": false,
                "created_at": "2026-05-28T19:55:00+08:00",
                "html_url": "https://gitee.com/hugedog233/Pronto/releases/tag/v2.7.2",
                "assets": [
                  {
                    "name": "Pronto_release_v2.7.2.apk",
                    "browser_download_url": "${server.url("/downloads/Pronto_release_v2.7.2.apk")}",
                    "size": 7340032,
                    "download_count": 12,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              }
            ]
        """.trimIndent()
    }

    private fun giteeProntoReleaseDetailJson(server: MockWebServer): String {
        return """
            {
              "name": "大狗记 v2.7.2",
              "tag_name": "v2.7.2",
              "body": "欢迎使用大狗记，本次更新新增了超长图的保存与读取。\n\n**修复** AgentChat图片关闭按钮显示异常的问题",
              "prerelease": false,
              "created_at": "2026-05-28T19:55:00+08:00",
              "html_url": "https://gitee.com/hugedog233/Pronto/releases/tag/v2.7.2",
              "assets": [
                {
                  "name": "Pronto_release_v2.7.2.apk",
                  "browser_download_url": "${server.url("/downloads/Pronto_release_v2.7.2.apk")}",
                  "size": 7340032,
                  "download_count": 12,
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()
    }

    private fun gitLabReleaseJson(server: MockWebServer): String {
        return """
            {
              "name": "Version 1.2.0",
              "tag_name": "v1.2.0",
              "description": "Stable build",
              "released_at": "2026-05-20T08:00:00Z",
              "_links": {
                "self": "https://gitlab.com/group/subgroup/app/-/releases/v1.2.0"
              },
              "commit": {
                "short_id": "abc1234"
              },
              "assets": {
                "links": [
                  {
                    "name": "demo-release.apk",
                    "url": "${server.url("/assets/demo-release.apk")}",
                    "direct_asset_url": "${server.url("/downloads/demo-release.apk")}",
                    "link_type": "package"
                  },
                  {
                    "name": "release-notes.txt",
                    "url": "${server.url("/downloads/release-notes.txt")}",
                    "link_type": "other"
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private fun giteaReleaseListJson(server: MockWebServer): String {
        return """
            [
              {
                "id": 869126,
                "tag_name": "v0.14.1",
                "name": "v0.14.1",
                "body": "## What's Changed\n* Fix login edit",
                "html_url": "https://gitea.com/gitea/tea/releases/tag/v0.14.1",
                "prerelease": false,
                "draft": false,
                "published_at": "2026-05-22T09:00:00Z",
                "assets": [
                  {
                    "name": "tea-v0.14.1-android.apk",
                    "browser_download_url": "${server.url("/downloads/tea-v0.14.1-android.apk")}",
                    "size": 2048,
                    "download_count": 4,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              },
              {
                "id": 869127,
                "tag_name": "v0.15.0-rc1",
                "name": "v0.15.0-rc1",
                "body": "Release candidate",
                "html_url": "https://gitea.com/gitea/tea/releases/tag/v0.15.0-rc1",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-05-21T09:00:00Z",
                "assets": []
              }
            ]
        """.trimIndent()
    }

    private fun giteaReleaseDetailJson(server: MockWebServer): String {
        return """
            {
              "id": 869126,
              "tag_name": "v0.14.1",
              "name": "v0.14.1",
              "body": "## What's Changed\n* Fix login edit",
              "html_url": "https://gitea.com/gitea/tea/releases/tag/v0.14.1",
              "prerelease": false,
              "draft": false,
              "published_at": "2026-05-22T09:00:00Z",
              "assets": [
                {
                  "name": "tea-v0.14.1-android.apk",
                  "browser_download_url": "${server.url("/downloads/tea-v0.14.1-android.apk")}",
                  "size": 2048,
                  "download_count": 4,
                  "content_type": "application/vnd.android.package-archive"
                },
                {
                  "name": "tea-v0.14.1.sha256",
                  "browser_download_url": "${server.url("/downloads/tea-v0.14.1.sha256")}",
                  "size": 64,
                  "download_count": 2,
                  "content_type": "text/plain"
                }
              ]
            }
        """.trimIndent()
    }
}
