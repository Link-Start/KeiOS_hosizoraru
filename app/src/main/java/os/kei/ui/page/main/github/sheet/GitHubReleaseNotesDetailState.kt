package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Immutable
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.buildGitHubReleaseNotesDetailLines
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import os.kei.ui.page.main.widget.markdown.parseCachedAppMarkdownBlocks

@Immutable
internal data class GitHubReleaseNotesDetailInput(
    val requestKey: String = "",
    val item: GitHubTrackedApp? = null,
    val state: VersionCheckUi = VersionCheckUi(),
    val assetBundle: GitHubReleaseAssetBundle? = null,
)

@Immutable
internal data class GitHubReleaseNotesDetailUiState(
    val requestKey: String = "",
    val rawMarkdown: String = "",
    val markdownBlocks: List<AppMarkdownBlock> = emptyList(),
    val lines: List<String> = emptyList(),
    val deriving: Boolean = false,
)

internal suspend fun deriveGitHubReleaseNotesDetailState(input: GitHubReleaseNotesDetailInput): GitHubReleaseNotesDetailUiState {
    val item =
        input.item
            ?: return GitHubReleaseNotesDetailUiState(requestKey = input.requestKey)
    val rawMarkdown = input.assetBundle?.releaseNotesBody.orEmpty()
    val markdownBlocks =
        if (rawMarkdown.isBlank()) {
            emptyList()
        } else {
            parseCachedAppMarkdownBlocks(
                markdown = rawMarkdown,
                preserveLineBreaks = true,
                sourceKey =
                    releaseNotesMarkdownSourceKey(
                        item = item,
                        bundle = input.assetBundle,
                        body = rawMarkdown,
                    ),
            )
        }
    return GitHubReleaseNotesDetailUiState(
        requestKey = input.requestKey,
        rawMarkdown = rawMarkdown,
        markdownBlocks = markdownBlocks,
        lines =
            buildGitHubReleaseNotesDetailLines(
                item = item,
                state = input.state,
                assetBundle = input.assetBundle,
            ),
    )
}

internal fun releaseNotesDetailRequestKey(
    item: GitHubTrackedApp?,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
): String {
    if (item == null) return ""
    val body = assetBundle?.releaseNotesBody.orEmpty()
    return buildString {
        append(item.id)
        append('|')
        append(state.checkedAtMillis)
        append('|')
        append(state.latestStableRawTag)
        append('|')
        append(state.latestPreRawTag)
        append('|')
        append(assetBundle?.tagName.orEmpty())
        append('|')
        append(assetBundle?.htmlUrl.orEmpty())
        append('|')
        append(assetBundle?.releaseUpdatedAtMillis ?: 0L)
        append('|')
        append(body.length)
        append(':')
        append(body.hashCode())
    }
}
