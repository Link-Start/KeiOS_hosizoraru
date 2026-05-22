package os.kei.ui.page.main.github.share

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal object GitHubShareImportFlowCoordinator {
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock
    private val previewCoordinator = GitHubShareImportPreviewCoordinator(clock)
    private val deliveryCoordinator = GitHubShareImportDeliveryCoordinator(clock)
    private val resultWriter = GitHubShareImportResultWriter(clock)
    private val entryCoordinator =
        GitHubShareImportEntryCoordinator(
            previewCoordinator = previewCoordinator,
            resultWriter = resultWriter,
            clock = clock,
        )

    suspend fun startIncomingShare(
        context: Context,
        sharedText: String,
        lookupConfig: GitHubLookupConfig? = null,
    ): ShareImportIncomingCoordinatorResult =
        entryCoordinator.startIncomingShare(
            context = context,
            sharedText = sharedText,
            lookupConfig = lookupConfig,
        )

    suspend fun prepareAssetReady(
        context: Context,
        preview: GitHubShareImportPreview,
        sendInstallActionEnabled: Boolean,
    ): ShareImportCoordinatorResult.AssetReady =
        previewCoordinator.prepareAssetReady(
            context = context,
            preview = preview,
            sendInstallActionEnabled = sendInstallActionEnabled,
        )

    suspend fun sendActivePreviewAssetToInstaller(context: Context): ShareImportDeliveryCoordinatorResult =
        deliveryCoordinator.sendActivePreviewAssetToInstaller(context)

    suspend fun continueActiveManagedInstall(
        context: Context,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        deliveryCoordinator.continueActiveManagedInstall(
            context = context,
            onManagedInstallProgress = onManagedInstallProgress,
        )

    suspend fun startDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        launchInNewTask: Boolean = false,
        onManagedInstallProgress: suspend (GitHubShareImportManagedInstallProgress) -> Unit = {},
    ): ShareImportDeliveryCoordinatorResult =
        deliveryCoordinator.startDelivery(
            context = context,
            preview = preview,
            selectedAsset = selectedAsset,
            lookupConfig = lookupConfig,
            launchInNewTask = launchInNewTask,
            onManagedInstallProgress = onManagedInstallProgress,
        )

    suspend fun refreshPendingInstall(
        context: Context,
        event: AppPackageChangedEvent? = null,
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val pending =
            withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.loadPendingShareImportTrack()
            } ?: return ShareImportCoordinatorResult.None
        val age = (clock.nowMs() - pending.armedAtMillis).coerceAtLeast(0L)
        if (age > shareImportTrackMaxAgeMs) {
            return cancelPending(
                context = appContext,
                pending = pending,
                notify = true,
            )
        }

        val reconciler = GitHubShareImportInstallReconciler(appContext)
        val currentCandidate =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore.loadActiveAttachCandidate()?.toShareImportAttachCandidate()
            }
        val reconcileResult =
            if (event != null) {
                reconciler.reconcilePackageEvent(
                    pendingTrack = pending,
                    event = event,
                    currentCandidate = currentCandidate,
                )
            } else {
                reconciler.reconcileRecentInstall(pending)
            }
        return applyReconcileResult(
            context = appContext,
            pending = pending,
            result = reconcileResult,
        )
    }

    suspend fun confirmActiveAttachCandidate(
        context: Context,
        prefetchLatestCheck: Boolean = true,
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val candidate =
            withContext(AppDispatchers.githubNetwork) {
                GitHubShareImportFlowStore
                    .loadActiveAttachCandidate()
                    ?.toShareImportAttachCandidate()
            } ?: return refreshPendingInstall(appContext)
        GitHubShareImportNotificationHelper.notifyAddingTrack(
            context = appContext,
            owner = candidate.owner,
            repo = candidate.repo,
            appLabel = candidate.appLabel,
            packageName = candidate.packageName,
            versionName = candidate.versionName,
            targetDisplayName =
                buildShareImportTargetDisplayName(
                    appLabel = candidate.appLabel,
                    repo = candidate.repo,
                    packageName = candidate.packageName,
                ),
        )
        return when (
            val result =
                attachCandidateToTracked(
                    context = appContext,
                    candidate = candidate,
                    prefetchLatestCheck = prefetchLatestCheck,
                )
        ) {
            ShareImportAttachResult.Duplicate -> {
                resultWriter.saveAttachCandidateResult(
                    candidate = candidate,
                    kind = GitHubShareImportResultKind.AlreadyTracked,
                    clearActiveAttachCandidate = true,
                )
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAlreadyTracked(
                    context = appContext,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = candidate.appLabel,
                    packageName = candidate.packageName,
                    versionName = candidate.versionName,
                    targetDisplayName =
                        buildShareImportTargetDisplayName(
                            appLabel = candidate.appLabel,
                            repo = candidate.repo,
                            packageName = candidate.packageName,
                        ),
                )
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportAttachResult.Failed -> {
                resultWriter.saveAttachCandidateResult(
                    candidate = candidate,
                    kind = GitHubShareImportResultKind.Failed,
                    message = result.message,
                )
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyFailed(appContext, result.message)
                ShareImportCoordinatorResult.Failed(result.message)
            }

            is ShareImportAttachResult.Added -> {
                resultWriter.saveAttachCandidateResult(
                    candidate = candidate,
                    kind = GitHubShareImportResultKind.Added,
                    appLabelOverride = result.appLabel,
                    clearActiveAttachCandidate = true,
                )
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAdded(
                    context = appContext,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = result.appLabel.ifBlank { candidate.appLabel },
                    packageName = candidate.packageName,
                    versionName = candidate.versionName,
                    targetDisplayName =
                        buildShareImportTargetDisplayName(
                            appLabel = result.appLabel.ifBlank { candidate.appLabel },
                            repo = candidate.repo,
                            packageName = candidate.packageName,
                        ),
                )
                ShareImportCoordinatorResult.Added(candidate, result.appLabel)
            }
        }
    }

    suspend fun cancelActiveFlow(context: Context): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        GitHubShareImportManagedInstallCoordinator.cancelActive(appContext)
        val result = buildCancelledResult(appContext)
        resultWriter.saveResultAndClearActiveFlow(result)
        GitHubShareImportPendingScheduler.cancel(appContext)
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyCancelled(appContext)
        return ShareImportCoordinatorResult.Cancelled(result)
    }

    fun markRead(context: Context) {
        GitHubShareImportFlowStore.clearActiveResult()
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.cancel(context.applicationContext)
    }

    fun handlePackageChangedAsync(
        context: Context,
        event: AppPackageChangedEvent,
    ) {
        if (event.action !in shareImportAttachActions) return
        backgroundScope.launch {
            refreshPendingInstall(context.applicationContext, event)
        }
    }

    private suspend fun applyReconcileResult(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        result: ShareImportInstallReconcileResult,
    ): ShareImportCoordinatorResult =
        when (result) {
            ShareImportInstallReconcileResult.None -> {
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
                ShareImportCoordinatorResult.Pending(pending)
            }

            ShareImportInstallReconcileResult.Expired -> {
                cancelPending(
                    context = context,
                    pending = pending,
                    notify = true,
                )
            }

            is ShareImportInstallReconcileResult.Duplicate -> {
                val candidate = result.candidate
                resultWriter.saveAttachCandidateResult(
                    candidate = candidate,
                    kind = GitHubShareImportResultKind.AlreadyTracked,
                    clearPendingTrack = true,
                    clearActiveFlow = true,
                )
                GitHubShareImportPendingScheduler.cancel(context)
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAlreadyTracked(
                    context = context,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = candidate.appLabel,
                    packageName = candidate.packageName,
                    versionName = candidate.versionName,
                    targetDisplayName =
                        buildShareImportTargetDisplayName(
                            appLabel = candidate.appLabel,
                            repo = candidate.repo,
                            packageName = candidate.packageName,
                        ),
                )
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportInstallReconcileResult.Detected -> {
                val candidate = result.candidate
                withContext(AppDispatchers.githubNetwork) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActivePreview()
                    GitHubShareImportFlowStore.saveActiveAttachCandidate(
                        candidate.toPendingAttachCandidateRecord(),
                    )
                }
                GitHubShareImportPendingScheduler.cancel(context)
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyInstallDetected(
                    context = context,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = candidate.appLabel,
                    packageName = candidate.packageName,
                    versionName = candidate.versionName,
                    targetDisplayName =
                        buildShareImportTargetDisplayName(
                            appLabel = candidate.appLabel,
                            repo = candidate.repo,
                            packageName = candidate.packageName,
                        ),
                )
                ShareImportCoordinatorResult.Detected(candidate)
            }
        }

    private suspend fun cancelPending(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        notify: Boolean,
    ): ShareImportCoordinatorResult.Cancelled {
        val result =
            resultWriter.savePendingTrackResultAndClearFlow(
                pending = pending,
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled),
            )
        GitHubShareImportPendingScheduler.cancel(context)
        GitHubTrackStoreSignals.notifyChanged()
        if (notify) {
            GitHubShareImportNotificationHelper.notifyCancelled(context)
        }
        return ShareImportCoordinatorResult.Cancelled(result)
    }

    private fun buildCancelledResult(context: Context): GitHubShareImportResult? {
        GitHubShareImportFlowStore.loadActiveAttachCandidate()?.let { candidate ->
            return candidate.toShareImportAttachCandidate().toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled),
                completedAtMillis = clock.nowMs(),
            )
        }
        GitHubTrackStore.loadPendingShareImportTrack()?.let { pending ->
            return pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled),
                completedAtMillis = clock.nowMs(),
            )
        }
        GitHubShareImportFlowStore.loadActivePreview()?.let { preview ->
            return preview.toShareImportPreview().toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled),
                completedAtMillis = clock.nowMs(),
            )
        }
        return null
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + AppDispatchers.githubNetwork)
}
