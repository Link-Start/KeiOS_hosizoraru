package os.kei.feature.github.data.remote

import org.junit.Test
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import kotlin.test.assertEquals

class GitHubReleaseAssetJsonMapperTest {
    @Test
    fun `release body maps into asset bundle notes`() {
        val release = """
        {
          "name": "Version 1.0",
          "tag_name": "v1.0",
          "html_url": "https://github.com/demo/app/releases/tag/v1.0",
          "body": "Added installer flow\nFixed cache refresh",
          "published_at": "2026-05-01T10:00:00Z",
          "assets": [
            {
              "name": "demo-arm64.apk",
              "browser_download_url": "https://example.com/demo-arm64.apk",
              "url": "https://api.github.com/assets/1",
              "size": 1024,
              "download_count": 3,
              "content_type": "application/vnd.android.package-archive",
              "digest": "sha256:abc123",
              "updated_at": "2026-05-01T10:05:00Z"
            }
          ]
        }
        """.trimIndent().parseJsonObjectOrNull()
            ?: error("release json should parse")

        val bundle = GitHubReleaseAssetJsonMapper.parseReleaseBundle(release)

        assertEquals("Version 1.0", bundle.releaseName)
        assertEquals("v1.0", bundle.tagName)
        assertEquals("Added installer flow\nFixed cache refresh", bundle.releaseNotesBody)
        assertEquals(1, bundle.assets.size)
        assertEquals("demo-arm64.apk", bundle.assets.single().name)
        assertEquals("sha256:abc123", bundle.assets.single().digest)
    }

    @Test
    fun `release stub preserves body`() {
        val stub = GitHubReleaseAssetJsonMapper.buildReleaseStub(
            releaseName = "Version 1.0",
            rawTag = "v1.0",
            releaseUrl = "https://github.com/demo/app/releases/tag/v1.0",
            releaseNotesBody = "Notes from html"
        )

        assertEquals("Notes from html", stub.optString("body"))
    }
}
