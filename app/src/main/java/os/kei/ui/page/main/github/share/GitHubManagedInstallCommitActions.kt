package os.kei.ui.page.main.github.share

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.install.GitHubApkInstallRequest

internal data class GitHubManagedInstallCommitRequest(
    val preview: GitHubShareImportPreview,
    val activeRecord: GitHubPendingShareImportManagedInstallRecord,
    val request: GitHubApkInstallRequest,
)

internal object GitHubManagedInstallCommitActions {
    suspend fun buildActiveCommitRequest(): GitHubManagedInstallCommitRequest? {
        val activeRecord =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveManagedInstall()
            } ?: return null
        if (activeRecord.sessionId <= 0) return null
        val preview =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActivePreview()?.toShareImportPreview()
            } ?: return null
        val asset =
            preview.assets.firstOrNull { asset ->
                asset.name.equals(activeRecord.assetName, ignoreCase = true)
            } ?: preview.selectedAssetForSend ?: return null
        val lookupConfig =
            withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.loadLookupConfig()
            }
        return GitHubManagedInstallCommitRequest(
            preview = preview,
            activeRecord = activeRecord,
            request =
                activeRecord.toInstallRequest(
                    asset = asset,
                    lookupConfig = lookupConfig,
                    fallbackTargetDisplayName = preview.targetDisplayName,
                ),
        )
    }
}
