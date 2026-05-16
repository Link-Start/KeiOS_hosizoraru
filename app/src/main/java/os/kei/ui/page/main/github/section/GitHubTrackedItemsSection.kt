package os.kei.ui.page.main.github.section

import androidx.compose.foundation.lazy.LazyListScope

@Suppress("FunctionName")
internal fun LazyListScope.GitHubTrackedItemsSection(
    content: GitHubTrackedItemsContent,
    surfaces: GitHubTrackedItemsSurfaces,
    checkState: GitHubTrackedItemsCheckState,
    assetState: GitHubTrackedItemsAssetState,
    expansionState: GitHubTrackedItemsExpansionState,
    runtime: GitHubTrackedItemsRuntime,
    actions: GitHubTrackedItemsActions,
) {
    GitHubTrackedItemsListShell(
        content = content,
        surfaces = surfaces,
        checkState = checkState,
        assetState = assetState,
        expansionState = expansionState,
        runtime = runtime,
        actions = actions,
    )
}
