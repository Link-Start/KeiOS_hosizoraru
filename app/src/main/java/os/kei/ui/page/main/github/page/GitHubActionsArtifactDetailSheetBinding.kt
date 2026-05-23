@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.feature.github.model.supportsManagedApkInstall
import os.kei.ui.page.main.github.sheet.GitHubActionsArtifactDetailSheet

@Composable
internal fun GitHubActionsArtifactDetailSheetBinding(
    state: GitHubPageState,
    actions: GitHubPageActions,
    backdrop: LayerBackdrop,
) {
    val request = state.actionsArtifactDetailRequest
    val artifactId = request?.artifactMatch?.artifact?.id
    GitHubActionsArtifactDetailSheet(
        request = request,
        backdrop = backdrop,
        hasToken = state.lookupConfig.actionsArtifactDownloadsAvailable,
        managedInstallEnabled =
            request
                ?.artifactMatch
                ?.supportsManagedApkInstall(state.lookupConfig) == true,
        downloading = artifactId?.let { state.actionsArtifactDownloadLoadingId == it } == true,
        sharing = artifactId?.let { state.actionsArtifactShareLoadingId == it } == true,
        onDismissRequest = actions::dismissActionsArtifactDetail,
        onRefreshRun = actions::refreshActionsRunStatus,
        onInstall = actions::installActionsArtifact,
        onDownload = actions::downloadActionsArtifact,
        onShare = actions::shareActionsArtifact,
    )
}
