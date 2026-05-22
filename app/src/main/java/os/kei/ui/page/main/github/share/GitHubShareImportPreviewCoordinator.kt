package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal class GitHubShareImportPreviewCoordinator(
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    suspend fun prepareAssetReady(
        context: Context,
        preview: GitHubShareImportPreview,
        sendInstallActionEnabled: Boolean,
    ): ShareImportCoordinatorResult.AssetReady {
        val selectedAsset = preview.selectedAssetForSend
        val readyPreview =
            preview.copy(
                selectedAssetName =
                    if (sendInstallActionEnabled) {
                        selectedAsset?.name.orEmpty()
                    } else {
                        preview.selectedAssetName
                    },
                sendInstallActionEnabled = sendInstallActionEnabled && selectedAsset != null,
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.saveActivePreview(
                readyPreview.toPendingPreviewRecord(createdAtMillis = clock.nowMs()),
            )
        }
        GitHubShareImportPendingScheduler.cancel(context)
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyAssetReady(
            context = context.applicationContext,
            owner = readyPreview.owner,
            repo = readyPreview.repo,
            releaseTag = readyPreview.releaseTag,
            assetCount = readyPreview.assets.size,
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled,
        )
        return ShareImportCoordinatorResult.AssetReady(
            preview = readyPreview,
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled,
        )
    }
}
