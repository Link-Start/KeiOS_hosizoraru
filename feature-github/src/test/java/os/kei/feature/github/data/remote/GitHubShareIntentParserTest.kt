package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class GitHubShareIntentParserTest {
    @Test
    fun `extract first github url trims trailing punctuation`() {
        val text = "看看这个链接：https://github.com/open-ani/animeko/releases。"
        val url = GitHubShareIntentParser.extractFirstGitHubUrl(text)
        assertEquals("https://github.com/open-ani/animeko/releases", url)
    }

    @Test
    fun `extract github url upgrades http to https`() {
        val text = "http://www.github.com/open-ani/animeko/releases"
        val url = GitHubShareIntentParser.extractFirstGitHubUrl(text)
        assertEquals("https://github.com/open-ani/animeko/releases", url)
    }

    @Test
    fun `parse single link resolves its type and target`() {
        val repoUrl = "https://github.com/open-ani/animeko"
        fun link(
            sourceUrl: String,
            type: GitHubSharedUrlType,
            releaseTag: String = "",
            assetName: String = ""
        ) = GitHubSharedReleaseLink(
            sourceUrl = sourceUrl,
            projectUrl = repoUrl,
            owner = "open-ani",
            repo = "animeko",
            type = type,
            releaseTag = releaseTag,
            assetName = assetName
        )
        val download = "$repoUrl/releases/download/v5.5.0-alpha02/ani-5.5.0-alpha02-arm64-v8a.apk"
        val rows = listOf(
            "repo link to project target" to link(repoUrl, GitHubSharedUrlType.Repo),
            "releases page link" to link("$repoUrl/releases", GitHubSharedUrlType.Releases),
            "release tag link with decoded tag" to link(
                "$repoUrl/releases/tag/v5.5.0-alpha02",
                GitHubSharedUrlType.ReleaseTag,
                releaseTag = "v5.5.0-alpha02"
            ),
            "release download link with tag and asset" to link(
                download,
                GitHubSharedUrlType.ReleaseDownloadAsset,
                releaseTag = "v5.5.0-alpha02",
                assetName = "ani-5.5.0-alpha02-arm64-v8a.apk"
            ),
            "releases latest link to latest stable target" to link(
                "$repoUrl/releases/latest",
                GitHubSharedUrlType.ReleasesLatest
            )
        )

        rows.forEach { (case, expected) ->
            assertEquals(
                expected,
                GitHubShareIntentParser.parseSharedReleaseLink(expected.sourceUrl),
                case
            )
        }
    }

    @Test
    fun `multiple same priority links choose latest shared url`() {
        val parsed = GitHubShareIntentParser.parseSharedReleaseLink(
            "https://github.com/open-ani/animeko\nhttps://github.com/asadahimeka/pixiv-viewer-app"
        )
        assertNotNull(parsed)
        assertEquals(GitHubSharedUrlType.Repo, parsed.type)
        assertEquals("asadahimeka", parsed.owner)
        assertEquals("pixiv-viewer-app", parsed.repo)
    }

    @Test
    fun `multiple links keep stronger release target priority`() {
        val parsed = GitHubShareIntentParser.parseSharedReleaseLink(
            "https://github.com/open-ani/animeko/releases/download/v5.5.0-alpha02/ani-5.5.0-alpha02-arm64-v8a.apk\nhttps://github.com/asadahimeka/pixiv-viewer-app"
        )
        assertNotNull(parsed)
        assertEquals(GitHubSharedUrlType.ReleaseDownloadAsset, parsed.type)
        assertEquals("open-ani", parsed.owner)
        assertEquals("animeko", parsed.repo)
    }

    @Test
    fun `non github text returns null`() {
        val parsed = GitHubShareIntentParser.parseSharedReleaseLink("https://example.com/demo")
        assertNull(parsed)
    }

    @Test
    fun `github url with userinfo is rejected`() {
        val parsed = GitHubShareIntentParser.parseSharedReleaseLink(
            "https://github.com@evil.example/open-ani/animeko/releases"
        )
        assertNull(parsed)
    }
}
