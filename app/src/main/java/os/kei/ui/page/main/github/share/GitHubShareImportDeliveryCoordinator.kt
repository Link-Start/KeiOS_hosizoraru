package os.kei.ui.page.main.github.share

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal class GitHubShareImportDeliveryCoordinator(
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    suspend fun sendActivePreviewAssetToInstaller(context: Context): ShareImportDeliveryCoordinatorResult {
        val appContext = context.applicationContext
        val preview =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActivePreview()?.toShareImportPreview()
            }
        val deliveryPlan = resolveActivePreviewDeliveryPlan(preview)
        when (deliveryPlan) {
            GitHubShareImportActivePreviewDeliveryPlan.MissingPreview -> {
                val reason = appContext.getString(R.string.github_share_import_error_resolve_failed)
                GitHubShareImportNotificationHelper.notifyFailed(appContext, reason)
                return ShareImportDeliveryCoordinatorResult.Failed(
                    R.string.github_share_import_error_resolve_failed,
                )
            }

            GitHubShareImportActivePreviewDeliveryPlan.InstallActionDisabled -> {
                val readyPreview = requireNotNull(preview)
                GitHubShareImportNotificationHelper.notifyAssetReady(
                    context = appContext,
                    owner = readyPreview.owner,
                    repo = readyPreview.repo,
                    releaseTag = readyPreview.releaseTag,
                    assetCount = readyPreview.assets.size,
                    sendInstallActionEnabled = false,
                )
                return ShareImportDeliveryCoordinatorResult.Failed(
                    R.string.github_share_import_notify_action_select_apk,
                )
            }

            GitHubShareImportActivePreviewDeliveryPlan.MissingSelectedAsset -> {
                val reason = appContext.getString(R.string.github_share_import_error_no_usable_apk)
                GitHubShareImportNotificationHelper.notifyFailed(appContext, reason)
                return ShareImportDeliveryCoordinatorResult.Failed(
                    R.string.github_share_import_error_no_usable_apk,
                )
            }

            is GitHubShareImportActivePreviewDeliveryPlan.Ready -> {
                Unit
            }
        }
        val lookupConfig =
            withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.loadLookupConfig()
            }
        return startDelivery(
            context = context,
            preview = deliveryPlan.preview,
            selectedAsset = deliveryPlan.selectedAsset,
            lookupConfig = lookupConfig,
            launchInNewTask = context !is Activity,
        )
    }

    suspend fun continueActiveManagedInstall(
        context: Context,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        GitHubShareImportManagedInstallCoordinator.commitActive(
            context = context,
            onProgressUpdate = onManagedInstallProgress,
        )

    suspend fun startDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        launchInNewTask: Boolean = false,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        coroutineScope {
            if (lookupConfig.appManagedShareInstallEnabled) {
                return@coroutineScope GitHubShareImportManagedInstallCoordinator.start(
                    context = context,
                    preview = preview,
                    selectedAsset = selectedAsset,
                    lookupConfig = lookupConfig,
                    onProgressUpdate = onManagedInstallProgress,
                )
            }
            GitHubShareImportNotificationHelper.notifyDelivering(
                context = context,
                owner = preview.owner,
                repo = preview.repo,
                releaseTag = preview.releaseTag,
                assetName = selectedAsset.name,
                targetDisplayName =
                    preview.targetDisplayName.ifBlank {
                        buildShareImportTargetDisplayName(
                            repo = preview.repo,
                            assetName = selectedAsset.name,
                        )
                    },
            )
            val scannedManifestInfoDeferred =
                async(AppDispatchers.githubNetwork) {
                    scanShareImportAssetManifestInfo(
                        asset = selectedAsset,
                        lookupConfig = lookupConfig,
                    ).getOrNull()
                }
            val deliveryResult =
                sendAssetToConfiguredChannel(
                    context = context,
                    lookupConfig = lookupConfig,
                    asset = selectedAsset,
                    newTask = launchInNewTask,
                )
            when (deliveryResult) {
                is ShareImportDeliveryResult.Failure -> {
                    scannedManifestInfoDeferred.cancel()
                    GitHubShareImportNotificationHelper.notifyFailed(
                        context = context,
                        reason = context.getString(deliveryResult.toastResId),
                    )
                    ShareImportDeliveryCoordinatorResult.Failed(deliveryResult.toastResId)
                }

                is ShareImportDeliveryResult.Success -> {
                    val scannedManifestInfo = scannedManifestInfoDeferred.await()
                    val pending =
                        buildWaitingInstallTrackRecord(
                            preview = preview,
                            selectedAsset = selectedAsset,
                            scannedManifestInfo = scannedManifestInfo,
                            armedAtMillis = clock.nowMs(),
                        )
                    withContext(AppDispatchers.githubNetwork) {
                        GitHubTrackStore.savePendingShareImportTrack(pending)
                        GitHubShareImportFlowStore.clearActiveFlow()
                    }
                    GitHubTrackStoreSignals.notifyChanged()
                    GitHubShareImportNotificationHelper.notifyWaitingInstall(
                        context = context,
                        owner = pending.owner,
                        repo = pending.repo,
                        releaseTag = pending.releaseTag,
                        assetName = pending.assetName,
                        packageName = pending.packageName,
                        versionName = pending.versionName,
                        remainingMinutes = shareImportRemainingMinutes(pending.armedAtMillis),
                        targetDisplayName = pending.targetDisplayName,
                    )
                    GitHubShareImportPendingScheduler.scheduleNext(context)
                    ShareImportDeliveryCoordinatorResult.WaitingInstall(
                        pending = pending,
                        toastResId = deliveryResult.toastResId,
                        assetName = selectedAsset.name,
                    )
                }
            }
        }
}
