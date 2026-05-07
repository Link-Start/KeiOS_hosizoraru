package os.kei.ui.page.main.github.share

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubShareImportActionReceiver
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal object GitHubShareImportFlowCoordinator {
    suspend fun startDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): ShareImportDeliveryCoordinatorResult = coroutineScope {
        GitHubShareImportNotificationHelper.notifyDelivering(
            context = context,
            owner = preview.owner,
            repo = preview.repo,
            assetName = selectedAsset.name,
            targetDisplayName = preview.targetDisplayName.ifBlank {
                buildShareImportTargetDisplayName(
                    repo = preview.repo,
                    assetName = selectedAsset.name
                )
            }
        )
        val scannedPackageNameDeferred = async(Dispatchers.IO) {
            scanShareImportAssetPackageName(
                asset = selectedAsset,
                lookupConfig = lookupConfig
            ).getOrDefault("")
        }
        val deliveryResult = sendAssetToConfiguredChannel(
            context = context,
            lookupConfig = lookupConfig,
            asset = selectedAsset
        )
        when (deliveryResult) {
            is ShareImportDeliveryResult.Failure -> {
                scannedPackageNameDeferred.cancel()
                GitHubShareImportNotificationHelper.notifyFailed(
                    context = context,
                    reason = context.getString(deliveryResult.toastResId)
                )
                ShareImportDeliveryCoordinatorResult.Failed(deliveryResult.toastResId)
            }

            is ShareImportDeliveryResult.Success -> {
                val scannedPackageName = scannedPackageNameDeferred.await()
                val pending = GitHubPendingShareImportTrackRecord(
                    projectUrl = preview.projectUrl,
                    owner = preview.owner,
                    repo = preview.repo,
                    releaseTag = preview.releaseTag,
                    assetName = selectedAsset.name,
                    packageName = scannedPackageName,
                    targetDisplayName = buildShareImportTargetDisplayName(
                        repo = preview.repo,
                        assetName = selectedAsset.name,
                        packageName = scannedPackageName
                    ).ifBlank { preview.targetDisplayName },
                    armedAtMillis = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
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
                    remainingMinutes = shareImportRemainingMinutes(pending.armedAtMillis),
                    targetDisplayName = pending.targetDisplayName
                )
                GitHubShareImportPendingScheduler.scheduleNext(context)
                ShareImportDeliveryCoordinatorResult.WaitingInstall(
                    pending = pending,
                    toastResId = deliveryResult.toastResId,
                    assetName = selectedAsset.name
                )
            }
        }
    }

    suspend fun refreshPendingInstall(
        context: Context,
        event: AppPackageChangedEvent? = null
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val pending = withContext(Dispatchers.IO) {
            GitHubTrackStore.loadPendingShareImportTrack()
        } ?: return ShareImportCoordinatorResult.None
        val age = (System.currentTimeMillis() - pending.armedAtMillis).coerceAtLeast(0L)
        if (age > shareImportTrackMaxAgeMs) {
            return cancelPending(
                context = appContext,
                pending = pending,
                notify = true
            )
        }

        val reconciler = GitHubShareImportInstallReconciler(appContext)
        val currentCandidate = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveAttachCandidate()?.toShareImportAttachCandidate()
        }
        val reconcileResult = if (event != null) {
            reconciler.reconcilePackageEvent(
                pendingTrack = pending,
                event = event,
                currentCandidate = currentCandidate
            )
        } else {
            reconciler.reconcileRecentInstall(pending)
        }
        return applyReconcileResult(
            context = appContext,
            pending = pending,
            result = reconcileResult
        )
    }

    suspend fun confirmActiveAttachCandidate(
        context: Context,
        prefetchLatestCheck: Boolean = true
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val candidate = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveAttachCandidate()
                ?.toShareImportAttachCandidate()
        } ?: return refreshPendingInstall(appContext)
        GitHubShareImportNotificationHelper.notifyAddingTrack(
            context = appContext,
            owner = candidate.owner,
            repo = candidate.repo,
            appLabel = candidate.appLabel,
            packageName = candidate.packageName,
            targetDisplayName = buildShareImportTargetDisplayName(
                appLabel = candidate.appLabel,
                repo = candidate.repo,
                packageName = candidate.packageName
            )
        )
        return when (
            val result = attachCandidateToTracked(
                context = appContext,
                candidate = candidate,
                prefetchLatestCheck = prefetchLatestCheck
            )
        ) {
            ShareImportAttachResult.Duplicate -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveAttachCandidate()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            .toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAlreadyTracked(
                    context = appContext,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = candidate.appLabel,
                    packageName = candidate.packageName,
                    targetDisplayName = buildShareImportTargetDisplayName(
                        appLabel = candidate.appLabel,
                        repo = candidate.repo,
                        packageName = candidate.packageName
                    )
                )
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportAttachResult.Failed -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(
                            kind = GitHubShareImportResultKind.Failed,
                            message = result.message
                        ).toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyFailed(appContext, result.message)
                ShareImportCoordinatorResult.Failed(result.message)
            }

            is ShareImportAttachResult.Added -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveAttachCandidate()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(
                            kind = GitHubShareImportResultKind.Added,
                            appLabelOverride = result.appLabel
                        ).toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAdded(
                    context = appContext,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = result.appLabel.ifBlank { candidate.appLabel },
                    packageName = candidate.packageName,
                    targetDisplayName = buildShareImportTargetDisplayName(
                        appLabel = result.appLabel.ifBlank { candidate.appLabel },
                        repo = candidate.repo,
                        packageName = candidate.packageName
                    )
                )
                ShareImportCoordinatorResult.Added(candidate, result.appLabel)
            }
        }
    }

    suspend fun cancelActiveFlow(context: Context): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val result = buildCancelledResult(appContext)
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubTrackStore.savePendingShareImportTrack(null)
            if (result != null) {
                GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
            }
        }
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

    fun handlePackageChangedAsync(context: Context, event: AppPackageChangedEvent) {
        if (event.action !in shareImportAttachActions) return
        backgroundScope.launch {
            refreshPendingInstall(context.applicationContext, event)
        }
    }

    private suspend fun applyReconcileResult(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        result: ShareImportInstallReconcileResult
    ): ShareImportCoordinatorResult {
        return when (result) {
            ShareImportInstallReconcileResult.None -> {
                GitHubShareImportNotificationHelper.notifyWaitingInstall(
                    context = context,
                    owner = pending.owner,
                    repo = pending.repo,
                    releaseTag = pending.releaseTag,
                    assetName = pending.assetName,
                    packageName = pending.packageName,
                    remainingMinutes = shareImportRemainingMinutes(pending.armedAtMillis),
                    targetDisplayName = pending.targetDisplayName
                )
                GitHubShareImportPendingScheduler.scheduleNext(context)
                ShareImportCoordinatorResult.Pending(pending)
            }

            ShareImportInstallReconcileResult.Expired -> cancelPending(
                context = context,
                pending = pending,
                notify = true
            )

            is ShareImportInstallReconcileResult.Duplicate -> {
                val candidate = result.candidate
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            .toRecord()
                    )
                }
                GitHubShareImportPendingScheduler.cancel(context)
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAlreadyTracked(
                    context = context,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = candidate.appLabel,
                    packageName = candidate.packageName,
                    targetDisplayName = buildShareImportTargetDisplayName(
                        appLabel = candidate.appLabel,
                        repo = candidate.repo,
                        packageName = candidate.packageName
                    )
                )
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportInstallReconcileResult.Detected -> {
                val candidate = result.candidate
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActivePreview()
                    GitHubShareImportFlowStore.saveActiveAttachCandidate(
                        candidate.toPendingAttachCandidateRecord()
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
                    targetDisplayName = buildShareImportTargetDisplayName(
                        appLabel = candidate.appLabel,
                        repo = candidate.repo,
                        packageName = candidate.packageName
                    )
                )
                ShareImportCoordinatorResult.Detected(candidate)
            }
        }
    }

    private suspend fun cancelPending(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        notify: Boolean
    ): ShareImportCoordinatorResult.Cancelled {
        val result = pending.toShareImportResult(
            kind = GitHubShareImportResultKind.Cancelled,
            message = context.getString(R.string.github_share_import_notify_content_cancelled)
        )
        withContext(Dispatchers.IO) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
        }
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
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        GitHubTrackStore.loadPendingShareImportTrack()?.let { pending ->
            return pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        GitHubShareImportFlowStore.loadActivePreview()?.let { preview ->
            return preview.toShareImportPreview().toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        return null
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal sealed interface ShareImportDeliveryCoordinatorResult {
    data class WaitingInstall(
        val pending: GitHubPendingShareImportTrackRecord,
        val toastResId: Int,
        val assetName: String
    ) : ShareImportDeliveryCoordinatorResult

    data class Failed(
        val toastResId: Int
    ) : ShareImportDeliveryCoordinatorResult
}

internal sealed interface ShareImportCoordinatorResult {
    data object None : ShareImportCoordinatorResult
    data class Pending(val pending: GitHubPendingShareImportTrackRecord) :
        ShareImportCoordinatorResult

    data class Detected(val candidate: GitHubPendingShareImportAttachCandidate) :
        ShareImportCoordinatorResult

    data class Added(
        val candidate: GitHubPendingShareImportAttachCandidate,
        val appLabel: String
    ) : ShareImportCoordinatorResult

    data class AlreadyTracked(
        val candidate: GitHubPendingShareImportAttachCandidate
    ) : ShareImportCoordinatorResult

    data class Failed(val message: String) : ShareImportCoordinatorResult
    data class Cancelled(val result: GitHubShareImportResult?) : ShareImportCoordinatorResult
}

internal object GitHubShareImportPendingScheduler {
    private const val REQUEST_CODE_SHARE_IMPORT_TICK = 42003
    private const val FIRST_TICK_DELAY_MS = 15_000L
    private const val REFRESH_TICK_DELAY_MS = 60_000L

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val pending = GitHubTrackStore.loadPendingShareImportTrack()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        if (pending == null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            return
        }
        val now = System.currentTimeMillis()
        val age = (now - pending.armedAtMillis).coerceAtLeast(0L)
        val expiresAt = pending.armedAtMillis + shareImportTrackMaxAgeMs
        val delay = if (age < FIRST_TICK_DELAY_MS) FIRST_TICK_DELAY_MS else REFRESH_TICK_DELAY_MS
        val triggerAt =
            (now + delay).coerceAtMost(expiresAt).coerceAtLeast(now + FIRST_TICK_DELAY_MS)
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.ACTION_REFRESH_SHARE_IMPORT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SHARE_IMPORT_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
