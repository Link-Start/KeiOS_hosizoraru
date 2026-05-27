package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

internal sealed interface GitHubShareImportSelectedAssetDeliveryResult {
    data class LaunchingManagedInstall(
        val selectedPreview: GitHubShareImportPreview,
        val progress: GitHubShareImportManagedInstallProgress,
    ) : GitHubShareImportSelectedAssetDeliveryResult

    data class CommittingManagedInstall(
        val progress: GitHubShareImportManagedInstallProgress,
    ) : GitHubShareImportSelectedAssetDeliveryResult

    data class Delivered(
        val result: ShareImportDeliveryCoordinatorResult,
    ) : GitHubShareImportSelectedAssetDeliveryResult
}

internal class GitHubShareImportInstallFlowCoordinator(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
) {
    suspend fun startSelectedAssetDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        currentManagedProgress: GitHubShareImportManagedInstallProgress?,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): GitHubShareImportSelectedAssetDeliveryResult {
        val lookupConfig =
            withContext(ioDispatcher) {
                GitHubTrackStore.loadLookupConfig()
            }
        when (
            val plan =
                resolveSelectedAssetDeliveryPlan(
                    preview = preview,
                    selectedAsset = selectedAsset,
                    appManagedShareInstallEnabled = lookupConfig.appManagedShareInstallEnabled,
                    currentManagedProgress = currentManagedProgress,
                )
        ) {
            is GitHubShareImportSelectedAssetDeliveryPlan.CommitManagedInstall -> {
                GitHubShareImportDeliveryRunner.launchCurrentDeliveryAction(context)
                return GitHubShareImportSelectedAssetDeliveryResult.CommittingManagedInstall(
                    plan.progress,
                )
            }

            is GitHubShareImportSelectedAssetDeliveryPlan.LaunchManagedInstall -> {
                GitHubShareImportDeliveryRunner.launchSelectedPreviewDelivery(
                    context = context,
                    preview = plan.selectedPreview,
                    selectedAsset = selectedAsset,
                )
                return GitHubShareImportSelectedAssetDeliveryResult.LaunchingManagedInstall(
                    selectedPreview = plan.selectedPreview,
                    progress = plan.progress,
                )
            }

            GitHubShareImportSelectedAssetDeliveryPlan.DirectDelivery -> {
                Unit
            }
        }
        val result =
            GitHubShareImportFlowCoordinator.startDelivery(
                context = context,
                preview = preview,
                selectedAsset = selectedAsset,
                lookupConfig = lookupConfig,
                onManagedInstallProgress = onManagedInstallProgress,
            )
        return GitHubShareImportSelectedAssetDeliveryResult.Delivered(result)
    }
}
