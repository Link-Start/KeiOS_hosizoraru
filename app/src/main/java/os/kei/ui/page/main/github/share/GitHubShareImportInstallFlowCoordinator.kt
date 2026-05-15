package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

internal sealed interface GitHubShareImportSelectedAssetDeliveryResult {
    data class LaunchingManagedInstall(
        val selectedPreview: GitHubShareImportPreview,
        val progress: GitHubShareImportManagedInstallProgress
    ) : GitHubShareImportSelectedAssetDeliveryResult

    data class CommittingManagedInstall(
        val progress: GitHubShareImportManagedInstallProgress
    ) : GitHubShareImportSelectedAssetDeliveryResult

    data class Delivered(
        val result: ShareImportDeliveryCoordinatorResult
    ) : GitHubShareImportSelectedAssetDeliveryResult
}

internal class GitHubShareImportInstallFlowCoordinator(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun startSelectedAssetDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        currentManagedProgress: GitHubShareImportManagedInstallProgress?,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {}
    ): GitHubShareImportSelectedAssetDeliveryResult {
        val lookupConfig = withContext(ioDispatcher) {
            GitHubTrackStore.loadLookupConfig()
        }
        if (lookupConfig.appManagedShareInstallEnabled) {
            if (currentManagedProgress?.phase == GitHubShareImportPhase.InstallReady) {
                val committingProgress = currentManagedProgress.copy(
                    phase = GitHubShareImportPhase.InstallCommitting,
                    progressPercent = 92
                )
                GitHubShareImportDeliveryRunner.launchCurrentDeliveryAction(context)
                return GitHubShareImportSelectedAssetDeliveryResult.CommittingManagedInstall(
                    committingProgress
                )
            }
            val selectedPreview = preview.copy(
                selectedAssetName = selectedAsset.name,
                sendInstallActionEnabled = true
            )
            val progress = GitHubShareImportManagedInstallProgress(
                phase = GitHubShareImportPhase.Installing,
                assetName = selectedAsset.name,
                targetDisplayName = selectedPreview.targetDisplayName,
                progressPercent = 0,
                totalBytes = selectedAsset.sizeBytes
            )
            GitHubShareImportDeliveryRunner.launchSelectedPreviewDelivery(
                context = context,
                preview = selectedPreview,
                selectedAsset = selectedAsset
            )
            return GitHubShareImportSelectedAssetDeliveryResult.LaunchingManagedInstall(
                selectedPreview = selectedPreview,
                progress = progress
            )
        }
        val result = GitHubShareImportFlowCoordinator.startDelivery(
            context = context,
            preview = preview,
            selectedAsset = selectedAsset,
            lookupConfig = lookupConfig,
            onManagedInstallProgress = onManagedInstallProgress
        )
        return GitHubShareImportSelectedAssetDeliveryResult.Delivered(result)
    }
}
