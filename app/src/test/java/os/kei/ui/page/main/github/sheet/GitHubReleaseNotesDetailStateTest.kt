package os.kei.ui.page.main.github.sheet

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubReleaseNotesDetailStateTest {
    @Test
    fun `release notes detail derivation parses markdown blocks and preview lines`() =
        runBlocking {
            val item =
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo",
                )
            val bundle =
                GitHubReleaseAssetBundle(
                    releaseName = "Demo 1.0",
                    tagName = "v1.0.0",
                    htmlUrl = "https://github.com/demo/app/releases/tag/v1.0.0",
                    releaseNotesBody = "# Changelog\n\n- Added APK install\n- Fixed jank",
                    assets = emptyList(),
                )

            val state =
                deriveGitHubReleaseNotesDetailState(
                    GitHubReleaseNotesDetailInput(
                        requestKey = "demo",
                        item = item,
                        state = VersionCheckUi(latestStableRawTag = "v1.0.0"),
                        assetBundle = bundle,
                    ),
                )

            assertEquals("demo", state.requestKey)
            assertEquals(bundle.releaseNotesBody, state.rawMarkdown)
            assertTrue(state.markdownBlocks.any { it is AppMarkdownBlock.Heading })
            assertEquals(listOf("Added APK install", "Fixed jank"), state.lines)
        }

    @Test
    fun `release notes detail derivation falls back to version state when markdown is empty`() =
        runBlocking {
            val item =
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo",
                )

            val state =
                deriveGitHubReleaseNotesDetailState(
                    GitHubReleaseNotesDetailInput(
                        requestKey = "fallback",
                        item = item,
                        state =
                            VersionCheckUi(
                                releaseHint = "更新到 v1.0.0",
                                latestStableRawTag = "v1.0.0",
                            ),
                    ),
                )

            assertEquals("", state.rawMarkdown)
            assertEquals(emptyList(), state.markdownBlocks)
            assertTrue(state.lines.any { it.contains("v1.0.0") })
        }
}
