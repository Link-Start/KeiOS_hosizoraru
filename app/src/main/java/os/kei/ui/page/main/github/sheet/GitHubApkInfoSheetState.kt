package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode

@Immutable
internal data class GitHubApkInfoSheetInput(
    val assetKey: String = "",
    val info: GitHubApkManifestInfo? = null,
    val query: String = "",
)

@Immutable
internal data class GitHubApkInfoSheetUiState(
    val assetKey: String = "",
    val query: String = "",
    val normalizedQuery: String = "",
    val deriving: Boolean = false,
    val nativeAbiValues: List<String> = emptyList(),
    val permissionValues: List<String> = emptyList(),
    val permissionColors: Map<String, Color> = emptyMap(),
    val featureValues: List<String> = emptyList(),
    val metadataValues: List<String> = emptyList(),
    val manifestNodes: List<GitHubApkManifestNode> = emptyList(),
    val manifestNodeGroups: Map<Int, List<GitHubApkManifestNode>> = emptyMap(),
)

internal fun deriveGitHubApkInfoSheetState(input: GitHubApkInfoSheetInput): GitHubApkInfoSheetUiState {
    val normalizedQuery = input.query.trim()
    val info =
        input.info
            ?: return GitHubApkInfoSheetUiState(
                assetKey = input.assetKey,
                query = input.query,
                normalizedQuery = normalizedQuery,
            )
    val manifestNodes = info.manifestNodes.filterNodesByQuery(normalizedQuery)
    return GitHubApkInfoSheetUiState(
        assetKey = input.assetKey,
        query = input.query,
        normalizedQuery = normalizedQuery,
        nativeAbiValues = info.nativeAbis.filterStringsByQuery(normalizedQuery),
        permissionValues = info.permissions.filterStringsByQuery(normalizedQuery),
        permissionColors = info.permissions.associateWith { permission -> permissionRiskColor(permission) },
        featureValues = info.features.filterStringsByQuery(normalizedQuery),
        metadataValues =
            info.metadata
                .map { metadata ->
                    if (metadata.value.isBlank()) metadata.name else "${metadata.name}: ${metadata.value}"
                }.filterStringsByQuery(normalizedQuery),
        manifestNodes = manifestNodes,
        manifestNodeGroups = manifestNodes.groupBy { it.groupLabelRes() },
    )
}
