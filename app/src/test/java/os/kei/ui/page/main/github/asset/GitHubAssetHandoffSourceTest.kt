package os.kei.ui.page.main.github.asset

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Keeps [GitHubAssetHandoff] the only place a GitHub surface builds a share.
 *
 * Issue #29 was a page that reused the tracked card's asset row for its look and then wired the row's
 * share button to an intent of its own, which read none of the transfer settings. The F-Droid history
 * copied it from there. The next surface will be written the same way — from the nearest page — so this
 * fails at the copy rather than in a bug report.
 */
class GitHubAssetHandoffSourceTest {
    @Test
    fun noGitHubSurfaceBuildsItsOwnShareIntent() {
        val sources = githubUiSources()
        assertTrue(sources.size > 100, "Expected the GitHub UI sources, found ${sources.size} files")

        val ownShares =
            sources
                .filterNot { file -> file.name in SHARE_INTENT_OWNERS }
                .filter { file -> SHARE_INTENT.containsMatchIn(file.readText()) }
                .map { file -> file.name }

        assertEquals(
            emptyList(),
            ownShares,
            "Share a release file through GitHubAssetHandoff, which reads 分享到安装器; an intent of " +
                "its own sends the file to a chooser even when an installer is chosen",
        )
    }

    @Test
    fun theGuardRecognisesTheShareThatCausedTheIssue() {
        // What the release list and the F-Droid history shipped before the hand-off existed. A pattern that
        // stopped matching this would pass forever and protect nothing.
        val releaseListShare =
            """
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, asset.downloadUrl)
                }
            context.startActivity(Intent.createChooser(send, asset.name))
            """.trimIndent()

        assertTrue(SHARE_INTENT.containsMatchIn(releaseListShare))
        assertTrue(
            SHARE_INTENT.containsMatchIn(
                githubUiSources().single { file -> file.name == "GitHubAssetHandoff.kt" }.readText(),
            ),
        )
    }
}

/** Anything that builds a share: the platform action, the app's text-share helper, or a chooser. */
private val SHARE_INTENT =
    Regex(
        """Intent\.ACTION_SEND(?:_MULTIPLE)?\b|"android\.intent\.action\.SEND(?:_MULTIPLE)?"|""" +
            """textShareIntent\(|createChooser\(""",
    )

private val SHARE_INTENT_OWNERS =
    setOf(
        "GitHubAssetHandoff.kt",
        // Release notes handed to a translator, with a share sheet as the last resort. Text to read, not a
        // file to install, so neither transfer setting has anything to say about it.
        "GitHubDecisionAssistDetailSheets.kt",
    )

private fun githubUiSources(): List<File> {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val root =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, GITHUB_UI_ROOT) }
            .firstOrNull(File::isDirectory)
    return requireNotNull(root) { "Unable to locate $GITHUB_UI_ROOT from $workingDirectory" }
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .toList()
}

private const val GITHUB_UI_ROOT = "app/src/main/java/os/kei/ui/page/main/github"
